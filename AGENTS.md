# rindetuplata — agent guide

Argentine yield comparator: "where does your money earn the most today?" Compares plazo fijo by bank,
money-market funds, remunerated wallets and dollar MEP by **real return** (against inflation), keeps
daily history, simulates amount + days, and sends alerts.

## Start here

1. Read `odd/tasks/rindetuplata-mvp.md` completely (Specs, Tasks, open decisions, verified sources, Log).
2. Pick the first `pending` task whose decisions are resolved. Ask the owner about an open decision (D#)
   only when the task you are about to start needs it.
3. Engram mirror topic: `odd/rindetuplata-mvp/tasks`.

## Stack

- Java 21, Spring Boot 4.1.1, Maven wrapper (`./mvnw`; Maven is not installed globally).
- PostgreSQL 18 with Flyway migrations (`src/main/resources/db/migration`).
- Tests: JUnit 5, Spring Boot test slices, Testcontainers (`postgres:18-alpine`). Docker must be running.

## Commands

```bash
./mvnw test                       # unit + integration tests (starts Postgres via Testcontainers)
./mvnw spring-boot:test-run       # run locally with a Testcontainers Postgres (TestRindetuplataApplication)
docker compose up -d postgres     # standalone local database (compose.yaml)
```

## Conventions

- Money and rates are `BigDecimal`, never `double`/`float`. State rounding mode and scale explicitly.
- Rates: store TNA as given by the source; derive TEA and real return in the domain layer, with tests.
- One adapter per external source behind a port per data kind (S5). Tests use recorded JSON fixtures in
  `src/test/resources/fixtures/`, never live HTTP.
- Ingestion is idempotent: re-running a day must not duplicate snapshots.
- Code, comments, identifiers, commits and docs in English. Conventional Commits.
- One work-unit commit per task with its tests; record the commit hash in the task table.
