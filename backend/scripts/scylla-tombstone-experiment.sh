#!/usr/bin/env bash
# =============================================================================
# Исследование образования и удаления tombstones в ScyllaDB
# Шаги:
#   1) Создать отдельную таблицу tombstone_test
#   2) Вставить строки, сделать flush → sstable с данными
#   3) Удалить часть строк → tombstones в memtable
#   4) Flush → tombstones в sstable
#   5) Показать маркеры через scylla sstable dump-data
#   6) Дождаться gc_grace_seconds (установлен 60 сек для теста), compaction
#   7) Сравнить sstable до/после, проверить что удалённые строки не читаются
# =============================================================================
set -euo pipefail

CQLSH_CMD() { echo "$1" | docker exec -i scylla1 cqlsh; }
NODETOOL()  { docker exec scylla1 nodetool "$@"; }
SCYLLA_HOME="/var/lib/scylla/data/university_events"

echo ""
echo "=== Шаг 1: создаём тестовую таблицу с gc_grace_seconds=60 ==="
CQLSH_CMD "
CREATE TABLE IF NOT EXISTS university_events.tombstone_test (
    pk   int,
    ck   int,
    val  text,
    PRIMARY KEY (pk, ck)
) WITH gc_grace_seconds = 60
  AND compaction = {'class': 'SizeTieredCompactionStrategy'};
"
echo "Таблица готова"

echo ""
echo "=== Шаг 2: вставляем 100 строк ==="
for pk in 1 2 3 4 5; do
    for ck in $(seq 1 20); do
        CQLSH_CMD "INSERT INTO university_events.tombstone_test (pk, ck, val) VALUES ($pk, $ck, 'value_${pk}_${ck}');" 2>/dev/null
    done
done
echo "Вставлено 100 строк"

echo ""
echo "=== Flush → первый sstable ==="
NODETOOL flush university_events tombstone_test
echo "После flush:"
docker exec scylla1 ls "$SCYLLA_HOME/tombstone_test-"*/ 2>/dev/null | head -20 || \
    docker exec scylla1 find /var/lib/scylla/data/university_events -name "*.db" 2>/dev/null | head -10

echo ""
echo "=== Шаг 3: удаляем строки с pk=1 и pk=2 (40 строк) ==="
CQLSH_CMD "DELETE FROM university_events.tombstone_test WHERE pk = 1;"
CQLSH_CMD "DELETE FROM university_events.tombstone_test WHERE pk = 2;"
echo "Строки удалены (tombstones в memtable)"

echo ""
echo "=== Шаг 4: flush → tombstones в sstable ==="
NODETOOL flush university_events tombstone_test
echo "SSTable после удаления:"
docker exec scylla1 find /var/lib/scylla/data/university_events -name "*.db" 2>/dev/null | head -10

echo ""
echo "=== Шаг 5: dump sstable — ищем tombstone маркеры ==="
SSTABLE_DIR=$(docker exec scylla1 find /var/lib/scylla/data/university_events -name "tombstone_test-*" -type d 2>/dev/null | head -1)
if [ -n "$SSTABLE_DIR" ]; then
    DATA_FILE=$(docker exec scylla1 find "$SSTABLE_DIR" -name "*-Data.db" 2>/dev/null | head -1)
    if [ -n "$DATA_FILE" ]; then
        echo "Файл данных: $DATA_FILE"
        echo "Дамп (ищем tombstone маркеры):"
        docker exec scylla1 scylla sstable dump-data "$DATA_FILE" 2>/dev/null | \
            python3 -c "
import sys, json
raw = sys.stdin.read().strip()
try:
    data = json.loads(raw)
except Exception:
    print(raw[:2000])
    sys.exit(0)
partitions = data if isinstance(data, list) else list(data.values()) if isinstance(data, dict) else []
found = 0
for p in partitions:
    text = json.dumps(p)
    if any(k in text for k in ('tombstone', 'deleted', 'deletion_time')):
        print(json.dumps(p, indent=2))
        found += 1
if found == 0:
    print('(tombstone маркеры не найдены в выводе dump-data)')
" || echo "(dump-data недоступен — используйте: docker exec scylla1 scylla sstable dump-data <файл>)"
    fi
else
    echo "SSTable директория не найдена. Попробуйте:"
    echo "  docker exec scylla1 find /var/lib/scylla/data/university_events -name '*.db'"
fi

echo ""
echo "=== Проверяем, что удалённые строки не читаются ==="
COUNT=$(CQLSH_CMD "SELECT COUNT(*) FROM university_events.tombstone_test WHERE pk = 1;" 2>/dev/null | grep -E '^\s+[0-9]' | tr -d ' ' || echo "?")
echo "Строк с pk=1: $COUNT (ожидается 0)"

echo ""
echo "=== Шаг 6: ждём gc_grace_seconds (60 сек) + запускаем compaction ==="
echo "Ожидание 65 секунд..."
sleep 65
NODETOOL compact university_events tombstone_test
echo "Compaction выполнен"

echo ""
echo "=== Шаг 7: SSTable после compaction (tombstones должны исчезнуть) ==="
docker exec scylla1 find /var/lib/scylla/data/university_events -name "*.db" 2>/dev/null | head -10
echo ""
COUNT_AFTER=$(CQLSH_CMD "SELECT COUNT(*) FROM university_events.tombstone_test WHERE pk = 1;" 2>/dev/null | grep -E '^\s+[0-9]' | tr -d ' ' || echo "?")
echo "Строк с pk=1 после compaction: $COUNT_AFTER (ожидается 0 — данные физически удалены)"
echo ""
echo "=== Эксперимент завершён ==="
