# Changelog

Format: [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).
Versioning: [Semantic Versioning](https://semver.org/).

## [Unreleased]

### Added

- Posts, likes, and reposts (FS-POST-01..08). Ticket #61. Flyway `V6__posts.sql`. Visibility `friends` (default) or `public`. 15-minute edit window. Soft-delete. Idempotent like. Unique repost. Max 4 image `mediaIds`. Stranger on a friends-only post is `NOT_FOUND`.
- Disk-safe media: `POST /api/v1/media`, `GET /api/v1/media/{id}/url`, `DELETE /api/v1/media/{id}` (FS-MED-01..09). Ticket #68. Bytes go to MinIO via signed PUT. Signed GET is 60 s after `canRead`. Quota 1 GiB (`QUOTA_EXCEEDED`). Images jpeg/png/webp max 8 MiB with EXIF-stripped sm/md WebP variants. Flyway `V5__media.sql`.
- Profiles: `GET`/`PATCH /api/v1/profiles/me`, `GET /api/v1/profiles/{handle}` with private stubs (FS-PROF-01..06). Ticket #59.
- `POST /api/v1/auth/password` (FS-ACCT-05) and `POST /api/v1/me/close` (FS-ACCT-07). Closed login is generic `FORBIDDEN`.
- Flyway `V3__profiles_and_closed.sql`: remaining profile columns and `users.status=closed`.
- Friend requests, accept/decline, unfriend, and blocks (FS-FRND-01..08). Ticket #60. Flyway `V4__friendships.sql`.
- Build-time OpenAPI Generator: fetch `gym-buddy-openapi` `bundled.yaml` and generate models + API interfaces. `AuthController` / `HealthController` implement those interfaces (ticket #41).
- `POST /api/v1/auth/register`, `/login`, `/refresh`, `/logout`: Argon2id passwords, HS256 access JWT, refresh cookie, Redis `jti` denylist (ticket #12).
- Flyway `V2__users_and_profiles.sql` (`users` + `profiles`). First registered user is `admin`.
- Java 25 LTS / Spring Boot 4.1 application (`pom.xml`): `GET /api/v1/healthz` and `GET /api/v1/readyz`.
- Flyway baseline, `DATABASE_URL` → JDBC, MinIO/S3 readiness, production refuse-without-S3.
- Testcontainers coverage for `readyz`. Smoke hits `/api/v1/healthz` when `pom.xml` exists.
- Local `compose.yaml` and `.env.example`: PostgreSQL 18, Redis, MinIO, API on `127.0.0.1`, optional MailHog profile. Not used on the VPS.
- Runtime proof that local compose answers `healthz` and `readyz` 200 on `127.0.0.1` (`docs/local-compose-proof.md`). Not a VPS/Postgres-on-OVH claim.
- VPS data-plane compose (`deploy/compose.yaml`): PostgreSQL 18, Redis, MinIO on private network `gym-buddy-data` with no published ports. `replace.sh` joins that network and injects VPS env (`DATABASE_URL`, `REDIS_URL`, `S3_*`, `JWT_ACCESS_SECRET`, `SPRING_PROFILES_ACTIVE=prod`). Operator runbook: `docs/vps-data-plane.md`.

### Fixed

- Compose `readyz` selected the "postgres not configured" stub while Flyway could already reach PostgreSQL. The JDBC adapter now keys off `DATABASE_URL`. The API waits for `minio-init` before start.

### Changed

- Release writes the computed SemVer into `pom.xml` before the tag (documentation ticket #53). Humans do not hand-edit that number. Auto bump still never chooses `1.0.0`.
- OpenAPI Generator now consumes the versioned `gym-buddy-openapi` package tag `v0.1.0` (`openapi/openapi.yaml` `$ref` tree) instead of a raw `develop` GET of `bundled.yaml` (ticket #47).
- Pin gym-buddy-openapi `develop` SHA `dc3488158a302de9475153f124f7f98a6e4dba9b` for ticket #59 until the next 0.1.x tag. `scripts/fetch-openapi-tree.sh` accepts a 40-character commit SHA.
- Pin gym-buddy-openapi develop SHA `3e63187727035b5277738db90c44744406057b4c` for ticket #60.
- Pin gym-buddy-openapi develop SHA `edca075cdf1e1eb6caf6f094e02cadaba7c480b5` for ticket #68.
- Pin gym-buddy-openapi develop SHA `d58a824e0720c2f50c56632e3664d3632484e281` for ticket #61.
- Auth JSON follows the spec: `AccessTokenResponse` is `accessToken` only; register returns generated `RegisteredUser`; handle `minLength` is 1.
- CI uses `actions/setup-java@v5` (Temurin 25) and `mvn` (no Maven wrapper binaries).
- Document Java 25 LTS as the approved stack (README, CHANGELOG, Dockerfile comments).

## [0.1.1] — 2026-08-17

### Added

### Changed

## [0.1.0] — 2026-08-17

### Added

- GitHub Actions CI on `develop`, Release squash+tag onto `main`, Deploy to GHCR (and SSH when secrets exist)
- Pipeline probe image so smoke and deploy work before Spring Boot exists

[Unreleased]: https://github.com/Projet-de-compensation-2025-2026/gym-buddy-service

[0.1.1]: https://github.com/Projet-de-compensation-2025-2026/gym-buddy-service/releases/tag/v0.1.1
[0.1.0]: https://github.com/Projet-de-compensation-2025-2026/gym-buddy-service/releases/tag/v0.1.0
