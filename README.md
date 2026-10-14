# rindetuplata

**Where does your money earn the most today?** An Argentine yield comparator that ranks plazo fijo,
money-market funds, remunerated wallets and dollar MEP by **real return**: how much each one beats or
loses to inflation.

Status: early development. See `odd/tasks/rindetuplata-mvp.md` for scope and progress.

## Planned features

- Daily ranking of yields with real return against INDEC inflation
- History per instrument, built from daily ingestion
- Simulator: "I have ARS 500,000 for 60 days, what is best?"
- Alerts: "tell me when a wallet goes above X% a year"

## Stack

Java 21 · Spring Boot 4 · PostgreSQL · Flyway · Testcontainers · (planned) Spring Batch, RabbitMQ

## Run

```bash
./mvnw test
./mvnw spring-boot:test-run
```

Requires Docker for the Testcontainers PostgreSQL.

## Daily raw capture

Some sources only expose today's value (plazo fijo by entity, wallet and fintech yields), so history
lost on a missed day cannot be recovered. A scheduled GitHub Actions workflow
(`.github/workflows/daily-capture.yml`) archives those responses every day at 12:00 Argentina time on
the orphan [`data`](../../tree/data) branch, under `raw/YYYY/MM/DD/`, with a `manifest.json` per day
(status, size and sha256 per source). The ingestion replays this archive later.

```bash
capture/test/capture_test.sh                                  # deterministic tests, no network
capture/capture.sh capture/sources.conf /tmp/capture "$(date +%F)"  # live capture to a local dir
```

Sources are listed in `capture/sources.conf`. A failing source never blocks the others and never
deletes an earlier good capture of the same day; the workflow fails so the owner gets notified.
