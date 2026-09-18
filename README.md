# Minilink

A URL shortener built with Spring Boot, Angular and Redis. Create short links with optional custom aliases and expiry dates, then track clicks with per-hour, per-day and per-referrer analytics.

## Features

- Short codes generated with collision retry, or a user-supplied custom alias
- Optional expiry date, with a scheduled job that purges expired links
- Click tracking: total count, recent events, breakdown by referrer, hour and day
- Per-IP rate limiting on link creation (per-minute and per-hour windows)
- Redis-backed redirect cache and rate limit counters

## Stack

| Layer    | Tech                                   |
|----------|----------------------------------------|
| Backend  | Java 21, Spring Boot 4, Spring Data Redis |
| Frontend | Angular 22, served by nginx            |
| Cache    | Redis 7                                |
| Runtime  | Docker Compose                         |

## Run with Docker Compose

Requires Docker Desktop (or Docker Engine with the Compose plugin). No local Java or Node needed.

```bash
git clone <this-repo> && cd minilink
docker compose up --build          # build images and start redis, backend, frontend
```

Add `-d` to run in the background. Useful commands:

```bash
docker compose logs -f backend     # follow backend logs
docker compose ps                  # service status and health
docker compose down                # stop and remove containers
docker compose down -v             # also wipe Redis data
```

| Service  | URL                     |
|----------|-------------------------|
| Frontend | http://localhost:8082   |
| Backend  | http://localhost:8080   |
| Redis    | localhost:6379          |

The frontend's nginx proxies `/api/*` to the backend, so the browser only talks to port 8082. The backend waits for Redis to pass its healthcheck before starting.

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

## How it works

**Storage.** Link records and click events are kept in in-memory concurrent maps inside the backend. This keeps the project simple but means data does not survive a restart. Redis is used for the two things below.

**Caching.** On redirect, the service first checks a plain string key `url:<code>` that maps straight to the destination. A hit skips the record lookup and expiry check. The cache entry is written on link creation and on the first miss, expires after `cache.ttl-minutes` (default 30), and is evicted when the link is deleted or cleaned up.

**Rate limiting.** Link creation is limited per client IP using a fixed-window counter stored in Redis under `ratelimit:<ip>`. Two windows are checked, per-minute and per-hour, and the counter key expires after an hour. Exceeding either returns `429 Too Many Requests`. Defaults are deliberately low (2/min, 10/hour) for demo purposes.

**Cleanup.** A scheduled job runs every `cleanup.interval-minutes` and removes links whose `expiresAt` has passed, along with their cache entries.

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
