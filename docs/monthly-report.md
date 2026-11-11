# Monthly "who beat inflation" report

`GET /api/v1/reports/monthly` lists the months that have a report (newest first) and
`GET /api/v1/reports/monthly/{yyyy-MM}` returns one. The static site renders `/informes/` and `/informes/<yyyy-mm>/`.

## When a month has a report

All three must hold, otherwise the month is a 404 `ProblemDetail` (`parameter: month`) and is absent from the list:

1. the month has ended (the Argentine day of the injected clock is after its last day);
2. INDEC's monthly inflation (BCRA variable 27) is stored for it;
3. at least one instrument can be measured over the whole month from the stored history.

The history starts on 2026-10-14, so October 2026 cannot be reported (nothing was captured on its first days); the first
full month is November 2026, and its report appears once INDEC publishes November's inflation (mid-December). A malformed
month (`2026-9`, `2026-13`, `septiembre`) is a 400.

## How each nominal return is measured

| Kind | Rule | Disqualified when |
|---|---|---|
| Wallet (`WALLET`, base TNA of the first tier) | Every calendar day earns `TNA / 365` of the latest valid capture on or before that day; the month is the product of `1 + daily` minus one | a day has no capture within 3 days (`NOT_CAPTURED_AT_START` for the first day, `GAP_IN_CAPTURES` after it) |
| Crypto (`CRYPTO_YIELD`, ARS only) | Same, with the daily rate `(1 + APY)^(1/365) - 1` | same |
| Plazo fijo (`PLAZO_FIJO_BANK`, non-client TNA) | The first capture of the month, dated on or before the first Monday-to-Friday day, is locked as one simple term: `TNA * days / 365` | no capture by then (`NOT_CAPTURED_AT_START`); no non-client rate (`NOT_OFFERED_TO_NON_CLIENTS`) |
| Fund class (`FUND_CLASS`) | `vcp(last quote <= month end) / vcp(last quote <= previous month end) - 1`; each quote at most 4 days before its end | `MISSING_BASE_QUOTE`, `MISSING_END_QUOTE` |

Real return is `(1 + nominal) / (1 + INDEC_M) - 1`. Nominal and real are rounded half-even to six decimals and
`beatInflation` is true when the *published* real return is above zero. Classes of one fund are grouped to the best one
(`foldedClasses` counts the others); an FCI-backed wallet is one `WALLET` row and its class is not listed again; classes in
dollars (`FOREIGN_CURRENCY`) or of Ley 27.743 (`RESTRICTED_CLASS`) are left out with their reason, exactly as the ranking
decides (`FundClassRules`). Invalid snapshots and quotes are never used (`INVALID_DATA` when nothing else is left).

Known limits: holidays are not modelled (the first business day is the first weekday); a wallet row uses its base TNA, so a
balance above the wallet's cap earns less than the row shows; conditional wallet rates are ignored.

## Fixtures

`web/fixtures/reports/` is synthetic (see its README); the empty state is built from `WEB_REPORTS_FIXTURES_DIR`.
