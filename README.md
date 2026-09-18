# Minilink

A URL shortener built with Spring Boot, Angular and Redis. Create short links with optional custom aliases and expiry dates, then track clicks with per-hour, per-day and per-referrer analytics.

## Features

- Short codes generated with collision retry, or a user-supplied custom alias
- Optional expiry date, with a scheduled job that purges expired links
- Click tracking: total count, recent events, breakdown by referrer, hour and day
- Per-IP rate limiting on link creation (per-minute and per-hour windows)
- Redis as the single datastore, with cached lookups for redirects

## Stack

| Layer    | Tech                                   |
|----------|----------------------------------------|
| Backend  | Java 21, Spring Boot 4, Spring Data Redis |
| Frontend | Angular 22, served by nginx            |
| Storage  | Redis 7                                |
| Runtime  | Docker Compose                         |

## Run it

```bash
docker compose up --build
```

| Service  | URL                     |
|----------|-------------------------|
| Frontend | http://localhost:8082   |
| Backend  | http://localhost:8080   |
| Redis    | localhost:6379          |

## API

Base path: `/api`

| Method | Path                     | Description                          |
|--------|--------------------------|--------------------------------------|
| POST   | `/shorten`               | Create a short link                  |
| GET    | `/{shortCode}`           | Redirect to the original URL         |
| GET    | `/stats/{shortCode}`     | Click count, dates and status        |
| GET    | `/analytics/{shortCode}` | Detailed click breakdown             |
| DELETE | `/{shortCode}`           | Delete a short link                  |
| GET    | `/health`                | Health check                         |

Example:

```bash
curl -X POST http://localhost:8080/api/shorten \
  -H 'Content-Type: application/json' \
  -d '{"originalURL": "https://example.com", "customAlias": "demo", "expiresAt": "2026-12-31T23:59:00"}'
```

```json
{
  "shortURL": "http://localhost:8080/api/demo",
  "shortCode": "demo",
  "originalURL": "https://example.com",
  "createdAt": "2026-09-18T15:24:32",
  "expiresAt": "2026-12-31T23:59:00"
}
```

## Configuration

Tunable in `backend/src/main/resources/application.yaml` under the `minilink` key, or via environment variables in `docker-compose.yaml`:

| Key                              | Default | Purpose                                 |
|----------------------------------|---------|-----------------------------------------|
| `base-url`                       | `http://localhost:8080` | Prefix used in generated short URLs |
| `short-code.length`              | 6       | Length of generated codes               |
| `rate-limit.requests-per-minute` | 2       | Link creations allowed per IP per minute |
| `rate-limit.requests-per-hour`   | 10      | Link creations allowed per IP per hour  |
| `cache.ttl-minutes`              | 30      | Redirect cache lifetime                 |
| `cleanup.interval-minutes`       | 1       | How often expired links are purged      |

## Local development

```bash
docker run -d -p 6379:6379 redis:7-alpine   # Redis
cd backend && ./mvnw spring-boot:run         # API on :8080
cd frontend && npm install && npm start      # UI on :4200
```
