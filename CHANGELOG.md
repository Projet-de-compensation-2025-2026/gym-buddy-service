# Changelog

Format: [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).
Versioning: [Semantic Versioning](https://semver.org/).

## [Unreleased]

### Added

- Laptop Compose builds a sibling `gym-buddy-ui` image and publishes it on
  `127.0.0.1:4200`. Nginx serves the member app and `/admin/`, and proxies
  `/api` (including `/api/v1/ws`) to the API. `local` profile CORS/WS origins
  include `http://127.0.0.1:4200` and `http://localhost:4200`.

### Changed

## [1.1.1] — 2026-08-31

### Added

### Changed

### Fixed

- `PATCH /profiles/me` JSON-merges: omitted fields stay unchanged (visibility-only no longer wipes sports, windows, bio, city, or coords). Unknown `experienceLevel` is `422 VALIDATION`. Stored `preferredWindows` survive PostgreSQL jsonb key order on GET. Pin gym-buddy-openapi `41f6613e5653fac0e4fd6398eade24d1a84b2631` (ticket **#119**, FS-PROF-02, FS-PROF-06).
- A valid member JWT no longer receives `401 UNAUTHENTICATED` `media is not configured`. `POST /media` mints a signed PUT when object storage is wired (S3 beans are not skipped by a same-class `@ConditionalOnBean`). Missing `GET /media/{id}/url` is `404 NOT_FOUND`. If media cannot be used, the body is `503` readyz-style and `GET /readyz` is not 200 (ticket **#121**, FS-MED-02, FS-MED-06).
- Search `page.next` encodes rank with `Double.toString` so `before` round-trips. `%.12f` rounded some scores (live events `0.199405646798`) and served the last hit again (ticket **#123**, FS-SRCH-05). Cursor stays opaque `{data, page.next, page.size}`.
- Applicant `GET /events/{id}` after series or occurrence cancel keeps
  `viewerApplication.status=cancelled`. Cancelled occurrences report
  `remainingSeats` 0 so clients do not treat them as open (ticket **#124**,
  FS-EVT-08).
- Member JWTs receive contract `NOT_FOUND` for every `/api/v1/admin/*` call, including `GET /admin/content` without `type` and empty-body `PATCH /admin/users/{id}/role` / `POST /admin/content/{type}/{id}/hide` (ticket **#116**, FS-ADM-09). Staff authorization runs before query/body validation. Unauthenticated `/admin/*` stays `401` UTF-8 JSON (ticket **#85**).
- `POST /api/v1/conversations` no longer returns Spring HTTP 500 when the two friends’ UUIDs disagree between Java `UUID.compareTo` (signed) and PostgreSQL `uuid <` (unsigned). Pair order now matches the `conversations_pair_order` check. Second POST for the same pair stays idempotent (ticket **#120**, FS-MSG-01).

## [1.1.0] — 2026-08-30

### Added

### Fixed

- `GET /api/v1/events` no longer returns HTTP 500. List SQL bound 6 viewer ids for 7 UUID placeholders (invitee `user_id` was missing). Organizer sessions appear in the page; ACL is unchanged (ticket **#96**).
- Unauthenticated `/api/v1/admin/*` JSON is `charset=UTF-8` (ticket **#85**). Members stay `NOT_FOUND`.
- Refresh cookie is `SameSite=None; Secure; HttpOnly; Partitioned; Path=/api/v1/auth` so GitHub Pages can send it (ticket **#89**). Access JWT stays in memory.
- `POST /auth/register` and profile handle patch reject handles that contain `@` or equal the email (`VALIDATION`, ticket **#103**).
- Env-gated `GYM_BUDDY_BOOTSTRAP_STAFF` inserts missing `demo.admin` / `demo.mod` without enabling `POST /admin/fixtures` on `prod` (ticket **#78**).
- Staff `GET /admin/content` lists hideable posts, comments, events, and media (FS-ADM-03). Members stay `NOT_FOUND`. Pin gym-buddy-openapi `16fd67df7691461062a797c25cbe2bb75531514d`.

- CI allows working `pom.xml` **1.0.0** now that tag **v1.0.0** exists. Auto bump still refuses an unattended 1.x; 2.0.0 stays rejected.

## [1.0.0] — 2026-08-30

### Added

- Deterministic Datafaker fixtures (ticket #70, FS-ADM-05). Seed `FIXTURE_SEED=20260813`. Factory classes plus CLI `mvn compile exec:java -Dexec.mainClass=fr.projetcompensation.gymbuddy.fixtures.FixturesCli -Dexec.args="--users 3000 --posts-per-user 5 --events 800 --reset"`. `POST /api/v1/admin/fixtures` generates (non-`prod`); `POST /api/v1/admin/fixtures/reset` truncates (`--reset`). Demo handles `demo.alex` / `demo.blake` / `demo.mod` / `demo.admin`. Media metadata reuses 10 stock MinIO keys. Integration tests use tens of rows, not the 3 000-user set. Pin gym-buddy-openapi develop SHA `f849a1dcd498c12fd9507b83f9d50d375d651347`.
- Private messaging (FS-MSG-01..10). Ticket #67. Flyway `V10__messaging.sql`. `GET`/`POST /api/v1/conversations`, `GET`/`POST /api/v1/conversations/{id}/messages`, `DELETE /api/v1/messages/{id}`, `GET /api/v1/ws`. Friends-only direct pair. Text 1–4000. Image/audio via existing `/media` (`kind=message`). Sender tombstone within 10 minutes. Inbox unread counts. Persistence first; WebSocket fan-out. Non-friends `FORBIDDEN`; stranger conversation `NOT_FOUND`. HTTP write succeeds when the socket is down.
- Admin and moderation (FS-ADM-01..09, FS-ACCT-08/09). Ticket #69. Flyway `V12__admin.sql` (`reports`, `audit_events`, media/comment hide columns). Staff `/api/v1/admin/*` (members `NOT_FOUND`). Moderator role PATCH is `FORBIDDEN`. Hide post → member `NOT_FOUND` plus `audit_events`. Last admin demote/lock is `CONFLICT`. Fixture trigger is implemented in ticket #70. Member `POST /api/v1/reports`. Pin gym-buddy-openapi develop SHA `8f89f1a72b1ddb6996d9598e6cedbac4d4788ace`.
- Personalized friend suggestions and weekly matching (FS-SUGG-01..07, FS-MATCH-01..03). Ticket #66. Flyway `V11__suggestions.sql`. `GET /api/v1/suggestions` default 20 max 50 with a plain-language `reason`; `POST /api/v1/suggestions/{userId}/dismiss` for 30 days. Two-stage generate-and-score (FoF ∪ city∩sport ∪ co-participants last 90 days). Forbidden set: self, friends, pending, blocked, dismissed-30d, locked/closed. `POST`/`DELETE /api/v1/matching/opt-in` and `GET /api/v1/matching/me`. Nightly greedy matching, no double assignment, no edge across a block, draft instant event capacity 1.
- Parameterized search `GET /api/v1/search/people` and `GET /api/v1/search/events` (FS-SRCH-01..08). Ticket #65. Filters AND across fields; sports ANY. Rank α ts_rank + β recency + γ geo + δ social. Cursor `before`. `radiusKm` 1–50. Private strangers, blocked users, and (when `remaining=true`) full events never appear. Unauthenticated is `401`.
- Instant and recurring events with apply/accept and transactional capacity (FS-EVT-01..13). Ticket #64. Flyway `V9__events.sql`. `FREQ=WEEKLY;BYDAY` + optional `UNTIL`, 90-day occurrences, visibility public/friends/private, last-seat accept is `CONFLICT`, organizer pending list ranked by matching score.
- Friends news feed `GET /api/v1/feed` (FS-FEED-01..06). Ticket #63. Flyway `V8__feed_repost_id.sql`. Viewer + accepted friends’ posts and reposts, reverse chrono on activity time, cursor `before`, default 20 max 50. Public non-friend posts stay off the feed. Hidden/deleted omitted. Size over 50 is `VALIDATION`.
- Nested comments (FS-CMT-01..07). Ticket #62. Flyway `V7__comments.sql`. Max depth 4 (root = 0). Author delete tombstones the body; children remain. Page roots (20) + expand replies. No media. Idempotent like. Stranger on a friends-only post is `NOT_FOUND`. Not the author on delete is `FORBIDDEN`.
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
- Pin gym-buddy-openapi develop SHA `8f89f1a72b1ddb6996d9598e6cedbac4d4788ace` for ticket #67.
- Pin gym-buddy-openapi develop SHA `8f89f1a72b1ddb6996d9598e6cedbac4d4788ace` for ticket #69 (includes search #65 and messaging #67).
- Pin gym-buddy-openapi develop SHA `2550b32f95dcb881b0bfaa37e30f130595dbe9d3` for ticket #65.
- Pin gym-buddy-openapi develop SHA `276b60d003b1e98a8d396b5c7f44cfe14b804d70` for ticket #66.
- Pin gym-buddy-openapi develop SHA `2ebc892909eed2a79841a4aea572aef1968747b4` for ticket #64.
- Pin gym-buddy-openapi develop SHA `82d0eadb592c023fe3934836c7ce0ca15ca56abd` for ticket #63.
- Pin gym-buddy-openapi develop SHA `01ab3d50195833296b10e8ca44aa89d1e046683a` for ticket #62.
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

[1.1.1]: https://github.com/Projet-de-compensation-2025-2026/gym-buddy-service/releases/tag/v1.1.1
[1.1.0]: https://github.com/Projet-de-compensation-2025-2026/gym-buddy-service/releases/tag/v1.1.0
[1.0.0]: https://github.com/Projet-de-compensation-2025-2026/gym-buddy-service/releases/tag/v1.0.0
[0.1.1]: https://github.com/Projet-de-compensation-2025-2026/gym-buddy-service/releases/tag/v0.1.1
[0.1.0]: https://github.com/Projet-de-compensation-2025-2026/gym-buddy-service/releases/tag/v0.1.0
