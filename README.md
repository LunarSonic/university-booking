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
git clone <url>
cd university-booking

# 2. Запустите 2 контейнера
docker compose up -d --build
```

### Сервисы

| Сервис      | URL                    |
|:------------|:-----------------------|
| Приложение  | http://localhost:8080  |
| Redis       | localhost:6379         |

### Остановка

```bash
docker compose down       # данные сохраняются
docker compose down -v    # с удалением volume
```

## API

## Структура данных в Redis

## Персистентность Redis

## Исследование поведения системы: потеря временных ключей

## Выводы
