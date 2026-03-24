# User Activity Monitoring System

Система мониторинга активности пользователей с использованием **Kafka Streams** для анализа поведения в реальном времени. Система отслеживает действия пользователей, агрегирует статистику по различным временным окнам и автоматически блокирует подозрительную активность (спам).

---

## 📋 Содержание

- [Технологический стек](#технологический-стек)
- [Архитектура](#архитектура)
- [Структура проекта](#структура-проекта)
- [API Endpoints](#api-endpoints)
- [Запуск проекта](#запуск-проекта)
- [Мониторинг и логирование](#мониторинг-и-логирование)
- [Troubleshooting](#troubleshooting)

---

## Технологический стек

| Технология | Версия | Назначение |
|------------|--------|------------|
| Java | 17+ | Язык программирования |
| Spring Boot | 3.2.x | Основной фреймворк |
| Spring Kafka | 3.1.x | Интеграция с Kafka |
| Kafka Streams | 3.5.0 | Обработка потоковых данных |
| Apache Kafka | 7.4.0 | Брокер сообщений |
| Debezium | 2.3.0 | Change Data Capture (CDC) из PostgreSQL |
| PostgreSQL | 16 | Реляционная база данных |
| Liquibase | 4.24.x | Миграции базы данных |
| Docker / Docker Compose | - | Контейнеризация инфраструктуры |
| Lombok | - | Упрощение кода |
| Jackson | 2.15.x | JSON сериализация/десериализация |
| Maven | 3.8+ | Сборка проекта |

---
## Архитектура
```
┌─────────────────┐ ┌──────────────────┐ ┌─────────────────┐
│ PostgreSQL │────▶│ Kafka Connect │────▶│ Kafka │
│ (источник) │ │ (Debezium) │ │ (топики) │
└─────────────────┘ └──────────────────┘ └────────┬────────┘
│
▼
┌─────────────────┐ ┌──────────────────┐ ┌─────────────────┐
│ REST API │◀────│ Приложение │◀────│ Kafka Streams │
│ (Spring MVC) │ │ (обработка) │ │ (аналитика) │
└─────────────────┘ └──────────────────┘ └─────────────────┘
```

### Поток данных

1. **PostgreSQL** → изменения таблицы `users` захватываются Debezium
2. **Kafka** → топик `postgres-server.public.users` получает все изменения
3. **Kafka Streams** → обрабатывает поток и выполняет агрегации по трем типам окон
4. **Выходные топики** → результаты агрегации отправляются в отдельные топики
5. **REST API** → позволяет управлять пользователями и просматривать статистику

### Выходные топики

| Топик | Тип окна | Описание |
|-------|----------|----------|
| `user-activity-stats-tumbling` | Tumbling | Фиксированные окна по 2 минуты |
| `user-activity-stats-hopping` | Hopping | Скользящие окна (2 минуты, шаг 30 сек) |
| `user-activity-stats-session` | Session | Сессионные окна (10 секунд бездействия) |

### Правила блокировки

| Тип окна | Порог | Действие |
|----------|-------|----------|
| Tumbling Window | 10+ действий | Блокировка пользователя на 2 минуты |
| Hopping Window | 8+ действий | Отправка предупреждения (без блокировки) |
| Session Window | 15+ действий за сессию | Логирование подозрительной активности |

---

## Структура проекта
```
src/main/java/com/user/activity/monitoring/
│
├── UserActivityMonitoringApplication.java # Точка входа (@SpringBootApplication)
│
├── controller/
│ └── UserController.java # REST API для пользователей и статистики
│
├── service/
│ ├── UserService.java # Бизнес-логика (CRUD, блокировка)
│ ├── ActivityStorageService.java # Хранение активности (in-memory)
│ ├── UserActivityStreams.java # Kafka Streams топология
│ ├── UserActivityListener.java # Консьюмеры агрегаций (блокировка)
│ ├── DebeziumListener.java # Создание Debezium коннектора
│ └── UserUnblockScheduler.java # Шедулер разблокировки
│
├── repository/
│ └── UserRepository.java # JPA репозиторий + нативные запросы
│
├── entity/
│ ├── User.java # Сущность пользователя
│ ├── UserActivity.java # DTO для активности
│ └── Spammer.java # DTO для агрегаций
│
├── exception/
│ ├── UserBlockedException.java # Исключение при блокировке
│ └── UserNotFoundException.java # Исключение "пользователь не найден"
│
└── handler/
└── GlobalExceptionHandler.java # Глобальный обработчик ошибок

src/main/resources/
│
├── application.yml # Конфигурация приложения
│
└── db/
└── changelog/
├── db.changelog-master.xml # Главный файл миграций
└── changes/
└── 001-create-users-table.xml # Создание таблицы users
```


---

## API Endpoints

### Пользователи

| Метод | URL | Описание |
|-------|-----|----------|
| POST | `/api/users` | Создание нового пользователя |
| PUT | `/api/users/{id}` | Обновление пользователя (проверка блокировки) |
| DELETE | `/api/users/{id}` | Удаление пользователя |
| GET | `/api/users` | Получение всех пользователей |
| GET | `/api/users/{id}` | Получение пользователя по ID |

### Статистика активности

| Метод | URL | Описание |
|-------|-----|----------|
| GET | `/api/stats/actions` | Получение последних активностей (параметры: `action`, `limit`) |
| GET | `/api/stats/users/{userId}/activities` | Активности конкретного пользователя (параметры: `limit`) |
| DELETE | `/api/stats/clear` | Очистка всех активностей из in-memory хранилища |

### Примеры запросов

#### Создание пользователя
```bash
curl -X POST http://localhost:8080/api/users \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Tecтовый Тест",
    "email": "test@test.com",
    "action": "TEST"
  }'
```
#### Проверка топиков
``` bash
docker exec -it kafka kafka-console-consumer \
  --bootstrap-server localhost:9093 \
  --topic postgres-server.public.users \
  --from-beginning
```
``` bash
docker exec -it kafka kafka-console-consumer \
  --bootstrap-server localhost:9093 \
  --topic user-activity-stats-tumbling \
  --from-beginning
```
``` bash
docker exec -it kafka kafka-console-consumer \
  --bootstrap-server localhost:9093 \
  --topic user-activity-stats-hopping \
  --from-beginning
```
``` bash
docker exec -it kafka kafka-console-consumer \
  --bootstrap-server localhost:9093 \
  --topic user-activity-stats-session \
  --from-beginning
```
