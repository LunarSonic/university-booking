# University Booking

![Java](https://img.shields.io/badge/Java-22-orange?logo=openjdk)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.1-6DB33F?logo=springboot&logoColor=white)
![Redis](https://img.shields.io/badge/Redis-DC382D?logo=redis&logoColor=white)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-4169E1?logo=postgresql&logoColor=white)
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

## Быстрый старт

### Требования

- [Docker](https://docs.docker.com/get-docker/)

### Запуск

```bash
# 1. Клонируйте репозиторий
git clone https://github.com/LunarSonic/university-booking.git
cd university-booking

# 2. Запустите 3 контейнера
docker compose up -d --build
```

При первом запуске `DataInitializer` автоматически заполняет таблицу `services` начальными данными.

### Сервисы

| Сервис     | URL                   |
|:-----------|:----------------------|
| Приложение | http://localhost:8080 |
| PostgreSQL | localhost:5434        |
| Redis      | localhost:6379        |

### Остановка

```bash
docker compose down       # данные сохраняются
docker compose down -v    # с удалением volume
```

## Архитектура хранения

| Данные                            | Хранилище  | Назначение                                    |
|-----------------------------------|------------|-----------------------------------------------|
| Заявки (BookingRequest)           | PostgreSQL | Постоянное хранение с ACID-гарантиями         |
| Каталог услуг (UniversityService) | PostgreSQL | Справочник услуг, инициализируется при старте |
| Временная корзина (BasketItem)    | Redis      | Хранение с автоматическим TTL                 |
| Кэш каталога услуг                | Redis      | Кэширование данных на 30 минут                |
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

| Метод | URL         | Описание                            |
|-------|-------------|-------------------------------------|
| `GET` | `/services` | Список услуг (кэшируется на 30 мин) |

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

Основные объекты перенесены из PostgreSQL в Firestore. CRUD API и Redis-хранилище сохранены без изменений.

## Что реализовано 

| Компонент                  | Описание                                                                                                  |
|----------------------------|-----------------------------------------------------------------------------------------------------------|
| `FirestoreConfig`          | Подключение к Firestore: эмулятор через `FIRESTORE_EMULATOR_HOST` или облако через credentials            |
| Модели                     | `BookingDocument`, `BookingStatusDocument`, `BookingDetails`, `UniversityServiceDocument`, `ServiceUsage` |
| Интерфейсы                 | `BookingStore`, `UniversityServiceStore`                                                                  |
| Firestore-реализации       | `FirestoreBookingStore`, `FirestoreUniversityServiceStore`, `FirestoreIdGenerator`                        |
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

## Транзакционные обновления

При создании заявки Firestore-транзакция атомарно:
1. Сохраняет документ в коллекции `bookings`
2. Увеличивает `usage.bookingsCount` связанной услуги в `services`

При удалении — уменьшает счётчик (минимум 0).

## Запуск с Firestore Emulator

```bash
docker compose up -d --build
```

Добавлены сервисы:

| Сервис             | URL                   |
|:-------------------|:----------------------|
| Firestore Emulator | http://localhost:8079 |

## Запуск с реальным Firestore

```bash
# Положите ключ сервисного аккаунта в secrets/firebase-key.json
docker compose -f docker-compose.cloud.yml up -d --build
```

## Миграция

Переносит данные из PostgreSQL в Firestore, затем обновляет схему документов с v1 на v2. Миграция идемпотентна: повторный запуск не перезаписывает уже перенесённые документы.

```bash
# 1. Создайте файл с переменными окружения (если ещё нет)
cp .env.example .env
# Заполните .env своими значениями

# 2. Запустите миграцию
./backend/scripts/migrate.sh
```

Просмотр данных в эмуляторе:
```bash
./backend/scripts/firestore.sh bookings      # вся коллекция
./backend/scripts/firestore.sh bookings 1    # конкретный документ
./backend/scripts/firestore.sh services
```

Очистка Firestore:
```bash
curl -X DELETE "http://localhost:8079/emulator/v1/projects/<project-id>/databases/(default)/documents"
```

