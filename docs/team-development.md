# Team Development Guide

This project uses separate environments for individual development, pull-request
validation, and shared integration testing.

| Environment | Purpose | Data |
|---|---|---|
| Developer laptop | Isolated coding and destructive testing | Local disposable PostgreSQL and Redis volumes |
| GitHub Actions | Automated pull-request checks | Mocked unit tests today; isolated integration dependencies recommended |
| Shared Docker host | Team integration and demonstrations | Persistent synthetic demo data |

The database on the shared Docker host is a real PostgreSQL database populated
with synthetic data. It is a shared integration database, not an in-process mock
and not a production database.

## Common setup

Install Git and clone the repository:

```bash
git clone https://github.com/RuchaDhondge/Test_Demo_ShowTime.git
cd Test_Demo_ShowTime
git checkout -b feature/short-description
```

Do not develop directly on `main`. Never commit `.env` files, credentials, or
tokens.

## Frontend development without Docker

Requirements:

- Node.js 24
- npm
- Access to either a local or shared backend

```bash
cd frontend
cp .env.example .env.local
npm ci
npm run dev
```

Set `VITE_API_PROXY_TARGET` in `frontend/.env.local`.

Use the shared backend for frontend-only work:

```env
VITE_API_PROXY_TARGET=http://SHARED_HOST:8080
```

Use a backend running on the same laptop:

```env
VITE_API_PROXY_TARGET=http://localhost:8080
```

Open <http://localhost:5173>. Requests under `/api` are proxied by Vite, so the
React source continues to use same-origin API paths.

Before pushing:

```bash
npm run lint
npm run build
```

Frontend-only work against the shared backend changes shared demo state when it
creates seat holds or uploads event images. Use synthetic data.

## Backend unit testing

Requirements:

- Java 17
- No Docker for the current mocked unit tests

```bash
cd backend
./mvnw test
```

The existing unit tests mock repositories and external services. They are fast,
but they do not prove that Flyway migrations or JPA queries work against
PostgreSQL.

## Backend development with isolated dependencies

For normal backend development, run Spring Boot from the IDE or Maven and run
only PostgreSQL, Redis, and the AWS Emulator in Docker:

```bash
docker compose -f compose.dependencies.yml up -d
docker compose -f compose.dependencies.yml ps

cd backend
./mvnw spring-boot:run
```

The default Spring configuration connects to:

| Dependency | Address |
|---|---|
| PostgreSQL | `localhost:5432`, database/user/password `booking` |
| Redis | `localhost:6379` |
| AWS Emulator proxy | `http://localhost:9298` |

Override those defaults with `DB_URL`, `DB_USER`, `DB_PASSWORD`, `REDIS_HOST`,
`REDIS_PORT`, `AWS_ENDPOINT_URL`, and `AWS_S3_BUCKET`.

Verify the backend:

```bash
curl -fsS http://localhost:8080/actuator/health
curl -fsS 'http://localhost:8080/api/events?city=Pune'
curl -fsS http://localhost:8080/api/events/1/seats
```

Stop dependencies without deleting data:

```bash
docker compose -f compose.dependencies.yml down
```

Reset only the developer's isolated data:

```bash
docker compose -f compose.dependencies.yml down -v
docker compose -f compose.dependencies.yml up -d
```

Never run `down -v` against the shared Compose project.

## Full-stack development

Developers who want the same topology as the hosted environment can run the
complete stack:

```bash
cp .env.example .env
docker compose up -d --build
docker compose ps
```

Open <http://localhost:8080>. After source changes, rebuild the affected
container:

```bash
docker compose up -d --build backend
docker compose up -d --build web
```

The default PostgreSQL bind address is `127.0.0.1`. Do not expose a developer
database to the LAN.

## Database and Flyway changes

PostgreSQL is authoritative for booking state. Make schema changes only through
new Flyway migrations under:

```text
backend/src/main/resources/db/migration/
```

Example:

```text
V2__add_user_profile.sql
V3__add_event_search_indexes.sql
```

Rules:

1. Never edit a migration that has already been applied.
2. Never manually alter the shared schema as the implementation of a change.
3. Test a new migration against a fresh local database.
4. Include constraints, indexes, and data transformations in the migration.
5. Document recovery steps for destructive or long-running changes.
6. Back up the shared database before a significant migration.

Keep permanent schema and reference data in versioned migrations. Keep large or
scenario-specific mock datasets in explicit development seed scripts rather
than production migrations.

## Should a local backend use the shared database?

Not by default. A local backend connected to the shared database can overwrite
team data, run Flyway migrations unexpectedly, and interfere with other tests.
The existing `booking_reader` account is read-only and cannot support the
application.

If centrally hosted developer databases are required, create a separate
database and write credential per developer or branch, for example:

```text
booking_shared
booking_alex
booking_pr_123
```

Redis and emulated S3 state must also be isolated or explicitly treated as
shared. One common writable database for every developer is not recommended.

## Pull requests and deployment

Before opening a pull request:

```bash
(cd backend && ./mvnw test)
(cd frontend && npm ci && npm run lint && npm run build)
```

The pull request should describe:

- frontend and backend behavior changed;
- API contract changes;
- database migrations;
- configuration or secret requirements;
- test evidence.

Pull requests run tests but do not deploy. After an approved change reaches
`main`, GitHub Actions tests the project, publishes commit-SHA images to GHCR,
and the self-hosted runner updates the shared Compose environment.

## Recommended CI improvement

Add PostgreSQL and Redis integration tests using Testcontainers or GitHub
Actions service containers. At minimum, CI should start a real PostgreSQL
instance, apply every Flyway migration, load the Spring context, and execute
repository and seat-locking tests. This closes the gap between mocked unit tests
and the shared deployment.

## Shared-host responsibilities

- Keep the host awake, connected, and running Docker.
- Use only synthetic data.
- Restrict the application to the approved office network.
- Keep Redis and the AWS Emulator private.
- Expose PostgreSQL only when approved and provide read-only credentials.
- Preserve the Compose project name and persistent volumes.
- Back up PostgreSQL before risky migrations.
- Do not run untrusted pull-request code on the persistent self-hosted runner.
