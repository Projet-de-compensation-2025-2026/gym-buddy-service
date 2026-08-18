# VPS data plane (operator runbook)

Apply this on the OVH VPS (`vps-c39cdf03.vps.ovh.net`). This cloud checkout cannot SSH there.

This is **not** the laptop stack. Repo-root `compose.yaml` stays local (`127.0.0.1` published ports). Do not run that file on the VPS.

Public story stays **Caddy → `127.0.0.1:8080`**. The API is not published on `0.0.0.0`. Data-plane ports stay off the host.

Release → Deploy still replaces the API with `deploy/replace.sh` + GHCR. Stay on application **0.1.x** (do not tag `0.3.0`).

## Files on the VPS

From this repository (clone or copy):

| Path | Role |
| --- | --- |
| `deploy/compose.yaml` | Private data plane (PostgreSQL 18, Redis, MinIO) |
| `deploy/vps.env.example` | Key template. Copy, then fill **on the host** |
| `deploy/replace.sh` | GHCR pull + `docker run` of the API (Deploy copies this each release) |
| This page | Operator steps |

Canonical env file: **`/etc/gym-buddy/vps.env`**. Never commit it. `replace.sh` reads it (`DEPLOY_ENV_FILE` overrides the path).

## Env keys (values stay on the VPS)

Do not put JWT material, database passwords, or object-store secrets in git or in this page.

| Key | Used by | Purpose |
| --- | --- | --- |
| `POSTGRES_PASSWORD` | `deploy/compose.yaml` | Creates the PostgreSQL 18 role. Not an application key. |
| `DATABASE_URL` | API via `replace.sh` | PostgreSQL 18. Host must be Docker DNS `postgres` (not `127.0.0.1`). |
| `REDIS_URL` | API via `replace.sh` | Cache / refresh denylist. Host must be `redis`. |
| `JWT_ACCESS_SECRET` | API via `replace.sh` | HS256 signing secret for access tokens. |
| `S3_ENDPOINT` | API + MinIO | S3-compatible API URL. Use `http://minio:9000` on the Docker network. |
| `S3_BUCKET` | API + `minio-init` | Bucket name. |
| `S3_ACCESS_KEY` | API + MinIO | Object-store access key. |
| `S3_SECRET_KEY` | API + MinIO | Object-store secret key. |
| `S3_REGION` | API via `replace.sh` | Region string the client library expects. |
| `SPRING_PROFILES_ACTIVE` | API via `replace.sh` | Must be `prod`. Production refuses to start without S3-compatible storage. |

`replace.sh` also honors (optional):

| Key | Default | Purpose |
| --- | --- | --- |
| `DEPLOY_BIND` | `127.0.0.1` | Host address for the published API port. `0.0.0.0` is refused. |
| `DEPLOY_HOST_PORT` | `8080` | Host port Caddy proxies to. |
| `DEPLOY_CONTAINER_NAME` | `gym-buddy-service` | API container name. |
| `DEPLOY_NETWORK` | `gym-buddy-data` | Private network created by `deploy/compose.yaml`. |
| `DEPLOY_ENV_FILE` | `/etc/gym-buddy/vps.env` | Env file on the VPS. |

GitHub Actions secrets stay the existing names only: `DEPLOY_HOST`, `DEPLOY_USER`, `DEPLOY_SSH_KEY`, optional `DEPLOY_PORT`. Those are SSH, not application secrets.

## 1. Create the env file

```bash
sudo mkdir -p /etc/gym-buddy
sudo cp deploy/vps.env.example /etc/gym-buddy/vps.env
sudo chmod 600 /etc/gym-buddy/vps.env
sudo "${EDITOR:-nano}" /etc/gym-buddy/vps.env
```

Set every key in the table above. For `DATABASE_URL`, use the compose service name as host:

```text
postgresql://gymbuddy:<POSTGRES_PASSWORD>@postgres:5432/gymbuddy
```

`REDIS_URL` is `redis://redis:6379`. `S3_ENDPOINT` is `http://minio:9000`. Leave `SPRING_PROFILES_ACTIVE=prod`.

## 2. Start the data plane

From a checkout of this repo on the VPS:

```bash
docker compose --env-file /etc/gym-buddy/vps.env -f deploy/compose.yaml up -d
```

Wait until Postgres and Redis are healthy and `minio-init` has exited 0:

