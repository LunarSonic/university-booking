# University Booking

![Java](https://img.shields.io/badge/Java-22-orange?logo=openjdk)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.1-6DB33F?logo=springboot&logoColor=white)
![Redis](https://img.shields.io/badge/Redis-DC382D?logo=redis&logoColor=white)
![Gradle](https://img.shields.io/badge/Gradle-02303A?logo=gradle&logoColor=white)
![Docker](https://img.shields.io/badge/Docker-2496ED?logo=docker&logoColor=white)

REST API для управления заявками на бронирование университетских услуг и ресурсов.

## Задание

| Параметр           | Значение                    |
|--------------------|-----------------------------|
| Основной объект    | Заявка                      |
| Роль               | Администратор               |
| Сценарий           | Создание и обработка заявки |
| Хранилище          | Redis                       |
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

# 2. Запустите 2 контейнера
docker compose up -d --build
```

### Сервисы

| Сервис     | URL                   |
|:-----------|:----------------------|
| Приложение | http://localhost:8080 |
| Redis      | localhost:6379        |

### Остановка

```bash
docker compose down       # данные сохраняются
docker compose down -v    # с удалением volume
```

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
  -d '{"userId": 1, "room": 305, "bookingDate": "2026-12-01T10:00:00"}'
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
  -d '{"userId": 1, "room": 210, "bookingDate": "2026-12-01T14:00:00"}'
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

Первый вызов занимает около 3 секунд (имитация обращения к медленному источнику). Последующие отдаются мгновенно из кэша Redis.

### Rate Limiter

На все эндпоинты действует ограничение: 10 запросов в минуту с одного IP. При превышении возвращается `429 Too Many Requests`.

## Ключи в Redis

| Вид ключа                        | Тип Redis | Назначение                                        |
|----------------------------------|-----------|---------------------------------------------------|
| `BasketItem:sequence`            | String    | Счётчик для автоинкремента ID корзины             |
| `BookingRequest:sequence`        | String    | Счётчик для автоинкремента ID заявок              |
| `BookingRequest:{id}:idx`        | Set       | Служебный: список индексов заявки (для очистки)   |
| `BookingRequest`                 | Set       | Множество всех ID заявок                          |
| `BookingRequest:{id}`            | Hash      | Заявка на бронирование (все поля объекта)         |
| `BasketItem`                     | Set       | Множество всех ID корзины                         |
| `BookingRequest:status:{STATUS}` | Set       | Вторичный индекс: ID заявок по статусу            |
| `BasketItem:{id}:idx`            | Set       | Служебный: список индексов элемента (для очистки) |
| `BasketItem:userId:{userId}`     | Set       | Вторичный индекс: ID элементов корзины по userId  |
| `BasketItem:{id}`                | Hash      | Элемент временной корзины                         |
| `rate_limiter:{ip}`              | String    | Счётчик запросов для rate limiter                 |
| `services::SimpleKey []`         | String    | Кэш списка услуг                                  |

