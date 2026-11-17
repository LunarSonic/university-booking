#!/usr/bin/env bash
# =============================================================================
# Нагрузочный эксперимент ScyllaDB — измерение throughput, latency, p95
# Участник 2 — Лабораторная работа 3
#
# Параметры:
#   APP_URL       — адрес приложения (default: http://localhost:8080)
#   DURATION_SEC  — длительность каждого прогона (default: 30)
#   WARMUP_SEC    — прогрев перед замерами (default: 10)
#
# Прогоны: параллелизм 1, 5, 10, 20, 50
# =============================================================================
set -euo pipefail

APP_URL="${APP_URL:-http://localhost:8080}"
DURATION_SEC="${DURATION_SEC:-30}"
WARMUP_SEC="${WARMUP_SEC:-10}"
RESULTS_DIR="./results/loadtest"
mkdir -p "$RESULTS_DIR"
TMPDIR_LOCAL=$(mktemp -d)
trap "rm -rf $TMPDIR_LOCAL" EXIT

# Дата начала теста (для чтения событий)
TEST_START=$(date -u '+%Y-%m-%dT%H:%M:%SZ')

# ── Одна итерация записи (создание заявки) ──────────────────────────────────
ms_now() { python3 -c "import time; print(int(time.time()*1000))"; }

write_once() {
    local booking_date
    booking_date=$(python3 -c "from datetime import datetime, timedelta; print((datetime.utcnow() + timedelta(days=1)).strftime('%Y-%m-%dT%H:%M:%S'))")
    # Случайный IP для обхода rate limiting в нагрузочном тесте
    local fake_ip="10.$((RANDOM % 254 + 1)).$((RANDOM % 254 + 1)).$((RANDOM % 254 + 1))"
    local start end elapsed resp
    start=$(ms_now)
    resp=$(curl -s -o /dev/null -w "%{http_code}" -X POST "$APP_URL/bookings" \
        -H "Content-Type: application/json" \
        -H "X-Forwarded-For: $fake_ip" \
        -d "{\"userId\":1,\"serviceId\":1,\"room\":101,\"bookingDate\":\"$booking_date\"}" 2>/dev/null || echo "000")
    end=$(ms_now)
    elapsed=$((end - start))
    echo "$elapsed $resp"
}

read_once() {
    local from="$TEST_START"
    local to
    to=$(date -u '+%Y-%m-%dT%H:%M:%SZ')
    local start end elapsed resp
    start=$(ms_now)
    resp=$(curl -s -o /dev/null -w "%{http_code}" \
        "$APP_URL/events/by-result?result=SUCCESS&from=${from}&to=${to}&limit=20" 2>/dev/null || echo "000")
    end=$(ms_now)
    elapsed=$((end - start))
    echo "$elapsed $resp"
}

# ── Прогон с заданным параллелизмом ─────────────────────────────────────────
# $1 — параллелизм
# $2 — "write" или "read"
run_phase() {
    local concurrency="$1"
    local op="$2"
    local latency_file="$TMPDIR_LOCAL/${op}_c${concurrency}.txt"
    > "$latency_file"

    echo "  Прогрев $WARMUP_SEC сек..."
    local warmup_end=$(( $(date +%s) + WARMUP_SEC ))
    while [ "$(date +%s)" -lt "$warmup_end" ]; do
        for _ in $(seq 1 $concurrency); do
            if [ "$op" = "write" ]; then write_once > /dev/null 2>&1 &
            else read_once > /dev/null 2>&1 &
            fi
        done
        wait
    done

    echo "  Замер $DURATION_SEC сек (concurrency=$concurrency, op=$op)..."
    local measure_end=$(( $(date +%s) + DURATION_SEC ))
    while [ "$(date +%s)" -lt "$measure_end" ]; do
        for _ in $(seq 1 $concurrency); do
            if [ "$op" = "write" ]; then
                (write_once 2>/dev/null >> "$latency_file") &
            else
                (read_once 2>/dev/null >> "$latency_file") &
            fi
        done
        wait
    done

    # Статистика
    python3 - <<PYEOF "$latency_file" "$concurrency" "$op" "$RESULTS_DIR"
import sys, statistics

latency_file, concurrency, op, results_dir = sys.argv[1:]
latencies = []
errors = 0

with open(latency_file) as f:
    for line in f:
        parts = line.strip().split()
        if len(parts) == 2:
            ms, code = int(parts[0]), parts[1]
            if code == '200' or code == '201':
                latencies.append(ms)
            else:
                errors += 1

total = len(latencies) + errors
duration = $DURATION_SEC
throughput = total / duration if duration > 0 else 0
avg_ms = statistics.mean(latencies) if latencies else 0
p95_ms = sorted(latencies)[int(len(latencies) * 0.95)] if latencies else 0

print(f"  concurrency={concurrency} op={op}: total={total} ok={len(latencies)} err={errors}")
print(f"    throughput={throughput:.1f} rps  avg={avg_ms:.0f}ms  p95={p95_ms:.0f}ms  error_rate={errors/total*100 if total else 0:.1f}%")

with open(f"{results_dir}/summary.csv", "a") as out:
    out.write(f"{concurrency},{op},{total},{len(latencies)},{errors},{throughput:.2f},{avg_ms:.0f},{p95_ms:.0f}\n")
PYEOF
}

# ── Основной сценарий ────────────────────────────────────────────────────────

echo "concurrency,operation,total,success,errors,throughput_rps,avg_ms,p95_ms" > "$RESULTS_DIR/summary.csv"

for concurrency in 1 5 10 20 50; do
    echo ""
    echo "=== Параллелизм: $concurrency ==="
    run_phase "$concurrency" "write"
    run_phase "$concurrency" "read"
done

echo ""
echo "=== Итоговая таблица ==="
echo "concurrency | op    | throughput rps | avg ms | p95 ms | error%"
echo "------------|-------|----------------|--------|--------|-------"
tail -n +2 "$RESULTS_DIR/summary.csv" | while IFS=, read -r c op total ok err thr avg p95; do
    err_pct=$(python3 -c "print(f'{$err/$total*100:.1f}%' if $total>0 else '0%')" 2>/dev/null || echo "?")
    printf "%-11s | %-5s | %-14s | %-6s | %-6s | %s\n" \
        "$c" "$op" "$thr" "$avg" "$p95" "$err_pct"
done

echo ""
echo "Результаты: $RESULTS_DIR/summary.csv"
