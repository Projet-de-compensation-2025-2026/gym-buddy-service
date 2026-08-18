# Changelog

Format: [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).
Versioning: [Semantic Versioning](https://semver.org/).

## [Unreleased]

### Added

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
