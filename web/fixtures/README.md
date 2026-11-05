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
| `openapi-pre-t7a.json` | `GET /v3/api-docs` before the CSRF endpoint (superseded) |
| `openapi.json` | `GET /v3/api-docs` after the CSRF endpoint (adds `/api/v1/auth/csrf`); `npm run api:types` generates `web/src/api/schema.d.ts` from it |

History is sparse on purpose: one ingestion day gives one rate point per bank or wallet and two quotes per fund.
To re-record, run the stack with `RINDETUPLATA_INGEST_CRON="0 */2 * * * *"` and repeat the requests above.

`realEarned` (pesos above or below the expected inflation over the horizon, UI clarity decision D4) is derived, not
recorded: the recordings predate the API field, so `node scripts/derive-real-earned.mjs` adds it to every ranked entry of
`ranking-*.json` and to `simulation-sample.json` with the API's formula, `finalAmount / (1 + inflation.overHorizon) -
request.amount`, in exact decimal arithmetic (BigInt, no float division) rounded half-even to cents, and `null` exactly
when `realReturn` is `null`. `simulation-sample.json` has no `request` or `inflation`, so the script reads them from
`ranking-500k-60d.json`, the recording of the same request. The script edits the files as text and is idempotent; the
inputs are the rounded JSON values, so a derived cent can differ from the API's by one. Re-run it after re-recording.
