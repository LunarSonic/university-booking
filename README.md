# University Booking

![Java](https://img.shields.io/badge/Java-22-orange?logo=openjdk)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.1-6DB33F?logo=springboot&logoColor=white)
![Redis](https://img.shields.io/badge/Redis-DC382D?logo=redis&logoColor=white)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-4169E1?logo=postgresql&logoColor=white)
![Gradle](https://img.shields.io/badge/Gradle-02303A?logo=gradle&logoColor=white)
![Docker](https://img.shields.io/badge/Docker-2496ED?logo=docker&logoColor=white)

REST API для управления заявками на бронирование университетских услуг и ресурсов.

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

На все эндпоинты действует ограничение: 10 запросов в минуту с одного IP. При превышении возвращается `429 Too Many Requests`.

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
