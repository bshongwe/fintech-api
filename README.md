# fintech-api — Starter Repo (initial commit)

This is a starter monorepo scaffold for a Java-based fintech API.
It includes two Spring Boot services: `auth-service` and `account-service`,
a shared `libs/commons` module, Docker Compose for local development,
and basic infra and scripts.

## Quick start (local)

Requirements:
- Java 17+
- Gradle 8+
- Docker & Docker Compose (for running services together)

From repo root:

```bash
# build services
./gradlew build

# run locally (builds Docker images)
./scripts/run-local.sh
```

The account service exposes:
- `GET http://localhost:8081/v1/accounts/{id}/balance`

Authorization server runs on port 9000 (placeholder in-memory server).

This is a minimal scaffold — extend with full FAPI auth server, DB migrations, bank adapters, and contract tests.
