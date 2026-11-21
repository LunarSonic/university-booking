#!/usr/bin/env python3
"""HTTP load experiment for university-booking; Python 3.9+, no extra packages."""
import argparse
import concurrent.futures
import csv
import http.client
import itertools
import json
import math
import os
import socket
import statistics
import sys
import threading
import time
import uuid
from collections import Counter
from datetime import datetime, timedelta, timezone
from pathlib import Path
from urllib.parse import urlencode, urlsplit


PROJECT = Path(__file__).resolve().parents[2]
FIELDS = ['concurrency', 'operation', 'total', 'success', 'errors', 'error_rate_pct',
          'elapsed_s', 'throughput_rps', 'success_rps', 'avg_ms', 'p95_ms', 'http_status_counts']
RAW_FIELDS = ['worker', 'started_ms', 'finished_ms', 'latency_ms', 'status', 'success', 'error']


def percentile95(values):
    ordered = sorted(values)
    return ordered[math.ceil(len(ordered) * 0.95) - 1] if ordered else None


def summarize(concurrency, operation, rows, elapsed):
    successful = [row['latency_ms'] for row in rows if row['success']]
    errors = len(rows) - len(successful)
    return {'concurrency': concurrency, 'operation': operation, 'total': len(rows),
            'success': len(successful), 'errors': errors,
            'error_rate_pct': round(100 * errors / len(rows), 3) if rows else 0,
            'elapsed_s': round(elapsed, 6),
            'throughput_rps': round(len(rows) / elapsed, 6),
            'success_rps': round(len(successful) / elapsed, 6),
            'avg_ms': round(statistics.mean(successful), 6) if successful else None,
            'p95_ms': round(percentile95(successful), 6) if successful else None,
            'http_status_counts': json.dumps(dict(sorted(Counter(str(row['status']) for row in rows).items())))}


class Client:
    def __init__(self, base, timeout):
        parts = urlsplit(base)
        if parts.scheme not in ('http', 'https') or not parts.hostname or parts.username or parts.password:
            raise ValueError('APP_URL должен быть HTTP(S)-адресом без встроенного пароля')
        self.scheme, self.host, self.port = parts.scheme, parts.hostname, parts.port
        self.prefix = parts.path.rstrip('/')
        self.timeout = timeout
        self.connection = None

    def close(self):
        if self.connection:
            self.connection.close()
        self.connection = None

    def request(self, method, path, body=None, headers=None):
        if self.connection is None:
            cls = http.client.HTTPSConnection if self.scheme == 'https' else http.client.HTTPConnection
            self.connection = cls(self.host, self.port, timeout=self.timeout)
        try:
            self.connection.request(method, self.prefix + path, body=body,
                                    headers={'Accept': 'application/json',
                                             'Content-Type': 'application/json', **(headers or {})})
            response = self.connection.getresponse()
            raw = response.read().decode('utf-8', errors='replace')
            return response.status, raw
        except (http.client.HTTPException, OSError, socket.timeout) as error:
            self.close()
            return 0, str(error)


