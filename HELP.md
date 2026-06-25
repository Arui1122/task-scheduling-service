# Task Scheduling Service — Setup & Usage

## Prerequisites

- Java 21
- Maven (use the bundled `./mvnw`)
- Docker + Docker Compose

## 1. Start infrastructure

```bash
docker-compose up -d
```

This brings up MySQL (3306), Redis (6379), RocketMQ namesrv (9876), broker (10911), and the RocketMQ console (8088).

Verify all containers are healthy:
```bash
docker-compose ps
```

The MySQL container automatically applies `init.sql` on first start, creating the `tasks` table.

## 2. Start the application

```bash
./mvnw spring-boot:run
```

The app listens on `http://localhost:8080`.

Swagger UI: `http://localhost:8080/swagger-ui.html`
OpenAPI JSON: `http://localhost:8080/v3/api-docs`
Health: `http://localhost:8080/actuator/health`
Prometheus metrics: `http://localhost:8080/actuator/prometheus`
RocketMQ console: `http://localhost:8088`

## 3. Run tests

```bash
./mvnw test
```

All tests run in-memory (H2 + in-process fakes for DelayQueue and TaskMessagePublisher). No Docker required for tests.

## 4. API examples

### Create a task (executes in 30 seconds)
```bash
EXEC_AT=$(date -u -v+30S +"%Y-%m-%dT%H:%M:%SZ" 2>/dev/null || date -u -d "+30 seconds" +"%Y-%m-%dT%H:%M:%SZ")
curl -X POST http://localhost:8080/tasks \
  -H "Content-Type: application/json" \
  -d "{
    \"taskId\": \"demo-1\",
    \"executeAt\": \"$EXEC_AT\",
    \"payload\": {
      \"type\": \"email\",
      \"target\": \"hello@example.com\",
      \"message\": \"This is a scheduled task!\"
    }
  }"
```

### Get task by id
```bash
curl http://localhost:8080/tasks/demo-1
```

### List pending tasks (paginated)
```bash
curl "http://localhost:8080/tasks?status=pending&page=0&size=20"
```

### Cancel a task
```bash
curl -X DELETE http://localhost:8080/tasks/demo-1
```

### Watch the message arrive in RocketMQ
Open `http://localhost:8088`, navigate to **Topic → task-schedule-topic → Consume**.

## 5. MySQL credentials

| Field    | Value     |
|----------|-----------|
| Host     | localhost |
| Port     | 3306      |
| Database | taskdb    |
| User     | taskuser  |
| Password | taskpass  |

```bash
mysql -h 127.0.0.1 -P 3306 -u taskuser -ptaskpass taskdb -e "SELECT task_id, status, execute_at, triggered_at FROM tasks ORDER BY created_at DESC LIMIT 10;"
```

## 6. Troubleshooting

- **`./mvnw spring-boot:run` fails at startup with `Producer start failed`** — RocketMQ broker isn't up yet. Wait ~30 s after `docker-compose up -d` and try again.
- **Tests pass but the app fails to find tables at runtime** — `init.sql` only runs on first MySQL container start. To re-apply, `docker-compose down -v && docker-compose up -d`.
- **Port 8080 in use** — change `server.port` in `application.yaml`.
