# University Booking

![Java](https://img.shields.io/badge/Java-22-orange?logo=openjdk)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.1-6DB33F?logo=springboot&logoColor=white)
![Redis](https://img.shields.io/badge/Redis-DC382D?logo=redis&logoColor=white)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-4169E1?logo=postgresql&logoColor=white)
![Firestore](https://img.shields.io/badge/Firestore-FFCA28?logo=firebase&logoColor=black)
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
./backend/scripts/firestore.sh bookings      # вся коллекция
./backend/scripts/firestore.sh bookings 1    # конкретный документ
./backend/scripts/firestore.sh services
```

Очистка Firestore:
```bash
curl -X DELETE "http://localhost:8079/emulator/v1/projects/university-booking-demo/databases/(default)/documents"
```


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

При первом запуске `DataInitializer` автоматически заполняет коллекцию `services` начальными данными.

### Сервисы

| Сервис                   | URL                      |
|:-------------------------|:-------------------------|
| Приложение (API)         | http://localhost:8080    |
| Frontend                 | http://localhost:3000    |
| Firestore Emulator (API) | http://localhost:8079    |
| PostgreSQL               | localhost:5434           |
| Redis                    | localhost:6379           |

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

