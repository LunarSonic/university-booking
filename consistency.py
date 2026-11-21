#!/usr/bin/env python3
"""Measure CQL client invocations in one cluster phase, on an isolated probe table."""
import argparse
import csv
import math
import re
import statistics
import subprocess
import time
import uuid
from pathlib import Path

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--phase', choices=['baseline', 'degraded', 'recovered'], required=True)
parser.add_argument('--iterations', type=int, default=10)
args = parser.parse_args()
if args.iterations < 1:
    parser.error('iterations must be positive')
out = Path('defense/results/consistency')
out.mkdir(parents=True, exist_ok=True)


def cql(query):
    start = time.perf_counter()
    result = subprocess.run(['docker', 'exec', 'scylla1', 'cqlsh', '-e', query],
                            capture_output=True, text=True, timeout=60)
    ms = (time.perf_counter() - start) * 1000
    log = result.stdout + result.stderr
    ok = result.returncode == 0 and not re.search(r'(?im)^.*(?:Error:|Exception:|Traceback|Unavailable:)', log)
    return bool(ok), ms, log


ok, _, log = cql("CREATE KEYSPACE IF NOT EXISTS university_defense WITH replication = "
                "{'class':'NetworkTopologyStrategy','datacenter1':3}; "
                "CREATE TABLE IF NOT EXISTS university_defense.probe (id uuid PRIMARY KEY, value text);")
if not ok:
    raise SystemExit(log)
rows = []
for index in range(args.iterations):
    for level in ['ONE', 'QUORUM']:
        event_id = uuid.uuid4()
        marker = f'{args.phase}-{index}-{level}'
        query = f"CONSISTENCY {level}; INSERT INTO university_defense.probe (id,value) VALUES ({event_id},'{marker}');"
        ok, ms, log = cql(query)
        rows.append({'operation': 'write_' + level, 'ok': ok, 'ms': ms, 'output': log})
        ok, ms, log = cql(f'CONSISTENCY QUORUM; SELECT value FROM university_defense.probe WHERE id={event_id};')
        rows.append({'operation': 'read_QUORUM_after_' + level, 'ok': ok, 'ms': ms,
                     'output': log, 'value_visible': marker in log})
with (out / f'{args.phase}-raw.csv').open('w', newline='') as stream:
    writer = csv.DictWriter(stream, fieldnames=['operation', 'ok', 'ms', 'output', 'value_visible'])
    writer.writeheader()
    writer.writerows(rows)
summary = []
for operation in sorted({row['operation'] for row in rows}):
    subset = [row for row in rows if row['operation'] == operation]
    values = sorted(row['ms'] for row in subset if row['ok'])
    item = {'phase': args.phase, 'operation': operation, 'success': len(values),
            'errors': len(subset) - len(values),
            'avg_ms': round(statistics.mean(values), 2) if values else '',
            'p95_ms': round(values[math.ceil(len(values) * 0.95) - 1], 2) if values else '',
            'visible_reads': sum(row.get('value_visible', False) for row in subset) if operation.startswith('read') else ''}
    summary.append(item)
    print(item)
with (out / f'{args.phase}-summary.csv').open('w', newline='') as stream:
    writer = csv.DictWriter(stream, fieldnames=list(summary[0]))
    writer.writeheader()
    writer.writerows(summary)
print('Latency includes docker exec + cqlsh startup. These are not pure server latency measurements.')
