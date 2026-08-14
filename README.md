# gym-buddy-service

Java API for Gym Buddies (Spring Boot, Java 26, PostgreSQL 18). Product decisions live in [`gym-buddy-documentation`](https://github.com/Projet-de-compensation-2025-2026/gym-buddy-documentation).

This repository is **pipeline-first**. Application code is not here yet. Until `pom.xml` exists, CI/Release/Deploy run against a small Docker probe so the smoke and VM replace path are real.

| Workflow | Trigger | Promise |
| --- | --- | --- |
| CI | PR / push on `develop` | format, tests, container actually answers HTTP |
| Release | `workflow_dispatch` | squash `develop` → `main`, tag `vX.Y.Z` |
| Deploy | that tag | push `ghcr.io/.../gym-buddy-service:vX.Y.Z` and replace the VM container when `DEPLOY_*` secrets exist |

See [07-CI-CD.md](https://github.com/Projet-de-compensation-2025-2026/gym-buddy-documentation/blob/develop/70-Engineering-practices/07-CI-CD.md).
