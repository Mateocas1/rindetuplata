# Recorded API fixtures

Real responses of the rindetuplata API, recorded on 2026-10-15 from a local stack
(`./mvnw spring-boot:test-run`, port 18080) after one LIVE `dailyIngestionJob` (capture date 2026-10-14).
Pages read these files at build time when `WEB_FIXTURES=1`, so tests and CI never need a running API.

| File | Request |
|---|---|
| `ranking-default.json` | `GET /api/v1/ranking` (1.000.000 ARS, 30 days, limit 50) |
| `ranking-500k-60d.json` | `GET /api/v1/ranking?amount=500000&days=60` |
| `ranking-all.json` | `GET /api/v1/ranking?limit=200` |
| `ranking-kind-<KIND>.json` | `GET /api/v1/ranking?kind=<KIND>&limit=200` for PLAZO_FIJO, WALLET, FUND, CRYPTO |
| `instruments.json` | `GET /api/v1/instruments` |
| `history/<id>.json` | `GET /api/v1/instruments/<id>/history` for every ranked or ineligible id of `ranking-all.json` |
| `simulation-sample.json` | `GET /api/v1/simulations?instrumentId=29&amount=500000&days=60` (SIMULATED) |
| `simulation-ineligible.json` | `GET /api/v1/simulations?instrumentId=29&amount=500000&days=20` (INELIGIBLE, minimum term) |
| `problem-400.json` | `GET /api/v1/ranking?amount=-1` (ProblemDetail) |
| `openapi-pre-t7a.json` | `GET /v3/api-docs` before T7a (superseded) |
| `openapi.json` | `GET /v3/api-docs` after T7a (adds `/api/v1/auth/csrf`); copy it to `web/src/api/openapi.json` and run `npm run api:types` |

History is sparse on purpose: one ingestion day gives one rate point per bank or wallet and two quotes per fund.
To re-record, run the stack with `RINDETUPLATA_INGEST_CRON="0 */2 * * * *"` and repeat the requests above.
