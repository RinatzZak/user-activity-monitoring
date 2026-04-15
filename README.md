# User Activity Monitoring System

Система мониторинга активности пользователей с использованием **Kafka Streams** для анализа поведения в реальном времени. Система отслеживает действия пользователей, агрегирует статистику по различным временным окнам и автоматически блокирует подозрительную активность (спам).

---

## 📋 Содержание

- [Технологический стек](#технологический-стек)
- [Архитектура](#архитектура)
- [Структура проекта](#структура-проекта)
- [API Endpoints](#api-endpoints)
- [Запуск проекта](#запуск-проекта)
- [Тестирование проекта](#тестирование-проекта)
- [Лицензия](#лицензия)

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

## Запуск проекта
1. Клонирование репозитория

```bash
git clone https://github.com/your-repo/user-activity-monitoring.git
cd user-activity-monitoring
```

2. Запуск инфраструктуры
```bash
docker-compose up -d
```

3. Запустить Spring Boot приложение в классе UserActivityMonitoringApplication.java


4. Проверка работоспособности
```bash
curl -X POST http://localhost:8080/api/users \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Тест",
    "email": "testov@test.com",
    "action": "TEST"
  }'
``` 

### Добавление настроек SSL для Spring Boot приложение:

```bash
cd .\docker\ssl

# 1. Удаляем старые файлы
Remove-Item *.jks, *.crt, *.key, *.pem, *.cer, *.csr, *.srl, *.ext -Force -ErrorAction SilentlyContinue

# 2. Создаем CA сертификат
openssl req -x509 -newkey rsa:4096 -keyout ca-key.pem -out ca-cert.pem -days 365 -subj "/CN=Kafka-CA" -passout pass:changeit -addext "basicConstraints=critical,CA:TRUE" -addext "keyUsage=critical,keyCertSign,cRLSign"

# 3. Создаем keystore
keytool -genkey -alias kafka -keyalg RSA -keysize 4096 -keystore kafka.jks -storepass changeit -keypass changeit -dname "CN=kafka" -validity 365

# 4. Создаем CSR
keytool -certreq -alias kafka -keystore kafka.jks -file kafka.csr -storepass changeit

# 5. Создаем файл с SAN расширениями
@"
subjectAltName=DNS:kafka,DNS:localhost,IP:127.0.0.1
extendedKeyUsage=serverAuth,clientAuth
"@ | Out-File -FilePath san.ext -Encoding ascii

# 6. Подписываем сертификат CA
openssl x509 -req -CA ca-cert.pem -CAkey ca-key.pem -in kafka.csr -out kafka-cert.pem -days 365 -CAcreateserial -passin pass:changeit -extfile san.ext

# 7. Импортируем CA в keystore
keytool -import -alias caroot -file ca-cert.pem -keystore kafka.jks -storepass changeit -noprompt

# 8. Импортируем подписанный сертификат
keytool -import -alias kafka -file kafka-cert.pem -keystore kafka.jks -storepass changeit -noprompt

# 9. Создаем truststore с CA
keytool -import -alias caroot -file ca-cert.pem -keystore truststore.jks -storepass changeit -noprompt

# 10. Создаем ssl_creds
"changeit" | Out-File -FilePath ssl_creds -Encoding ascii -NoNewline

# 11. Проверяем
keytool -list -v -keystore truststore.jks -storepass changeit | Select-String -Pattern "IsCA"
keytool -list -keystore kafka.jks -storepass changeit
```


```
# application.yml
spring.kafka.properties.security.protocol=SSL
spring.kafka.ssl.trust-store-location=classpath:ssl/kafka.client.truststore.jks
spring.kafka.ssl.trust-store-password=changeit
```

## Тестирование проекта

###  На данный момент action может быть только из списка.
```
    REGISTER("Регистрация")
    UPDATE("Обновление")
    SCROLL("Пролистывание")
    DELETE("Удаление")
    UNKNOWN("Неизвестное действие")
```

Проверьте подключение к Debezium. В приложении регистрации настроена при запуске приложения, поэтому нужно только проверить.
```bash
curl http://localhost:8083/connectors/postgres-connector/status
```

1. Сгенерировать пользователя
```bash
curl -X POST http://localhost:8080/api/users \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Блинчик Пирожков",
    "email": "coolpancake@test.com",
    "action": "REGISTER"
  }'
```
Должен вернуться ответ
```
{
  "id": 1, //для примера 1, после проверки у вас может быть другой идентификатор
  "name": "Блинчик Пирожков",
  "email": "coolpancake@test.com",
  "action": "REGISTER",
  "isBlocked": false,
  "createdAt": "2026-03-26T12:00:00"
}
```

2. Получить список пользователей.
```bash
curl http://localhost:8080/api/users
```

3. Обновить пользователя.
```bash
curl -X PUT http://localhost:8080/api/users/1 \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Блинчик Пирожков",
    "email": "coolpancake@test.com",
    "action": "UPDATE_PROFILE"
  }'
```

4. Проверить, что сообщение появилось в топике Kafka с изменениями пользователя.
```bash
docker exec -it kafka kafka-console-consumer \
  --bootstrap-server localhost:9093 \
  --topic postgres-server.public.users \
  --from-beginning \
  --max-messages 1
```
Так же для удобства все сообщения залогированы в приложении, вы их можете увидеть в консоли.

5. Проверка агрегации в Tumbling Window за 2 минуты с задержкой 30 секунд.
``` bash
docker exec -it kafka kafka-console-consumer \
  --bootstrap-server localhost:9093 \
  --topic user-activity-stats-tumbling \
  --from-beginning
```

6. Проверка скользящего среднего за 2 минуты.
``` bash
docker exec -it kafka kafka-console-consumer \
  --bootstrap-server localhost:9093 \
  --topic user-activity-stats-hopping \
  --from-beginning
```

7. Проверка блокировки пользователя.
   Создание спам-активности (более 5 действий)
```bash
# Создать пользователя
curl -X POST http://localhost:8080/api/users \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Спаммер",
    "email": "userCountActivity@test.com",
    "action": "LOGIN"
  }'

# Выполнить 5 быстрых обновлений (спам) ID который вернется в ответе создания пользователя
for i in {1..5}; do
  curl -X PUT http://localhost:8080/api/users/3 \
    -H "Content-Type: application/json" \
    -d "{
      \"name\": \"Спаммер\",
      \"email\": \"userCountActivity@test.com\",
      \"action\": \"SPAM_$i\"
    }"
done
```
Проверяем, что заблокирован:

``` bash
docker exec -it kafka kafka-console-consumer \
  --bootstrap-server localhost:9093 \
  --topic user-activity-stats-session \
  --from-beginning
```

```bash
curl -X PUT http://localhost:8080/api/users/3 \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Спаммер",
    "email": "userCountActivity@test.com",
    "action": "TRY_UPDATE"
  }'
```
Должен вернуться ответ в виде JSON:
```
{
  "status": 403,
  "error": "Пользователь заблокирован",
  "message": "Пользователь с id 3 заблокирован до 2026-03-26T12:05:00",
  "path": "/api/users/3",
  "timestamp": "2024-03-26T12:03:00",
  "userId": 3,
  "unblockTime": "2024-03-26T12:05:00"
}
```
Блокировка длится 2 минуты. В message Вы можете увидеть до какого времени назначена блокировка.
Она снимается автоматически шедулером, поэтому спустя 2 минуты Вы снова можете проверить апдейт пользователя.

8. Проверка работы ретраев и отправки сообщения в DLQ.
```bash
docker exec -it kafka kafka-console-producer --bootstrap-server localhost:9093 --topic user-activity-stats-session --property "parse.key=true" --property "key.separator=:"
```
Далее ввести 1:{"id""count":5} и отправить.

## 📜 Лицензия
© 2026. Данный проект распространяется под лицензией [MIT License](https://choosealicense.com/licenses/mit/)