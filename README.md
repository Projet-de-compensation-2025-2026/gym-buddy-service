# gym-buddy-service

Java 25 LTS / Spring Boot 4.1 API for Gym Buddies (PostgreSQL 18). Product decisions live in [`gym-buddy-documentation`](https://github.com/Projet-de-compensation-2025-2026/gym-buddy-documentation).

This slice ships the local data plane, liveness/readiness, JWT auth, profiles, friends, disk-safe media (`/api/v1/media`), posts (`/api/v1/posts`), and nested comments (`/api/v1/comments`). Feed, events, and messaging come in later tickets. The HTTP contract is the versioned [`gym-buddy-openapi`](https://github.com/Projet-de-compensation-2025-2026/gym-buddy-openapi) package, not a running `/v3/api-docs`. `mvn` fetches a **git tag or 40-character commit SHA** (`openapi.package.tag`) at generate-sources and points OpenAPI Generator at `openapi/openapi.yaml` so the `$ref` tree resolves. Ticket #62 pins develop SHA `01ab3d50195833296b10e8ca44aa89d1e046683a` until the next 0.1.x tag. Controllers implement the generated interfaces. Do not commit a second YAML or hand-edit generated sources. Do not generate from `bundled.yaml` or the branch name `develop`.

| Workflow | Trigger | Promise |
| --- | --- | --- |
| CI | PR / push on `develop` | Spotless, JUnit (+ Testcontainers for `readyz` and auth), container answers `GET /api/v1/healthz` |
| Release | `workflow_dispatch` | write SemVer into `pom.xml`, squash `develop` → `main`, tag `vX.Y.Z` |
| Deploy | that tag | push `ghcr.io/.../gym-buddy-service:vX.Y.Z` and replace the VM container when `DEPLOY_*` secrets exist |

See [07-CI-CD.md](https://github.com/Projet-de-compensation-2025-2026/gym-buddy-documentation/blob/develop/70-Engineering-practices/07-CI-CD.md).

## Local data plane

Postgres 18, Redis, MinIO, and this API. Laptop stack from the [runbook](https://github.com/Projet-de-compensation-2025-2026/gym-buddy-documentation/blob/develop/10-Getting-started/04-Environment-and-pipeline.md). Do **not** run `compose.yaml` on the VPS.

```bash
cp .env.example .env
docker compose up -d
```

Requires JDK 25 (Temurin) and Maven on the host if you run the app outside Compose:

```bash
mvn -B test
mvn -B spring-boot:run
```

`mvn -B test` is the repo test command (generate-sources, then JUnit / Testcontainers). There is no Maven wrapper in git.

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

Auth (`POST /api/v1/auth/register`, `/login`, `/refresh`, `/logout`): Argon2id passwords, HS256 access JWT (15 min, claims `sub` / `handle` / `role` / `typ=access`), refresh cookie (`HttpOnly; Secure; SameSite=Lax`; path `/api/v1/auth`; 14 days). `JWT_ACCESS_SECRET` signs both tokens. Logout denylists the refresh `jti` in Redis.

Unauthenticated probes (OpenAPI `healthz` / `readyz`):

| Path | Meaning |
| --- | --- |
| `GET /api/v1/healthz` | Process is up. Body is only `{"status":"ok"}`. |
| `GET /api/v1/readyz` | PostgreSQL and object storage. `200 {"status":"ok"}` or `503` with `details` naming `postgres` and/or `objectStorage`. |

`SPRING_PROFILES_ACTIVE=prod` refuses to start unless `S3_ENDPOINT`, `S3_BUCKET`, `S3_ACCESS_KEY`, and `S3_SECRET_KEY` are set. There is no local `uploads/` fallback.

## VPS data plane

PostgreSQL 18, Redis, and MinIO on the OVH VPS use **`deploy/compose.yaml`**, not this laptop file. Ports `5432` / `6379` / `9000` / `9001` are not published. `deploy/replace.sh` joins `gym-buddy-data` and injects VPS env from `/etc/gym-buddy/vps.env` (not committed). `DEPLOY_BIND` stays `127.0.0.1`. Operator steps: [`docs/vps-data-plane.md`](docs/vps-data-plane.md).
