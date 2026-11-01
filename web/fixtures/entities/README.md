# Synthetic entity fixtures (T6b)

Hand-made recordings in the shape of `GET /api/v1/entities/banks` and `GET /api/v1/entities/banks/{slug}`, read by
`web/src/lib/entities.ts` when `WEB_FIXTURES=1`. They are **synthetic**: the endpoints are new, so there is no live
capture to record, and the recorded ranking fixtures are not re-recorded (L16). The bank names and instrument ids come
from `ranking-kind-PLAZO_FIJO.json`, so the links to `/instrumentos/<name>-<id>/` of the banks that have an instrument
page resolve; the rates, the three capture days (2026-10-12..14) and the best-rate counts are invented but consistent
with each other (`bestDays`/`observedDays` follow from the series: 29 is the best on 13 and 14, 12 and 26 tie on 12).

| File | Stands for |
|---|---|
| `banks.json` | `GET /api/v1/entities/banks` (sorted by slug) |
| `banks/<slug>.json` | `GET /api/v1/entities/banks/<slug>` |
| `history/<instrumentId>.json` | `GET /api/v1/instruments/<instrumentId>/history`, what the bank chart plots |

Two banks (`banco-de-galicia-y-buenos-aires-s-a`, `banco-bbva-argentina-s-a`) only publish a client rate: no series, no
history file (the chart shows its empty state) and no instrument page to link. When the real endpoints have been
recorded on a live stack, replace these files with the recordings and keep this table.