```bash
docker compose --env-file /etc/gym-buddy/vps.env -f deploy/compose.yaml ps
```

That command creates the named network `gym-buddy-data`. `replace.sh` joins it with `docker run --network gym-buddy-data`. The API then resolves `postgres`, `redis`, and `minio` by Docker DNS.

Do **not** add `ports:` for 5432 / 6379 / 9000 / 9001. Confirm the host is not listening on those:

```bash
ss -lnt | grep -E ':5432|:6379|:9000|:9001' || echo "data-plane ports not published"
docker compose --env-file /etc/gym-buddy/vps.env -f deploy/compose.yaml ps --format '{{.Name}} {{.Publishers}}'
```

`Publishers` must be empty.

## 3. Replace the API (first time, or any time)

`replace.sh` still does `docker pull` + stop/rm + `docker run`. It is **not** `docker compose up` for the API.

First proof before a new tag exists: build the Java 25 image on the VPS from this tree, then replace:

```bash
docker build -t gym-buddy-service:local .
sudo env \
  DEPLOY_ENV_FILE=/etc/gym-buddy/vps.env \
  DEPLOY_BIND=127.0.0.1 \
  ./deploy/replace.sh gym-buddy-service:local
```

After a **0.1.x** Release (not `0.3.0`), Deploy copies `deploy/replace.sh` over SSH and runs it with the GHCR tag. Same env file, same network. You do not pass secret values through GitHub Actions.

Manual GHCR replace (operator already logged in, or `GHCR_USERNAME` + `GHCR_TOKEN` in the environment):

```bash
sudo env \
  DEPLOY_ENV_FILE=/etc/gym-buddy/vps.env \
  DEPLOY_BIND=127.0.0.1 \
  GHCR_USERNAME="$GHCR_USERNAME" \
  GHCR_TOKEN="$GHCR_TOKEN" \
  ./deploy/replace.sh ghcr.io/projet-de-compensation-2025-2026/gym-buddy-service:v0.1.x
```

What `replace.sh` does on that `docker run`:

- `--network gym-buddy-data` (or `DEPLOY_NETWORK`)
- `-p ${DEPLOY_BIND:-127.0.0.1}:${DEPLOY_HOST_PORT:-8080}:8080`
- `-e` for `SPRING_PROFILES_ACTIVE=prod`, `DATABASE_URL`, `REDIS_URL`, `JWT_ACCESS_SECRET`, `S3_ENDPOINT`, `S3_BUCKET`, `S3_ACCESS_KEY`, `S3_SECRET_KEY`, `S3_REGION`

It fails closed if the env file, a required key, or the Docker network is missing. It refuses `DEPLOY_BIND=0.0.0.0`.

## 4. Prove healthz / readyz

On the VPS (loopback, same target Caddy uses):

```bash
curl -sS -D- http://127.0.0.1:8080/api/v1/healthz
curl -sS -D- http://127.0.0.1:8080/api/v1/readyz
```

Expect HTTP 200 and `{"status":"ok"}` on both. `readyz` 200 means PostgreSQL and object storage are reachable from the API.

Through Caddy (from a host allowed to reach :443):

```bash
curl -sS -D- https://vps-c39cdf03.vps.ovh.net/api/v1/healthz
curl -sS -D- https://vps-c39cdf03.vps.ovh.net/api/v1/readyz
```

Confirm the API listen address:

```bash
ss -lnt | grep ':8080'
docker port gym-buddy-service
```

Expect `127.0.0.1:8080`. Not `0.0.0.0:8080`.

`SPRING_PROFILES_ACTIVE=prod` still refuses to start if `S3_ENDPOINT` / `S3_BUCKET` / credentials are blank. There is no local `uploads/` fallback.

## 5. Ongoing releases

1. Keep `/etc/gym-buddy/vps.env` and `docker compose … -f deploy/compose.yaml` running.
2. When a 0.1.x version is stable, run the existing **Release** workflow (pin `version=0.1.x` if needed). Do not bump the application to `0.3.0`.
3. **Deploy** builds `ghcr.io/projet-de-compensation-2025-2026/gym-buddy-service:vX.Y.Z`, copies `deploy/replace.sh`, and runs it. The new container joins `gym-buddy-data` and reads the VPS env file.

Do not compose the API on the VPS. Do not publish friends / feed / events in this slice.
