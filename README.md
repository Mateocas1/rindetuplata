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
