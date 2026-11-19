# University Booking

![Java](https://img.shields.io/badge/Java-22-orange?logo=openjdk)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.1-6DB33F?logo=springboot&logoColor=white)
![Redis](https://img.shields.io/badge/Redis-DC382D?logo=redis&logoColor=white)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-4169E1?logo=postgresql&logoColor=white)
![Firestore](https://img.shields.io/badge/Firestore-FFCA28?logo=firebase&logoColor=black)
![ScyllaDB](https://img.shields.io/badge/ScyllaDB-6CD5E9?logo=scylladb&logoColor=black)
![Gradle](https://img.shields.io/badge/Gradle-02303A?logo=gradle&logoColor=white)
![Docker](https://img.shields.io/badge/Docker-2496ED?logo=docker&logoColor=white)

REST API для управления заявками на бронирование университетских услуг и ресурсов.

# Лабораторная работа №1
## Задание

| Параметр           | Значение                    |
|--------------------|-----------------------------|
| Основной объект    | Заявка                      |
| Роль               | Администратор               |
| Сценарий           | Создание и обработка заявки |
| Хранилище          | PostgreSQL + Redis          |
| Временное хранение | Временная корзина (TTL)     |
| Кэширование        | Список доступных услуг      |
| Атомарный механизм | Rate limiter для REST API   |
| Исследование       | Потеря временных ключей     |

## Архитектура хранения

| Данные                            | Хранилище  | Назначение                                    |
|-----------------------------------|------------|-----------------------------------------------|
| Заявки (BookingRequest)           | PostgreSQL | Постоянное хранение с ACID-гарантиями         |
| Каталог услуг (UniversityService) | PostgreSQL | Справочник услуг, инициализируется при старте |
| Временная корзина (BasketItem)    | Redis      | Хранение с автоматическим TTL                 |
| Кэш каталога услуг                | Redis      | Кэширование данных (TTL настраивается, по умолчанию 60 с) |
| Rate limiter                      | Redis      | Атомарный счётчик запросов                    |

## Основной сценарий

1. Пользователь создаёт заявку — `POST /bookings`
2. Администратор смотрит новые заявки — `GET /bookings?status=NEW`
3. Обрабатывает заявку — `PATCH /bookings/{id}?status=APPROVED`

Заявку можно оформить и через временную корзину: посмотреть каталог услуг (`GET /services`), добавить выбранное в корзину (`POST /basket`) и оформить её в заявку (`POST /basket/{id}/checkout`). Если корзина не оформлена в течение TTL, она удаляется автоматически.

## API

### Заявки (`/bookings`)

| Метод    | URL                      | Описание                 | Тело запроса            |
|----------|--------------------------|--------------------------|-------------------------|
| `POST`   | `/bookings`              | Создать заявку           | `BookingDto`            |
| `GET`    | `/bookings?status=`      | Список заявок по статусу | —                       |
| `GET`    | `/bookings/{id}`         | Получить заявку по ID    | —                       |
| `PATCH`  | `/bookings/{id}?status=` | Изменить статус заявки   | query-параметр `status` |
| `DELETE` | `/bookings/{id}`         | Удалить заявку           | —                       |

Статусы заявки: `NEW`, `APPROVED`, `REJECTED`.

Создание заявки:
```bash
curl -X POST http://localhost:8080/bookings \
  -H "Content-Type: application/json" \
  -d '{"userId": 1, "serviceId": 1, "room": 305, "bookingDate": "2026-12-01T10:00:00"}'
```

Список новых заявок:
```bash
curl "http://localhost:8080/bookings?status=NEW"
```

Смена статуса:
```bash
curl -X PATCH "http://localhost:8080/bookings/1?status=APPROVED"
```

### Временная корзина (`/basket`)

| Метод    | URL                     | Описание                      |
|----------|-------------------------|-------------------------------|
| `POST`   | `/basket`               | Добавить элемент в корзину    |
| `GET`    | `/basket/user/{userId}` | Получить корзину пользователя |
| `POST`   | `/basket/{id}/checkout` | Оформить элемент в заявку     |
| `DELETE` | `/basket/{id}`          | Удалить элемент из корзины    |

Добавление:
```bash
curl -X POST http://localhost:8080/basket \
  -H "Content-Type: application/json" \
  -d '{"userId": 1, "serviceId": 2, "room": 210, "bookingDate": "2026-12-01T14:00:00"}'
```

Оформление в заявку:
```bash
curl -X POST http://localhost:8080/basket/1/checkout
```

Элемент корзины читается, из него создаётся заявка со статусом `NEW`, ключ корзины удаляется. Если TTL истёк, возвращается `404`.

### Каталог услуг (`/services`)

| Метод  | URL         | Описание                                         |
|--------|-------------|--------------------------------------------------|
| `GET`  | `/services` | Список услуг (кэшируется, TTL 60 с по умолчанию) |

Первый вызов загружает данные из PostgreSQL. Последующие отдаются мгновенно из кэша.

### Rate Limiter

На все эндпоинты действует ограничение: 30 запросов в минуту с одного IP. При превышении возвращается `429 Too Many Requests`.

## Ключи в Redis

| Вид ключа                    | Тип Redis | Назначение                                        |
|------------------------------|-----------|---------------------------------------------------|
| `BasketItem:sequence`        | String    | Счётчик для автоинкремента ID корзины             |
| `BasketItem`                 | Set       | Множество всех ID корзины                         |
| `BasketItem:{id}`            | Hash      | Элемент временной корзины                         |
| `BasketItem:{id}:idx`        | Set       | Служебный: список индексов элемента (для очистки) |
| `BasketItem:userId:{userId}` | Set       | Вторичный индекс: ID элементов корзины по userId  |
| `rate_limiter:{ip}`          | String    | Счётчик запросов для rate limiter                 |
| `services::SimpleKey []`     | String    | Кэш списка услуг                                  |


# Лабораторная работа №2

## Задание

| Параметр              | Значение                                                |
|-----------------------|---------------------------------------------------------|
| Система               | Firestore                                               |
| Вложенные данные      | Дополнительные характеристики заявки (`BookingDetails`) |
| Связанная коллекция   | Справочник услуг (`services`)                           |
| Индексируемый запрос  | Поиск по статусу и дате создания                        |
| Аналитический запрос  | Количество изменений статусов по временным интервалам   |
| Изменение структуры   | Преобразование строкового статуса во вложенный объект   |

Основные объекты перенесены из PostgreSQL в Firestore. CRUD API и Redis-хранилище сохранены без изменений.

## Что реализовано

| Компонент                  | Описание                                                                                                  |
|----------------------------|-----------------------------------------------------------------------------------------------------------|
| `FirestoreConfig`          | Подключение к Firestore: эмулятор через `FIRESTORE_EMULATOR_HOST` или облако через credentials            |
| Модели                     | `BookingDocument`, `BookingStatusDocument`, `BookingDetails`, `UniversityServiceDocument`, `ServiceUsage` |
| Интерфейсы                 | `BookingStore`, `UniversityServiceStore`                                                                  |
| Firestore-реализации       | `FirestoreBookingStore`, `FirestoreUniversityServiceStore`                                                |
| `BookingMapper`            | Преобразование `BookingDocument` ↔ `BookingDto`                                                           |
| `BookingService`           | Создание и удаление заявок через Firestore-транзакцию с обновлением счётчика услуги                       |
| `BasketService`            | Использует `UniversityServiceStore` вместо JPA                                                            |
| `UniversityCatalogService` | Читает каталог из Firestore, кэширует в Redis                                                             |
| `DataInitializer`          | Заполняет коллекцию `services` при старте (отключён в профиле `migration`)                                |
| `MigrationRunner`          | Перенос данных PostgreSQL → Firestore (профиль `migration`)                                               |
| `SchemaV1ToV2Migrator`     | Преобразование строкового статуса во вложенный объект                                                     |

## Структура документа заявки

**v1** — после переноса из PostgreSQL:
```json
{
  "id": 1,
  "userId": 1,
  "serviceId": 2,
  "room": 305,
  "statusCode": "APPROVED",
  "status": null,
  "schemaVersion": 1
}
```

**v2** — после миграции схемы:
```json
{
  "id": 1,
  "userId": 1,
  "serviceId": 2,
  "room": 305,
  "statusCode": null,
  "status": { "code": "APPROVED", "changedAt": null },
  "details": { "participants": 20, "needsProjector": true, "comment": "..." },
  "schemaVersion": 2
}
```

## Коллекции Firestore

| Коллекция              | Назначение                                               |
|------------------------|----------------------------------------------------------|
| `bookings`             | Основные заявки                                          |
| `services`             | Справочник университетских услуг                         |
| `counters/bookings`    | Атомарный счётчик для автоинкремента ID заявок           |
| `checkouts/{basketId}` | Маркеры идемпотентности для оформления корзины           |
| `bookingChangeDaily`   | Агрегат изменений статусов заявок по дням (аналитика)    |

## Транзакционные обновления

При создании заявки Firestore-транзакция атомарно:
1. Сохраняет документ в коллекции `bookings`
2. Увеличивает `usage.bookingsCount` связанной услуги в `services`

При удалении — уменьшает счётчик (минимум 0). Оформление корзины идемпотентно: повторный вызов `POST /basket/{id}/checkout` возвращает ту же заявку. Конкурентные запросы создают ровно одну заявку.

## Новые эндпоинты

### Поиск заявок с курсорной пагинацией

| Метод    | URL                         | Описание                              |
|----------|-----------------------------|---------------------------------------|
| `GET`    | `/bookings/search`          | Поиск заявок с фильтрами и пагинацией |
| `PATCH`  | `/bookings/{id}/details`    | Обновить детали заявки                |
| `PATCH`  | `/bookings/{id}/assignment` | Переназначить заявку на услугу        |

Параметры `/bookings/search`:

| Параметр         | Тип     | Описание                                      |
|------------------|---------|-----------------------------------------------|
| `status`         | String  | Статус заявки (`NEW`, `APPROVED`, `REJECTED`) |
| `userId`         | Long    | Фильтр по пользователю                        |
| `serviceId`      | Long    | Фильтр по услуге                              |
| `needsProjector` | Boolean | Фильтр по наличию проектора                   |
| `createdFrom`    | Instant | Начало диапазона дат создания                 |
| `createdTo`      | Instant | Конец диапазона дат создания                  |
| `cursor`         | String  | Непрозрачный курсор для следующей страницы    |
| `limit`          | int     | Размер страницы (1–100, по умолчанию 20)      |
| `direction`      | String  | `ASC` или `DESC` по дате создания             |

Ответ содержит поле `nextCursor` — если оно заполнено, есть следующая страница. Курсор нельзя смешивать с другими фильтрами: при несовместимых параметрах возвращается `400`.

```bash
curl "http://localhost:8080/bookings/search?status=NEW&needsProjector=true&limit=5"
```

### Аналитика

| Метод | URL                                                   | Описание                                |
|-------|-------------------------------------------------------|-----------------------------------------|
| `GET` | `/analytics/booking-changes?from=&to=&interval=day`   | Количество изменений статусов по дням   |

```bash
curl "http://localhost:8080/analytics/booking-changes?from=2026-11-01&to=2026-11-05&interval=day"
```

### Управление услугами

| Метод   | URL              | Описание        |
|---------|------------------|-----------------|
| `PATCH` | `/services/{id}` | Обновить услугу |

```bash
curl -X PATCH http://localhost:8080/services/2 \
  -H "Content-Type: application/json" \
  -d '{"name": "Аудитория 305", "description": "Обновлённое описание", "active": true}'
```

## Индексы и правила Firestore

Составные индексы описаны в `firestore.indexes.json` и покрывают все комбинации фильтров поиска (статус, userId, serviceId, needsProjector, диапазон дат). Правила безопасности описаны в `firestore.rules` — доступ к коллекциям разрешён только через backend-приложение.

## Миграция PostgreSQL -> Firestore

Переносит данные из PostgreSQL в Firestore, затем обновляет схему документов с v1 на v2. Миграция идемпотентна: повторный запуск не перезаписывает уже перенесённые документы.

```bash
# 1. Создайте файл с переменными окружения (если ещё нет)
cp .env.example .env
# Заполните .env своими значениями

# 2. Запустите миграцию
./backend/scripts/migrate.sh
```

Просмотр данных в эмуляторе через curl:
```bash
curl "http://localhost:8079/v1/projects/university-booking-demo/databases/(default)/documents/bookings"
curl "http://localhost:8079/v1/projects/university-booking-demo/databases/(default)/documents/bookings/1"
curl "http://localhost:8079/v1/projects/university-booking-demo/databases/(default)/documents/services"
```

Очистка Firestore:
```bash
curl -X DELETE "http://localhost:8079/emulator/v1/projects/university-booking-demo/databases/(default)/documents"
```


# Лабораторная работа №3

## Задание

| Параметр                  | Значение                                              |
|---------------------------|-------------------------------------------------------|
| Система хранения          | ScyllaDB (wide-column)                                |
| Хранимые события          | События выполнения бизнес-операций                    |
| Обязательный запрос       | События с заданным результатом за период              |
| Размер временного бакета  | Один день (UTC)                                       |
| Сценарий согласованности  | Запись с ONE, чтение с QUORUM                         |
| Эксперимент с отказом     | Остановка одного узла-реплики                         |
| Исследуемый механизм      | Образование и удаление tombstones                     |

## Что реализовано

| Компонент                | Описание                                                                                 |
|--------------------------|------------------------------------------------------------------------------------------|
| `ScyllaConfig`           | Подключение к кластеру, создание keyspace и таблиц при старте (`CREATE IF NOT EXISTS`)   |
| `EventRecord`            | Модель события: `eventId`, `occurredAt`, `operation`, `result`, `bookingId`, `errorCode` |
| `EventJournalRepository` | Prepared statements, запись `ONE`, чтение `QUORUM`, retry ×3 при сбое записи             |
| `EventJournalService`    | Async-запись через виртуальный поток; пагинация по дням от новых к старым                |
| `EventCursor`            | Версионированный Base64-курсор: `"1\|day_utc\|hex_paging_state"`                         |
| `EventController`        | Три REST-эндпоинта для чтения журнала событий                                            |
| `BookingService`         | Записывает `CREATE_BOOKING` и `UPDATE_STATUS` после подтверждения Firestore              |
| `BasketService`          | Записывает `CHECKOUT`; идемпотентный повтор оформления события не создаёт                |
| `scylla-schema.cql`      | CQL-схема с обоснованием ключей (документация и ручное выполнение)                       |
| `docker-compose.yml`     | Кластер из 3 узлов ScyllaDB с healthcheck через `cqlsh`                                  |

## Таблицы ScyllaDB

Три денормализованные таблицы под три запроса.

| Запрос                                | Таблица                   | Partition key           |
|---------------------------------------|---------------------------|-------------------------|
| По результату операции (обязательный) | `events_by_result_day`    | `(result, day_utc)`     |
| По типу операции                      | `events_by_operation_day` | `(operation, day_utc)`  |
| По заявке                             | `events_by_booking_day`   | `(booking_id, day_utc)` |

Во всех таблицах clustering columns: `occurred_at DESC, event_id DESC`.

**Обоснование ключей:**
- Partition key объединяет фильтр запроса со суточным бакетом UTC — данные за один день и одно значение фильтра попадают в одну партицию и читаются без `ALLOW FILTERING`.
- Суточный бакет ограничивает размер партиции: максимум 86 400 событий в сутки на значение фильтра.
- `occurred_at DESC` — порядок от новых к старым, нужный для пагинации.
- `event_id DESC` — детерминированный порядок при совпадении метки времени.

## Поля события

| Поле          | Тип       | Описание                                            |
|---------------|-----------|-----------------------------------------------------|
| `event_id`    | UUID      | Уникальный идентификатор (v4)                       |
| `occurred_at` | timestamp | Момент события (UTC)                                |
| `operation`   | text      | `CREATE_BOOKING`, `UPDATE_STATUS`, `CHECKOUT`       |
| `result`      | text      | `SUCCESS`, `REJECTED`                               |
| `booking_id`  | bigint    | ID заявки (null для некоторых отклонённых операций) |
| `error_code`  | text      | Класс исключения для `REJECTED`-событий             |

Успешная смена статуса заявки на `REJECTED` имеет `result = SUCCESS`: результатом события является успех операции смены статуса, а не финальный статус заявки.

## Согласованность

| Операция          | Уровень   | Смысл                                                         |
|-------------------|-----------|---------------------------------------------------------------|
| Запись в журнал   | `ONE`     | Достаточно подтверждения одной реплики — минимальная задержка |
| Чтение из журнала | `QUORUM`  | Нужны 2 из 3 реплик — гарантирует видимость последней записи  |

При RF=3 и QUORUM чтение всегда успешно, если доступны хотя бы 2 узла. При RF=3 и QUORUM=2 пересечение реплик записи и чтения всегда есть (1 + 2 > 3), поэтому QUORUM-чтение гарантированно увидит запись с уровнем ONE.

## API журнала событий

Все эндпоинты реализованы за счёт денормализованных таблиц.

| Метод   | URL                    | Описание                                                      |
|---------|------------------------|---------------------------------------------------------------|
| `GET`   | `/events/by-result`    | Обязательный запрос: события с заданным результатом за период |
| `GET`   | `/events/by-operation` | События по типу операции за период                            |
| `GET`   | `/events/by-booking`   | События конкретной заявки за период                           |

**Общие параметры:**

| Параметр   | Тип                | Описание                                  |
|------------|--------------------|-------------------------------------------|
| `from`     | Instant (ISO 8601) | Начало диапазона (обязательный)           |
| `to`       | Instant (ISO 8601) | Конец диапазона (обязательный)            |
| `limit`    | int                | Размер страницы (1–500, по умолчанию 20)  |
| `cursor`   | String             | Непрозрачный курсор из предыдущего ответа |

**Уникальные параметры:**

| Эндпоинт        | Параметр    | Значения                                      |
|-----------------|-------------|-----------------------------------------------|
| `/by-result`    | `result`    | `SUCCESS`, `REJECTED`                         |
| `/by-operation` | `operation` | `CREATE_BOOKING`, `UPDATE_STATUS`, `CHECKOUT` |
| `/by-booking`   | `bookingId` | long                                          |

Ответ: `{ "items": [...], "nextCursor": "..." }`. Если `nextCursor == null` — страниц больше нет.

```bash
# Обязательный запрос: успешные события за день
curl "http://localhost:8080/events/by-result?result=SUCCESS&from=2026-11-17T00:00:00Z&to=2026-11-17T23:59:59Z&limit=20"

# Следующая страница
curl "http://localhost:8080/events/by-result?result=SUCCESS&from=2026-11-17T00:00:00Z&to=2026-11-17T23:59:59Z&limit=20&cursor=<nextCursor>"

# События CREATE_BOOKING за период
curl "http://localhost:8080/events/by-operation?operation=CREATE_BOOKING&from=2026-11-17T00:00:00Z&to=2026-11-17T23:59:59Z"

# Все события заявки с id=1
curl "http://localhost:8080/events/by-booking?bookingId=1&from=2026-11-01T00:00:00Z&to=2026-11-30T23:59:59Z"
```

## Пагинация

Страницы обходят дни от `to` до `from` (от новых к старым). Курсор кодирует: (день UTC, paging state внутри дня). При исчерпании дня автоматически переходит к предыдущему. Переход через полночь UTC, пустые дни и события с одинаковым временем обрабатываются корректно.

## Кластер ScyllaDB

Три узла в одном датацентре `datacenter1`, keyspace `university_events` с `NetworkTopologyStrategy RF=3`.

```
docker exec scylla1 nodetool status
# Datacenter: datacenter1
# =======================
# Status=Up/Down
# |/ State=Normal/Leaving/Joining/Moving
# --  Address      Tokens  Rack
# UN  172.x.x.1    256     rack1   <- scylla1
# UN  172.x.x.2    256     rack1   <- scylla2
# UN  172.x.x.3    256     rack1   <- scylla3
```

| Порт хоста   | Контейнер   | Назначение                                       |
|--------------|-------------|--------------------------------------------------|
| 9042         | scylla1     | CQL-порт seed-узла (контактная точка приложения) |
| 9043         | scylla2     | CQL-порт для прямых экспериментов                |
| 9044         | scylla3     | CQL-порт для прямых экспериментов                |
| 10000        | scylla1     | REST API для nodetool                            |

Приложение подключается к `scylla1:9042`. Драйвер сам обнаруживает остальные узлы через peer discovery.

## Эксперименты

### Эксперимент с отказом узла и согласованностью

Сравнивает доступность и задержку в трёх фазах: все 3 узла работают → scylla3 остановлен → scylla3 возвращён.

```bash
./backend/scripts/scylla-consistency-experiment.sh
# Результаты: results/consistency/results.csv
```

При RF=3 и QUORUM достаточно 2 реплик — чтение и запись продолжают работать при остановке одного узла. Для уровня ONE запись успешна даже при одной доступной реплике, но QUORUM-чтение может вернуть старые данные, пока не выполнится read repair.

### Исследование tombstones

```bash
./backend/scripts/scylla-tombstone-experiment.sh
```

Шаги: вставка 100 строк → flush → удаление 40 строк → flush → `scylla sstable dump-data` (видны маркеры `partition_tombstone`) → ожидание `gc_grace_seconds=60` → compaction → tombstones физически удалены, строки по-прежнему не читаются.

Tombstones необходимы при RF>1: узлы, которые не получили DELETE, при восстановлении должны узнать, что строка удалена — иначе они вернут её обратно (read repair). `gc_grace_seconds` — окно, в течение которого tombstone гарантированно распространится на все реплики до физического удаления.

### Нагрузочное тестирование

```bash
./backend/scripts/scylla-load-test.sh
# Результаты: results/loadtest/summary.csv
```

Прогрев 10 с, затем замеры по 30 с при параллелизме 1, 5, 10, 20, 50. Фиксирует throughput (rps), среднюю задержку и p95 отдельно для записи (POST `/bookings`) и чтения (`GET /events/by-result`).

# Быстрый старт

## Требования

- [Docker](https://docs.docker.com/get-docker/)

## Запуск с Firestore Emulator 

```bash
# 1. Клонируйте репозиторий
git clone https://github.com/LunarSonic/university-booking.git
cd university-booking

# 2. Скопируйте файл переменных окружения
cp .env.example .env

# 3. Запустите все контейнеры
docker compose up -d --build
```

При первом запуске `DataInitializer` автоматически заполняет коллекцию `services` начальными данными. Кластер ScyllaDB (3 узла) инициализируется ~3–5 минут — приложение стартует только после того, как все три узла пройдут healthcheck. Схема keyspace и таблиц создаётся автоматически при первом запуске приложения.

### Сервисы

| Сервис                   | URL                      |
|:-------------------------|:-------------------------|
| Приложение (API)         | http://localhost:8080    |
| Frontend                 | http://localhost:3000    |
| Firestore Emulator (API) | http://localhost:8079    |
| PostgreSQL               | localhost:5434           |
| Redis                    | localhost:6379           |
| ScyllaDB node 1 (CQL)    | localhost:9042           |
| ScyllaDB node 2 (CQL)    | localhost:9043           |
| ScyllaDB node 3 (CQL)    | localhost:9044           |

### Остановка

```bash
docker compose down       # данные сохраняются
docker compose down -v    # с удалением volumes
```

## Запуск с Firestore

### 1. Создайте Firebase-проект

1. Откройте [console.firebase.google.com](https://console.firebase.google.com)
2. Нажмите **Add project** → введите имя (например `university-booking`) → подтвердите
3. В левом меню: **Build → Firestore Database → Create database**
4. Выберите регион и режим **Native mode** (не Datastore)

### 2. Создайте сервисный аккаунт

1. В Firebase Console: **Project settings (шестерёнка) → Service accounts**
2. Нажмите **Generate new private key** → сохраните файл `firebase-key.json`
3. Создайте папку `secrets/` в корне репозитория и положите туда ключ:
```bash
mkdir -p secrets
cp ~/Downloads/firebase-key.json secrets/firebase-key.json
```

### 3. Задеплойте индексы и правила

Установите Firebase CLI и авторизуйтесь:
```bash
npm install -g firebase-tools
firebase login
```

Выберите свой проект и задеплойте:
```bash
firebase use --add   # выберите ваш проект из списка
firebase deploy --only firestore
```

Это создаст составные индексы (`firestore.indexes.json`) и загрузит правила безопасности (`firestore.rules`).

### 4. Настройте .env

Скопируйте `.env.example` -> `.env` и заполните:
```bash
cp .env.example .env
```

Укажите значения:
```env
FIREBASE_PROJECT_ID=ваш-project-id   # из Firebase Console
POSTGRES_PASSWORD=ваш-пароль
SPRING_DATASOURCE_PASSWORD=ваш-пароль
```

### 5. Запустите приложение

```bash
docker compose -f docker-compose.cloud.yml up -d --build
```

| Сервис     | URL                   |
|:-----------|:----------------------|
| Приложение | http://localhost:8080 |
| Frontend   | http://localhost:3000 |
| PostgreSQL | localhost:5434        |
| Redis      | localhost:6379        |

