# gym-buddy-service

Java API for Gym Buddies (Spring Boot 4.1, Java 26, PostgreSQL 18). Product decisions live in [`gym-buddy-documentation`](https://github.com/Projet-de-compensation-2025-2026/gym-buddy-documentation).

The HTTP contract is owned by [`gym-buddy-openapi`](https://github.com/Projet-de-compensation-2025-2026/gym-buddy-openapi). This service implements it. Public health is `GET /api/v1/healthz` and `GET /api/v1/readyz`, not `/actuator/health`.

| Workflow | Trigger | Promise |
| --- | --- | --- |
| CI | PR / push on `develop` | Spotless, JUnit (incl. Testcontainers for `readyz`), container answers `GET /api/v1/healthz` |
| Release | `workflow_dispatch` | squash `develop` → `main`, tag `vX.Y.Z` |
| Deploy | that tag | push `ghcr.io/.../gym-buddy-service:vX.Y.Z` and replace the VM container when `DEPLOY_*` secrets exist |

See [07-CI-CD.md](https://github.com/Projet-de-compensation-2025-2026/gym-buddy-documentation/blob/develop/70-Engineering-practices/07-CI-CD.md).

## Local data plane

Postgres 18, Redis, MinIO, and this API. This is the laptop stack from the [runbook](https://github.com/Projet-de-compensation-2025-2026/gym-buddy-documentation/blob/develop/10-Getting-started/04-Environment-and-pipeline.md). Do **not** run `compose.yaml` on the VPS.

```bash
cp .env.example .env
docker compose up -d
```

Every published port binds `127.0.0.1`:

| Service | Port |
| --- | --- |
| API | 8080 |
| PostgreSQL 18 | 5432 |
| Redis | 6379 |
| MinIO S3 | 9000 |
| MinIO console | 9001 |
| MailHog (optional) | SMTP 1025, UI 8025 |

MailHog is opt-in:

```bash
docker compose --profile mail up -d
```

`SPRING_PROFILES_ACTIVE=prod` refuses to start without `S3_ENDPOINT`, `S3_BUCKET`, and credentials. There is no local `uploads/` fallback.

CI smoke builds only the API image (no Postgres / MinIO) and therefore only hits `healthz`. `readyz` is covered by Testcontainers in `mvn test`.
