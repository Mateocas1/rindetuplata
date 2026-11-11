# Synthetic monthly report fixtures

These files are **not recorded**: they are hand-made, with obviously fictional names, so builds with `WEB_FIXTURES=1`
(tests and CI) can render the monthly report pages without a running API. The recorded fixtures of `web/fixtures/` start on
2026-10-14 and cannot cover a whole month (they stay as recorded).

| File | Shape of |
|---|---|
| `index.json` | `GET /api/v1/reports/monthly` |
| `<yyyy-MM>.json` | `GET /api/v1/reports/monthly/<yyyy-MM>` |

`2026-10.json` is internally consistent (a test checks it): rows sorted by real return, `real = (1 + nominal) / (1 + inflation) - 1`,
`beatInflation` true exactly when `real > 0`, and `totals` that match the rows. Setting `WEB_REPORTS_FIXTURES_DIR` to another
directory with the same layout replaces this one (the empty-state build test points it at an empty month list).
