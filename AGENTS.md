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
capture/test/capture_test.sh      # daily raw capture tests (bash + jq + python3, no network)
```

## Model routing and delegation

The parent session orchestrates; it keeps its own context thin (delegate before ~150k tokens). Route by the
shape of the work, handing off by reference (`odd/tasks/rindetuplata-mvp.md`, task, specs), never by paraphrase:

| Route | Use for | Limits |
|---|---|---|
| Sonnet 5.5 medium, `gentle-ai-worker` subagent | Units that really merit it: multi-file features, delicate reasoning (domain math, adapters) | Shares the Claude quota |
| Haiku 5.5 xhigh, herdr pane | Super-bounded edits: 1-2 files, one rule, explicit allowed paths | Stay under 100k tokens; a fresh pi session already uses ~50k, a 3-rule task peaked at ~105k; shares the Claude quota |
| DeepSeek v4 flash, herdr pane | Speed: fast reads over large context (1M), summaries, bulk lookups, mechanical edits | Max 16.4K output tokens per reply: write big files in several edits; does not use the Claude quota |

Herdr pane recipe (only inside herdr, `HERDR_ENV=1`):

```bash
pane=$(herdr pane split --current --direction right --cwd "$PWD" --no-focus | jq -r .result.pane.pane_id)
herdr agent start <name> --kind pi --pane "$pane" -- --model claude-bridge/claude-haiku-5-5:xhigh   # or nan/deepseek-v4-flash
herdr agent prompt <name> "<bounded task: files allowed, test-first, no commit, short reply>" --wait --timeout 1500000
herdr agent read <name> | tail -40   # plain terminal text, not JSON
herdr pane close "$pane"
```

Run the prompt step with `bash_background` for long tasks. Measure a pane's real context from its session file in
`~/.pi/agent/sessions/--home-picala-code-rindetuplata--/` (assistant `usage`: input + cacheRead + cacheWrite).
The parent always verifies pane output (diff scope, tests) and makes the commit.

## Conventions

- Money and rates are `BigDecimal`, never `double`/`float`. State rounding mode and scale explicitly.
- Rates: store TNA as given by the source; derive TEA and real return in the domain layer, with tests.
- One adapter per external source behind a port per data kind (S5). Tests use recorded JSON fixtures in
  `src/test/resources/fixtures/`, never live HTTP.
- Ingestion is idempotent: re-running a day must not duplicate snapshots.
- Code, comments, identifiers, commits and docs in English. Conventional Commits.
- One work-unit commit per task with its tests; record the commit hash in the task table.
