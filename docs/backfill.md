# History backfill (T4a, S4, L33 a)

`historyBackfillJob(from, to)` fills the stored history of the days `from..to` (both inclusive) from the public sources
that keep one, so a fresh deploy does not have to wait weeks for the 7-day fund yield, the MEP and the monthly report.
It is a Spring Batch job started by an admin-only endpoint; it never runs by itself.

## Starting a run

```bash
# Log in on the site as an admin first, then copy the SESSION and XSRF-TOKEN cookies from the browser.
curl -sS -X POST "https://api.rindetuplata.ar/api/v1/admin/backfill?from=2026-10-01&to=2026-10-31" \
  -H "Cookie: SESSION=<session>; XSRF-TOKEN=<token>" -H "X-XSRF-TOKEN: <token>"
# 202 {"jobId":42,"from":"2026-10-01","to":"2026-10-31"}
```

| Answer | When |
|---|---|
| `202` + `{"jobId", "from", "to"}` | the run started; `jobId` is the Spring Batch job execution id (table `batch_job_execution`) |
| `409` `ProblemDetail` | a run is still going; one at a time per API instance (the guard is in memory and dies with the process) |
| `400` `ProblemDetail`, `parameter: from` | `from` is after `to`, or missing, or not an ISO date |
| `400` `ProblemDetail`, `parameter: to` | `to` is after yesterday (the Argentine day), the range covers more than 366 days, or `to` is missing or malformed |
| `401` / `403` | not signed in / not `ROLE_ADMIN` / no CSRF token. The path sits under `/api/v1/admin/**`, never public |

A rejected request starts nothing and writes nothing. Watch a run with
`select step_name, status from batch_step_execution where job_execution_id = 42` and
`select * from source_run where mode = 'BACKFILL' order by recorded_at desc`.

## What it reads

| Kind | Source | Call | Shape of the run |
|---|---|---|---|
| FCI money-market classes | ArgentinaDatos | `GET /v1/finanzas/fci/mercadoDinero/yyyy/MM/dd` | one call per day, waiting `rindetuplata.backfill.delay` (default 500 ms, `RINDETUPLATA_BACKFILL_DELAY`) between calls; one transaction per day and a cursor in the step's execution context, so a restart resumes at the next day |
| Dólar MEP | ArgentinaDatos, source id `argentinadatos-dolar-bolsa` | `GET /v1/cotizaciones/dolares/bolsa` | the whole series (one row per calendar day) once per run, only the days of the range are stored |
| BCRA macro (INDEC monthly inflation 27, REM 29, PSP benchmark 1196) | BCRA statistics v4 | `GET /monetarias/{id}?desde=yyyy-MM-dd&hasta=yyyy-MM-dd&limit=3000` | one call per variable for the whole range |

Probed live on 2026-11-01, answers recorded under `src/test/resources/fixtures/`:

- **FCI by date.** A business day answers 200 with every class of that date in the same row shape as `/ultimo` (354
  classes for 2026-10-01; fixture `argentinadatos/fci-mercado-dinero-2026-10-01-excerpt.json`, 10 verbatim rows). A day
  without quotes (2026-10-03, a Saturday) answers **404 `{"error":"Not found"}`**
  (`argentinadatos/fci-mercado-dinero-2026-10-03-not-found.json`): the adapter reads it as "no quotes that day", not as a
  failure of the source. Staleness is measured against the requested day, never against the fetch date, so backfilled
  classes are not all flagged `STALE`.
- **MEP series.** 2,926 rows from 2018-10-29 to 2026-11-01, `{casa:"bolsa", compra, venta, fecha}`; weekends repeat the
  Friday price (fixture `argentinadatos/dolares-bolsa-serie-2026-09-28-2026-10-06-excerpt.json`, 9 verbatim rows).
- **BCRA takes a date range.** `desde` and `hasta` are inclusive (`/monetarias/27?desde=2025-01-01&hasta=2025-06-30`
  answers the six month ends, fixture `bcra/monetarias-27-range-2025-01-01-2025-06-30.json`). `limit` accepts at most
  **3000** (3001 is a 400 `El límite no puede superar los 3000 registros.`), far above the 366 days of a run, so the
  macro step needs no paging. An inverted range is a 400 `Fecha desde no debe ser mayor a la fecha hasta.` (fixture
  `bcra/monetarias-range-inverted.json`), surfaced as the typed `BcraApiException`. INDEC inflation is BCRA variable 27,
  so no other INDEC call is needed.

## What it writes

- **Insert-only (live data wins).** Every write is `insert ... on conflict do nothing`: a fund quote, a MEP quote or a
  macro observation that is already stored is never touched, and a rerun of a range changes nothing. The MEP series is
  stored under its own source id, so it never collides with the daily `dolarapi-bolsa` rows, and the MEP reader takes the
  latest quote at or before the requested day whatever the source, **preferring DolarApi on the same date**.
- **One `source_run` row per source and day, mode `BACKFILL`.** `rows` is what this run inserted (0 when the day was
  already stored), a day the source has nothing for is `OK` with 0 rows and a `failure` text saying so, and a failing
  source is `FAILED` (macro: `DEGRADED` when only some variables failed). A backfill row replaces an earlier backfill
  row (a rerun after a failure) and never a `LIVE` or `REPLAY` row, so what the daily job recorded stays as it was.
  These rows are why the ranking cache and the monthly report cache see the new history: the data version counts and
  dates `source_run`. Because a backfill row never replaces a live one, a run that only fills gaps under days the daily
  job already recorded (say, a day its source was down) would leave every row as it was; so each run also records one
  summary row, dated `to`, under the pseudo source `history-backfill` (`rows` = everything the run inserted, `failure` =
  the range), which keeps the version moving.
- **No alert and no rebuild listener.** The job has none: alerts are about today (the daily job's listener now
  evaluates only when its data date is today in `America/Argentina/Buenos_Aires`, so a replay or a late run of a past date
  never compares old data with newer alert state), and the static site is not rebuilt. After a large backfill, trigger the
  site's rebuild hook by hand (see `docs/deploy.md`).

## Reading it back

- `GET /api/v1/reports/monthly` rebuilds each month from the stored history, which a backfill makes years long. The
  service now answers from a bounded in-process LRU (`rindetuplata.report.cache-max-entries`, default 24): the month list
  is keyed by the data version and today's date, a month's report by the data version and the month. A failure is never
  cached.
- Migration `V11` adds `rate_snapshot_capture_date_idx` and `fund_quote_quote_date_idx`: the primary keys of both tables
  lead with the instrument, so `min(date)` (the report's earliest-history lookup) and any date predicate scanned the
  tables. Indexes only; no data changes.

## Limits

- One run at a time, at most 366 days, `to` no later than yesterday. For more history, run consecutive ranges.
- A year of fund classes is on the order of 100,000 rows, written one transaction per day.
- The 404-as-empty rule hides a source that starts answering 404 for every date: check `source_run` for runs of 0 rows.
- The run is in the API process: restarting the API stops it. Run it again; stored days are skipped.
