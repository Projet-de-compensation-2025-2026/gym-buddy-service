# Changelog

Format: [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).
Versioning: [Semantic Versioning](https://semver.org/).

## [Unreleased]

### Added

- Local `compose.yaml` and `.env.example`: PostgreSQL 18, Redis, MinIO, probe API on `127.0.0.1`, optional MailHog profile. Not used on the VPS.

### Changed

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
