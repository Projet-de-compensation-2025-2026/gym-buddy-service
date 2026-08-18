# Local compose runtime proof

Recorded 2026-08-18 so the wiki can stop implying `compose.yaml` is untested.

This is a **local laptop stack** proof (`cp .env.example .env` and `docker compose up -d` in this repo). It does **not** claim the OVH VPS runs Postgres. Sign-up / sign-in (#12) was not started.

Host: Linux (`x86_64`). Repo at `6b825061486026618ecd146d9a622de091e4fbf4` (`feature/19-prove-local-compose`). Service artifact remains `0.2.0-SNAPSHOT` (not 0.3.0).

## Commands

```bash
cp .env.example .env
docker compose down
docker compose up -d --build
```

`docker compose up -d --build` exited 0 at **2026-08-18T10:40:39Z**. `minio-init` exited 0 before the API started. Postgres and Redis were healthy; MinIO and the API were running.

## HTTP probes

Exact requests against `127.0.0.1` at **2026-08-18T10:40:55Z**:

```text
GET http://127.0.0.1:8080/api/v1/healthz
HTTP/1.1 200
Content-Type: application/json
Content-Length: 15

{"status":"ok"}
```

```text
GET http://127.0.0.1:8080/api/v1/readyz
HTTP/1.1 200
Content-Type: application/json

{"status":"ok"}
```

`readyz` 200 means Postgres and object storage (`S3_BUCKET=gym-buddy`) were reachable from the API.

## Published ports

`ss -lntp` and `docker ps` showed only `127.0.0.1` binds. No `0.0.0.0` listeners on these ports.

| Service | Image | Published |
| --- | --- | --- |
| api | `gym-buddy-api` (built here) | `127.0.0.1:8080->8080/tcp` |
| postgres | `postgres:18.6` | `127.0.0.1:5432->5432/tcp` |
| redis | `redis:8-alpine` | `127.0.0.1:6379->6379/tcp` |
| minio | `minio/minio:latest` | `127.0.0.1:9000-9001->9000-9001/tcp` |

`docker compose config` `host_ip` for each published port was `127.0.0.1`.

## Versions observed

From the running containers (not the host JDK):

- API: Spring Boot 4.1.0, `Starting GymBuddyApplication v0.2.0-SNAPSHOT using Java 25.0.3`
- `java -version` in `api`: `openjdk version "25.0.3" 2026-04-21 LTS` / `Temurin-25.0.3+9`
- Postgres: `postgres (PostgreSQL) 18.6 (Debian 18.6-1.pgdg13+2)`; Flyway logged `jdbc:postgresql://postgres:5432/gymbuddy (PostgreSQL 18.6)`
- Redis: `Redis server v=8.10.0`
- MinIO: `RELEASE.2025-09-07T16-13-09Z` (`go1.24.6 linux/amd64`)
- Docker Engine 29.1.3, Docker Compose 2.40.3

## What failed first (and what was fixed)

An earlier `docker compose up -d` on this host (2026-08-18T10:33:44Z) started the same services on `127.0.0.1`. After the API process was up:

- `GET /api/v1/healthz` → **200** `{"status":"ok"}` at 2026-08-18T10:38:07Z
- `GET /api/v1/readyz` → **503** `{"status":"unavailable","details":{"postgres":"not configured"}}`

Flyway had already applied `V1` against PostgreSQL 18.6. The JDBC readiness adapter was gated on `@ConditionalOnBean(JdbcTemplate)`, which is evaluated before `PersistenceConfiguration` creates that bean, so `readyz` used the "not configured" stub. That adapter now keys off `DATABASE_URL`. The API also waits for `minio-init` (`service_completed_successfully`) so the bucket exists before start.

A clean `docker compose down` + `docker compose up -d --build` after that change is the 200/200 run above.
