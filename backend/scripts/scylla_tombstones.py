#!/usr/bin/env python3
"""Reproducible ScyllaDB tombstone experiment; Python standard library only.

Uses a new RF=3 table on every run. ALL writes/deletes, explicit timeout GC,
disabled automatic compaction, a grace-period wait, a full-node flush and one
major compaction per node make the stages observable without bypassing GC.
Raw SSTable dumps and checked, readable reports are saved inside the project.
"""
import json
import os
import re
import subprocess
import sys
import time
from datetime import datetime, timezone
from pathlib import Path


NODES = ('scylla1', 'scylla2', 'scylla3')
KEYSPACE = 'university_defense'
GRACE_SECONDS = 60
SAFETY_SECONDS = 5
PROJECT = Path(__file__).resolve().parents[2]


class Experiment:
    def __init__(self):
        self.run_id = datetime.now(timezone.utc).strftime('%Y%m%d_%H%M%S_%f') + '_' + str(os.getpid())
        self.table = 'tombstone_' + self.run_id
        self.output = PROJECT / 'results' / 'tombstones' / self.run_id
        self.latest = PROJECT / 'results' / 'tombstones' / 'latest'
        self.schema_path = '/tmp/' + self.table + '.cql'
        self.disabled = []
        self.stages = {}
        self.compactions = {node: 0 for node in NODES}

    def save_json(self, path, value):
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(json.dumps(value, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')

    def run(self, args, *, input_text=None, timeout=120):
        start = time.monotonic()
        result = subprocess.run(args, input=input_text, text=True, capture_output=True, timeout=timeout)
        record = {'command': args, 'return_code': result.returncode,
                  'seconds': round(time.monotonic() - start, 3),
                  'stdout': result.stdout, 'stderr': result.stderr}
        with (self.output / 'commands.jsonl').open('a', encoding='utf-8') as stream:
            stream.write(json.dumps(record, ensure_ascii=False) + '\n')
        if result.returncode:
            raise RuntimeError('Команда завершилась с ошибкой: ' + ' '.join(args)
                               + '\n' + (result.stderr + result.stdout)[-3000:])
        return result.stdout, result.stderr

    def docker(self, node, *args):
        return self.run(['docker', 'exec', node, *args])

    def cql(self, query):
        stdout, stderr = self.docker('scylla1', 'cqlsh', '-e', query)
        errors = r'(?im)^\s*(?:SyntaxException|InvalidRequest|ConfigurationException|AlreadyExists|NoHostAvailable|Unavailable|ReadTimeout|WriteTimeout|OperationTimedOut|Traceback|Connection error)\b'
        if re.search(errors, stdout + stderr):
            raise RuntimeError('Ошибка CQL:\n' + stdout + stderr)
        return stdout

    def count(self, where=''):
        query = f'CONSISTENCY ALL; SELECT COUNT(*) FROM {KEYSPACE}.{self.table}{where};'
        text = self.cql(query)
        values = re.findall(r'(?m)^\s*(\d+)\s*$', text)
        if len(values) != 1:
            raise RuntimeError('Не удалось прочитать COUNT из ответа:\n' + text)
        return int(values[0])

    def preflight(self):
        for node in NODES:
            raw, _ = self.run(['docker', 'inspect', node, '--format', '{{json .State}}'])
            state = json.loads(raw)
            if not state.get('Running') or state.get('Health', {}).get('Status') != 'healthy':
                raise RuntimeError(f'{node} не готов. Перед опытом нужны три healthy-узла.')

    def flush(self, all_tables=False):
        for node in NODES:
            args = ('nodetool', 'flush') if all_tables else ('nodetool', 'flush', KEYSPACE, self.table)
            self.docker(node, *args)

    def capture(self, stage, title):
        report = {'run_id': self.run_id, 'stage': title,
                  'table': KEYSPACE + '.' + self.table,
                  'live_rows': self.count(),
                  'deleted_partition_1_live_rows': self.count(' WHERE pk=1'),
                  'untouched_partition_3_live_rows': self.count(' WHERE pk=3'),
                  'nodes': {}}
        for node in NODES:
            text, _ = self.docker(node, 'find', '/var/lib/scylla/data/' + KEYSPACE,
                                  '-mindepth', '1', '-maxdepth', '1', '-type', 'd', '-name', self.table + '-*')
            dirs = text.splitlines()
            if len(dirs) != 1:
                raise RuntimeError(f'Не найдена однозначная директория таблицы на {node}: {dirs}')
            text, _ = self.docker(node, 'find', dirs[0], '-maxdepth', '1', '-name', '*-Data.db')
            files = sorted(text.splitlines())
            if not files:
                raise RuntimeError(f'Нет активных SSTable для проверки на {node}')
            details = {'sstable_files': len(files), 'row_versions_in_files': 0,
                       'partition_tombstone_count': 0, 'partition_tombstones': [],
                       'partition_keys_in_files': [], 'raw_dump_files': []}
            keys = set()
            for index, file in enumerate(files, 1):
                stdout, stderr = self.docker(node, 'scylla', 'sstable', 'dump-data',
                                             '--schema-file', self.schema_path, file)
                raw_dir = self.output / 'raw' / stage / node
                raw_dir.mkdir(parents=True, exist_ok=True)
                raw_path = raw_dir / f'{index}.json'
                raw_path.write_text(stdout, encoding='utf-8')
                (raw_dir / f'{index}.stderr').write_text(stderr, encoding='utf-8')
                data = json.loads(stdout)
                details['raw_dump_files'].append(str(raw_path.relative_to(PROJECT)))
                for partitions in data['sstables'].values():
                    for partition in partitions:
                        key = partition['key']['value']
                        keys.add(key)
                        details['row_versions_in_files'] += sum(
                            item.get('type') == 'clustering-row'
                            for item in partition.get('clustering_elements', []))
                        marker = partition.get('tombstone')
                        if marker is not None:
                            details['partition_tombstone_count'] += 1
                            details['partition_tombstones'].append({'partition_key': key, **marker})
            details['partition_keys_in_files'] = sorted(keys)
            report['nodes'][node] = details
        self.stages[stage] = report
        self.save_json(self.output / f'{stage}.json', report)
        self.save_json(self.latest / f'{stage}.json', report)
        return report

    def check(self, report, expected_live, expected_disk, expected_markers, expected_keys):
        if report['live_rows'] != expected_live:
            raise RuntimeError(f"Читаемых строк: {report['live_rows']}, ожидалось {expected_live}")
        for node, data in report['nodes'].items():
            observed = (data['row_versions_in_files'], data['partition_tombstone_count'],
                        set(data['partition_keys_in_files']))
            expected = (expected_disk, expected_markers, expected_keys)
            if observed != expected:
                raise RuntimeError(f'Дампы {node} не подтвердили ожидаемое состояние: {observed}; ожидалось {expected}')
        if expected_markers:
            for node, data in report['nodes'].items():
                deleted_keys = {item['partition_key'] for item in data['partition_tombstones']}
                if deleted_keys != {'1', '2'}:
                    raise RuntimeError(f'Неожиданные удалённые партиции на {node}: {deleted_keys}')

    def setup(self):
        self.cql(f"CREATE KEYSPACE IF NOT EXISTS {KEYSPACE} WITH replication = "
                 "{'class':'NetworkTopologyStrategy','datacenter1':3};")
        replication = self.cql(f"SELECT replication FROM system_schema.keyspaces WHERE keyspace_name='{KEYSPACE}';")
        if 'NetworkTopologyStrategy' not in replication or not re.search(r"'datacenter1'\s*:\s*'3'", replication):
            raise RuntimeError('Тестовый keyspace должен иметь RF=3 в datacenter1:\n' + replication)
        columns = f'CREATE TABLE {KEYSPACE}.{self.table} (pk int, ck int, val text, PRIMARY KEY (pk, ck))'
        ddl = columns + " WITH gc_grace_seconds=60 AND tombstone_gc={'mode':'timeout'} AND compaction={'class':'SizeTieredCompactionStrategy'};"
        self.cql(ddl)
        actual = self.cql(f'DESCRIBE TABLE {KEYSPACE}.{self.table};')
        if not re.search(r'gc_grace_seconds\s*=\s*60\b', actual) or not re.search(r"'mode'\s*:\s*'timeout'", actual):
            raise RuntimeError('Не подтверждены параметры GC тестовой таблицы:\n' + actual)
        (self.output / 'table.cql').write_text(actual, encoding='utf-8')
        # Scylla 6.2's offline schema reader rejects some valid server properties.
        # A minimal schema decodes data; it does not alter the online table options.
        minimal = columns + ';\n'
        (self.output / 'dump-schema.cql').write_text(minimal, encoding='utf-8')
        for node in NODES:
            self.run(['docker', 'exec', '-i', node, 'sh', '-c', 'cat > ' + self.schema_path], input_text=minimal)
            self.docker(node, 'nodetool', 'disableautocompaction', KEYSPACE, self.table)
            self.disabled.append(node)

    def insert(self):
        statements = ['CONSISTENCY ALL;']
        for pk in range(1, 6):
            for ck in range(1, 21):
                statements.append(f"INSERT INTO {KEYSPACE}.{self.table} (pk,ck,val) VALUES ({pk},{ck},'value_{pk}_{ck}');")
        script = '\n'.join(statements)
        (self.output / 'insert.cql').write_text(script, encoding='utf-8')
        # One CQL session; do not hide CQL errors even if cqlsh returns code zero.
        self.cql(script)

    def wait_for_grace(self, before):
        deletion_times = []
        for data in before['nodes'].values():
            for marker in data['partition_tombstones']:
                value = datetime.strptime(marker['deletion_time'], '%Y-%m-%d %H:%M:%Sz').replace(tzinfo=timezone.utc)
                deletion_times.append(int(value.timestamp()))
        target = max(deletion_times) + GRACE_SECONDS + SAFETY_SECONDS
        node_times = [int(self.docker(node, 'date', '+%s')[0].strip()) for node in NODES]
        seconds = max(0, target - min(node_times))
        print(f'4/6. Ожидание истечения gc_grace_seconds на узлах: {seconds} с.', flush=True)
        time.sleep(seconds)

    def summary(self, verified, error=None):
        value = {'run_id': self.run_id, 'table': KEYSPACE + '.' + self.table,
                 'verified': verified, 'gc_grace_seconds': GRACE_SECONDS,
                 'tombstone_gc': 'timeout', 'write_and_delete_consistency': 'ALL',
                 'major_compactions_per_node': self.compactions.copy(), 'stages': {}}
        for stage, report in self.stages.items():
            value['stages'][stage] = {'live_rows': report['live_rows'], 'nodes': {
                node: {'row_versions_in_files': data['row_versions_in_files'],
                       'partition_tombstone_count': data['partition_tombstone_count']}
                for node, data in report['nodes'].items()}}
        if error:
            value['error'] = error
        self.save_json(self.output / 'summary.json', value)
        self.save_json(self.latest / 'summary.json', value)

    def execute(self):
        self.output.mkdir(parents=True)
        self.latest.mkdir(parents=True, exist_ok=True)
        for stage in ['initial', 'before', 'after']:
            self.save_json(self.latest / f'{stage}.json', {'run_id': self.run_id, 'status': 'ещё не измерено'})
        self.save_json(self.latest / 'summary.json', {'run_id': self.run_id, 'verified': False, 'status': 'опыт выполняется'})
        print('Результаты:', self.output, flush=True)
        print('1/6. Проверка трёх узлов и создание отдельной тестовой таблицы.', flush=True)
        try:
            self.preflight()
            self.setup()
            print('2/6. Вставка 100 строк, flush, сохранение начального состояния.', flush=True)
            self.insert()
            self.flush()
            initial = self.capture('initial', 'До DELETE: 100 строк, маркеров удаления нет')
            self.check(initial, 100, 100, 0, {'1', '2', '3', '4', '5'})
            print('3/6. DELETE двух партиций (40 строк), flush и сохранение tombstones.', flush=True)
            self.cql(f'CONSISTENCY ALL; DELETE FROM {KEYSPACE}.{self.table} WHERE pk=1; DELETE FROM {KEYSPACE}.{self.table} WHERE pk=2;')
            self.flush()
            before = self.capture('before', 'После DELETE, до compaction: строки скрыты, tombstones сохранены')
            self.check(before, 60, 100, 2, {'1', '2', '3', '4', '5'})
            self.wait_for_grace(before)
            print('5/6. Полный flush каждого узла и одна major compaction тестовой таблицы на узел.', flush=True)
            # Commitlog segments are shared among tables. Flushing only this table
            # can retain older segments and prevent tombstone purging. Major compaction's
            # automatic full flush is rate-limited, so perform it explicitly every run.
            self.flush(all_tables=True)
            for node in NODES:
                self.docker(node, 'nodetool', 'compact', KEYSPACE, self.table)
                self.compactions[node] += 1
            after = self.capture('after', 'После compaction: 60 строк, tombstones удалены')
            self.check(after, 60, 60, 0, {'3', '4', '5'})
            if after['deleted_partition_1_live_rows'] != 0 or after['untouched_partition_3_live_rows'] != 20:
                raise RuntimeError('Не выполнена проверка удалённой и сохранённой партиций')
            self.summary(True)
            print('6/6. ПРОВЕРЕНО по реальным SSTable всех трёх узлов:', flush=True)
            for node in NODES:
                print(f'  {node}: версии строк 100 -> 60; tombstones 2 -> 0; читаемых строк 60.', flush=True)
            print('Для показа в IDEA открой results/tombstones/latest/:', flush=True)
            print('  initial.json — до DELETE; before.json — маркеры после DELETE;', flush=True)
            print('  after.json — маркеров нет; summary.json — проверенные итоги.', flush=True)
            print('Полные исходные дампы находятся в', self.output / 'raw', flush=True)
            return 0
        except (Exception, KeyboardInterrupt) as error:
            self.summary(False, str(error) or 'Прервано пользователем')
            print('ОПЫТ НЕ ПОДТВЕРЖДЁН:', str(error), file=sys.stderr, flush=True)
            return 1
        finally:
            for node in self.disabled:
                try:
                    self.docker(node, 'nodetool', 'enableautocompaction', KEYSPACE, self.table)
                except Exception as error:
                    print(f'Не удалось восстановить autocompaction на {node}: {error}', file=sys.stderr)


if __name__ == '__main__':
    sys.exit(Experiment().execute())