class LoadTest:
    def __init__(self, args):
        self.args = args
        self.run_id = datetime.now(timezone.utc).strftime('%Y%m%d_%H%M%S_%f') + '_' + str(os.getpid())
        self.output = PROJECT / 'results' / 'loadtest' / self.run_id
        self.latest = PROJECT / 'results' / 'loadtest' / 'latest'
        self.summaries = []
        self.status = 'running'
        self.ip_counter = itertools.count(uuid.uuid4().int % 131072)
        day = datetime.now(timezone.utc).date().isoformat()
        self.read_path = '/events/by-result?' + urlencode({'result': 'SUCCESS', 'from': day + 'T00:00:00Z',
                                                         'to': day + 'T23:59:59.999Z', 'limit': 20})
        self.body = json.dumps({'userId': 900001, 'serviceId': 1, 'room': 305,
                                'bookingDate': (datetime.now() + timedelta(days=1)).isoformat(timespec='seconds'),
                                'details': {'participants': 10, 'needsProjector': True,
                                            'comment': 'load-test ' + self.run_id}}).encode()
        self.preload_ids = []
        self.error_examples = Counter()
        self.config = {'run_id': self.run_id, 'app_url': args.base,
                       'measurement_window_s': args.duration, 'warmup_s': args.warmup,
                       'concurrency': args.concurrency, 'operation': args.operation,
                       'request_timeout_s': args.timeout, 'read_page_size': 20,
                       'preload_bookings': args.seed_count,
                       'rate_limit_strategy': 'dedicated simulated benchmark IP per POST; server limit unchanged',
                       'write_scope': 'HTTP POST /bookings: Firestore transaction; Scylla journal is asynchronous',
                       'read_scope': 'HTTP GET /events/by-result, first page of 20 successful events',
                       'latency_population': 'successful requests only; nearest-rank p95',
                       'throughput_denominator': 'actual wall time including completion of in-flight requests',
                       'raw_results': str(self.output / 'raw')}

    def json_file(self, path, value):
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(json.dumps(value, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')

    def write_headers(self):
        value = next(self.ip_counter) % 131072
        # 198.18.0.0/15 is reserved for benchmarks. These are header values only.
        ip = f'198.{18 + (value >> 16)}.{(value >> 8) & 255}.{value & 255}'
        return {'X-Forwarded-For': ip}

    def expect(self, client, method, path, expected, body=None):
        code, raw = client.request(method, path, body, self.write_headers() if method == 'POST' else None)
        if code != expected:
            raise RuntimeError(f'{method} {path}: ожидался HTTP {expected}, получен {code}: {raw[:1000]}')
        return json.loads(raw) if raw else None

    def preflight(self):
        print('Подготовка: проверка API и создание данных для чтения (в замер не входят).', flush=True)
        client = Client(self.args.base, self.args.timeout)
        try:
            services = self.expect(client, 'GET', '/services', 200)
            if not any(service.get('id') == 1 for service in services):
                raise RuntimeError('В каталоге отсутствует услуга №1 для теста')
            for _ in range(self.args.seed_count):
                booking = self.expect(client, 'POST', '/bookings', 201, self.body)
                self.preload_ids.append(booking['id'])
            deadline = time.monotonic() + 30
            last_id = self.preload_ids[-1]
            day = datetime.now(timezone.utc).date().isoformat()
            witness = '/events/by-booking?' + urlencode({'bookingId': last_id, 'from': day + 'T00:00:00Z',
                                                       'to': day + 'T23:59:59.999Z', 'limit': 20})
            while True:
                events = self.expect(client, 'GET', witness, 200)
                page = self.expect(client, 'GET', self.read_path, 200)
                if events.get('items') and len(page.get('items', [])) == 20:
                    break
                if time.monotonic() >= deadline:
                    raise RuntimeError('За 30 секунд не появилась подготовленная страница из 20 событий')
                time.sleep(0.5)
            self.config['preload_booking_ids'] = self.preload_ids
            print(f'API готов. Создано {len(self.preload_ids)} тестовых заявок; страница из 20 событий читается.', flush=True)
        finally:
            client.close()

    def phase(self, pool, clients, operation, seconds):
        ready = threading.Barrier(len(clients) + 1)
        start_event = threading.Event()
        timing = {}

        def worker(index):
            rows = []
            client = clients[index]
            ready.wait()
            start_event.wait()
            while time.monotonic() < timing['deadline']:
                headers = self.write_headers() if operation == 'write' else None
                began = time.perf_counter()
                if operation == 'write':
                    code, raw = client.request('POST', '/bookings', self.body, headers)
                else:
                    code, raw = client.request('GET', self.read_path)
                finished = time.perf_counter()
                success = code == (201 if operation == 'write' else 200)
                rows.append({'worker': index + 1,
                             'started_ms': round((began - timing['start']) * 1000, 6),
                             'finished_ms': round((finished - timing['start']) * 1000, 6),
                             'latency_ms': (finished - began) * 1000,
                             'status': code, 'success': success,
                             'error': '' if success else raw[:1500].replace('\n', ' ')})
            return rows

        futures = [pool.submit(worker, index) for index in range(len(clients))]
        ready.wait()
        timing['start'] = time.perf_counter()
        timing['deadline'] = time.monotonic() + seconds
        start_event.set()
        rows = [row for future in futures for row in future.result()]
        return rows, time.perf_counter() - timing['start']

    def case(self, concurrency, operation):
        clients = [Client(self.args.base, self.args.timeout) for _ in range(concurrency)]
        try:
            with concurrent.futures.ThreadPoolExecutor(max_workers=concurrency) as pool:
                print(f'Прогрев: {operation}, параллелизм {concurrency}, {self.args.warmup:g} с.', flush=True)
                if self.args.warmup:
                    self.phase(pool, clients, operation, self.args.warmup)
                print(f'Замер: {operation}, параллелизм {concurrency}, {self.args.duration:g} с + завершение запросов.', flush=True)
                rows, elapsed = self.phase(pool, clients, operation, self.args.duration)
            if not rows:
                raise RuntimeError('Не выполнено ни одного запроса в измеряемой фазе')
            raw_dir = self.output / 'raw'
            raw_dir.mkdir(exist_ok=True)
            with (raw_dir / f'{operation}_c{concurrency}.csv').open('w', newline='', encoding='utf-8') as stream:
                writer = csv.DictWriter(stream, fieldnames=RAW_FIELDS)
                writer.writeheader()
                writer.writerows(rows)
            for row in rows:
                if not row['success']:
                    self.error_examples[(str(row['status']), row['error'])] += 1
            summary = summarize(concurrency, operation, rows, elapsed)
            self.summaries.append(summary)
            self.save_results()
            avg = f"{summary['avg_ms']:.2f}" if summary['avg_ms'] is not None else 'нет успешных'
            p95 = f"{summary['p95_ms']:.2f}" if summary['p95_ms'] is not None else 'нет успешных'
            print(f"  Готово: успешно {summary['success']}/{summary['total']}; "
                  f"успешных/с {summary['success_rps']:.2f}; avg {avg} мс; p95 {p95} мс; "
                  f"ошибки {summary['error_rate_pct']:.1f}%.", flush=True)
        finally:
            for client in clients:
                client.close()

    @staticmethod
    def display_number(value):
        if value is None:
            return '—'
        return f'{value:,.2f}'.replace(',', ' ').replace('.', ',')

    def table_rows(self):
        labels = {'write': 'Создание заявки', 'read': 'Чтение событий'}
        return [[str(row['concurrency']), labels[row['operation']],
                 self.display_number(row['success_rps']), self.display_number(row['avg_ms']),
                 self.display_number(row['p95_ms']), self.display_number(row['error_rate_pct'])]
                for row in self.summaries]

    def report(self):
        headers = ['Параллелизм', 'Операция', 'Успешных/с', 'Среднее, мс', 'p95, мс', 'Ошибки, %']
        rows = self.table_rows()
        widths = [max([len(header)] + [len(row[i]) for row in rows]) for i, header in enumerate(headers)]
        def border(left, middle, right):
            return left + middle.join('─' * (width + 2) for width in widths) + right
        def line(values, heading=False):
            cells = []
            for i, (value, width) in enumerate(zip(values, widths)):
                aligned = value.center(width) if heading else (value.ljust(width) if i == 1 else value.rjust(width))
                cells.append(' ' + aligned + ' ')
            return '│' + '│'.join(cells) + '│'
        lines = [border('┌', '┬', '┐'), line(headers, True)]
        if rows:
            lines.append(border('├', '┼', '┤'))
        for i, row in enumerate(rows):
            lines.append(line(row))
            if i + 1 < len(rows) and rows[i + 1][0] != row[0]:
                lines.append(border('├', '┼', '┤'))
        lines.append(border('└', '┴', '┘'))
        return '\n'.join(lines) + '\n'

    def table_html(self):
        headers = ['Параллелизм', 'Операция', 'Успешных/с', 'Среднее, мс', 'p95, мс', 'Ошибки, %']
        values = self.table_rows()
        counts = Counter(row[0] for row in values)
        seen = set()
        body = []
        for index, row in enumerate(values):
            group_start = row[0] not in seen
            cells = []
            if group_start:
                cells.append(f'<td class="parallel" rowspan="{counts[row[0]]}">{row[0]}</td>')
                seen.add(row[0])
            kind = 'write' if self.summaries[index]['operation'] == 'write' else 'read'
            cells.append(f'<td class="operation"><span class="badge {kind}">{row[1]}</span></td>')
            for cell in row[2:5]:
                cells.append(f'<td class="number">{cell}</td>')
            error_class = 'error' if self.summaries[index]['error_rate_pct'] > 0 else 'zero'
            cells.append(f'<td class="number {error_class}">{row[5]}</td>')
            body.append('<tr' + (' class="group-start"' if group_start and index else '') + '>' + ''.join(cells) + '</tr>')
        head = ''.join('<th>' + title + '</th>' for title in headers)
        return """<!doctype html>
<html lang="ru"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>Таблица</title><style>
*{box-sizing:border-box}body{margin:0;padding:40px 28px;background:#f3f5f9;color:#172033;font-family:-apple-system,BlinkMacSystemFont,"Segoe UI",Arial,sans-serif}
.table-wrap{max-width:1120px;margin:0 auto;background:white;border:1px solid #e0e5ee;border-radius:16px;overflow:auto;box-shadow:0 10px 32px #2336510c}
table{width:100%;border-collapse:collapse;font-size:16px;font-variant-numeric:tabular-nums;white-space:nowrap}
th{background:#192d48;color:#fff;font-size:13px;font-weight:600;text-align:right;padding:20px 22px}
th:first-child{text-align:center}th:nth-child(2){text-align:left}td{padding:18px 22px;border-bottom:1px solid #edf0f5}
tr:last-child td{border-bottom:0}.group-start td{border-top:2px solid #dfe5ef}.parallel{text-align:center;font-weight:700;font-size:20px;background:#f8faff;border-right:1px solid #edf0f5;border-bottom:2px solid #dfe5ef}
.operation{text-align:left}.number{text-align:right}.badge{display:inline-block;border-radius:7px;padding:7px 10px;font-size:13px;font-weight:600}
.write{color:#3449a4;background:#eef0ff}.read{color:#087b6b;background:#e8f7f2}.error{color:#c43c45;font-weight:650}.zero{color:#7a8698}
@media print{body{padding:0;background:white}.table-wrap{box-shadow:none}th{background:#eef1f6;color:#172033}}
</style></head><body><div class="table-wrap"><table><thead><tr>""" + head + '</tr></thead><tbody>' + ''.join(body) + '</tbody></table></div></body></html>'

    def save_results(self):
        metadata = {**self.config, 'status': self.status,
                    'completed_cases': len(self.summaries),
                    'error_examples': [{'status': code, 'body': text, 'count': count}
                                       for (code, text), count in self.error_examples.most_common(10)]}
        for folder in [self.output, self.latest]:
            self.json_file(folder / 'metadata.json', metadata)
            with (folder / 'summary.csv').open('w', newline='', encoding='utf-8') as stream:
                writer = csv.DictWriter(stream, fieldnames=FIELDS)
                writer.writeheader()
                writer.writerows(self.summaries)
            (folder / 'report.txt').write_text(self.report(), encoding='utf-8')
            (folder / 'report.html').write_text(self.table_html(), encoding='utf-8')

    def execute(self):
        self.output.mkdir(parents=True)
        self.latest.mkdir(parents=True, exist_ok=True)
        self.save_results()
        print('Результаты запуска:', self.output, flush=True)
        try:
            self.preflight()
            operations = ['write', 'read'] if self.args.operation == 'all' else [self.args.operation]
            for concurrency in self.args.concurrency:
                for operation in operations:
                    self.case(concurrency, operation)
            self.status = 'complete'
            self.save_results()
            print('ГОТОВО. Для защиты открой results/loadtest/latest/report.txt', flush=True)
            print('Числа: results/loadtest/latest/summary.csv; отдельные запросы: ' + str(self.output / 'raw'), flush=True)
            return 0
        except (Exception, KeyboardInterrupt) as error:
            self.status = 'failed'
            self.config['fatal_error'] = str(error) or 'Прервано пользователем'
            self.save_results()
            print('ОПЫТ ПРЕРВАН:', self.config['fatal_error'], file=sys.stderr, flush=True)
            return 1


def arguments():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--base', default=os.environ.get('APP_URL', 'http://localhost:8080'))
    parser.add_argument('--duration', type=float, default=float(os.environ.get('DURATION_SEC', '30')))
    parser.add_argument('--warmup', type=float, default=float(os.environ.get('WARMUP_SEC', '10')))
    parser.add_argument('--timeout', type=float, default=30)
    parser.add_argument('--concurrency', type=int, nargs='+', default=[1, 5, 10, 20, 50])
    parser.add_argument('--operation', choices=['all', 'read', 'write'], default='all')
    parser.add_argument('--seed-count', type=int, default=20)
    args = parser.parse_args()
    if args.duration <= 0 or args.warmup < 0 or args.timeout <= 0 or min(args.concurrency) < 1 or args.seed_count < 20:
        parser.error('Нужны duration/timeout/concurrency > 0, warmup >= 0 и seed-count >= 20')
    if len(set(args.concurrency)) != len(args.concurrency):
        parser.error('Значения concurrency не должны повторяться')
    return args


if __name__ == '__main__':
    sys.exit(LoadTest(arguments()).execute())
