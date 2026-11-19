#!/usr/bin/env bash
# =============================================================================
# Эксперимент с уровнями согласованности ScyllaDB
# Сравнивает: запись ONE / чтение QUORUM  vs  запись QUORUM / чтение QUORUM
# в трёх состояниях кластера:
#   1) все 3 узла работают (baseline)
#   2) scylla3 остановлен (2 из 3 узлов)
#   3) scylla3 возвращён
# =============================================================================
set -euo pipefail

APP_URL="${APP_URL:-http://localhost:8080}"
CQLSH="docker exec scylla1 cqlsh"
RESULTS_DIR="./results/consistency"
mkdir -p "$RESULTS_DIR"

# ── Вспомогательные функции ──────────────────────────────────────────────────

check_cluster() {
    echo "=== nodetool status ==="
    docker exec scylla1 nodetool status
}

# Прямой тест ScyllaDB INSERT с заданным CL — измеряет latency и доступность
# $1 — метка фазы, $2 — CONSISTENCY LEVEL (ONE или QUORUM), $3 — кол-во итераций
test_scylla_insert() {
    local phase="$1" cl="$2" iters="${3:-20}"
    local ok=0 fail=0 total_ms=0
    echo "  [cql-write cl=$cl] $iters итераций..."
    for i in $(seq 1 $iters); do
        local uuid ts
        uuid=$(python3 -c "import uuid; print(uuid.uuid4())")
        ts=$(python3 -c "import time; print(int(time.time()*1000))")
        local t0 t1
        t0=$(python3 -c "import time; print(int(time.time()*1000))")
        result=$(docker exec scylla1 cqlsh \
            -e "CONSISTENCY $cl; INSERT INTO university_events.events_by_result_day (result,day_utc,occurred_at,event_id,operation,booking_id,error_code) VALUES ('SUCCESS','$(date +%Y-%m-%d)',toTimestamp(now()),$uuid,'TEST_CL',0,null);" \
            2>&1 && echo "ok" || echo "err")
        t1=$(python3 -c "import time; print(int(time.time()*1000))")
        total_ms=$(( total_ms + t1 - t0 ))
        if echo "$result" | grep -q "ok"; then ok=$((ok+1)); else fail=$((fail+1)); fi
    done
    local avg=$(( total_ms / iters ))
    echo "  [cql-write cl=$cl] ok=$ok fail=$fail avg=${avg}ms"
    echo "${phase},cql_write_${cl},$ok,$fail,$avg" >> "$RESULTS_DIR/results.csv"
}

# Создаёт заявку через API и немедленно читает её событие из ScyllaDB
# $1 — метка фазы (baseline / degraded / recovered)
write_and_read() {
    local phase="$1"
    local ok=0 fail=0 total_write_ms=0 total_read_ms=0
    local iterations=50

    echo "[$phase] Запись $iterations событий через API..."
    for i in $(seq 1 $iterations); do
        start=$(python3 -c "import time; print(int(time.time()*1000))")
        resp=$(curl -s -o /dev/null -w "%{http_code}" -X POST "$APP_URL/bookings" \
            -H "Content-Type: application/json" \
            -d "{\"userId\":1,\"serviceId\":1,\"room\":101,\"bookingDate\":\"$(date -u -v+1d '+%Y-%m-%dT%H:%M:%S')\"}" 2>/dev/null || echo "000")
        end=$(python3 -c "import time; print(int(time.time()*1000))")
        elapsed=$((end - start))
        total_write_ms=$((total_write_ms + elapsed))
        if [ "$resp" = "201" ]; then
            ok=$((ok + 1))
        else
            fail=$((fail + 1))
        fi
    done

    local avg_write=$(( total_write_ms / iterations ))

    # Теперь читаем последние SUCCESS события
    local from=$(date -u -v-1H '+%Y-%m-%dT%H:%M:%SZ')
    local to=$(date -u '+%Y-%m-%dT%H:%M:%SZ')

    local read_ok=0 read_fail=0
    for i in $(seq 1 10); do
        start=$(python3 -c "import time; print(int(time.time()*1000))")
        read_resp=$(curl -s -o /dev/null -w "%{http_code}" \
            "$APP_URL/events/by-result?result=SUCCESS&from=${from}&to=${to}&limit=5" 2>/dev/null || echo "000")
        end=$(python3 -c "import time; print(int(time.time()*1000))")
        elapsed=$((end - start))
        total_read_ms=$((total_read_ms + elapsed))
        if [ "$read_resp" = "200" ]; then
            read_ok=$((read_ok + 1))
        else
            read_fail=$((read_fail + 1))
        fi
    done
    local avg_read=$(( total_read_ms / 10 ))

    echo "[$phase] Запись: успешно=$ok, ошибок=$fail, avg=${avg_write}ms"
    echo "[$phase] Чтение: успешно=$read_ok, ошибок=$read_fail, avg=${avg_read}ms"

    echo "$phase,write,$ok,$fail,$avg_write" >> "$RESULTS_DIR/results.csv"
    echo "$phase,read,$read_ok,$read_fail,$avg_read" >> "$RESULTS_DIR/results.csv"
}

# ── Основной сценарий ────────────────────────────────────────────────────────

echo "phase,operation,success,fail,avg_ms" > "$RESULTS_DIR/results.csv"

echo ""
echo "=== Фаза 1: baseline (все 3 узла) ==="
check_cluster
write_and_read "baseline"
test_scylla_insert "baseline" "ONE"
test_scylla_insert "baseline" "QUORUM"

echo ""
echo "=== Фаза 2: остановка scylla3 (2 из 3 узлов) ==="
docker stop scylla3
echo "Ждём 10 сек., пока кластер пересчитает топологию..."
sleep 10
check_cluster
write_and_read "degraded"
test_scylla_insert "degraded" "ONE"
test_scylla_insert "degraded" "QUORUM"

echo ""
echo "=== Фаза 3: возврат scylla3 ==="
docker start scylla3
echo "Ждём 30 сек., пока scylla3 присоединится..."
sleep 30
check_cluster
write_and_read "recovered"
test_scylla_insert "recovered" "ONE"
test_scylla_insert "recovered" "QUORUM"

echo ""
echo "=== Результаты сохранены в $RESULTS_DIR/results.csv ==="
cat "$RESULTS_DIR/results.csv"
