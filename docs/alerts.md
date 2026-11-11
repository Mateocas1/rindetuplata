# Alert rules

A signed-in user owns up to **20 active rules**, whatever their type. `POST /api/v1/alerts` takes a `type`; a request
without one is a `THRESHOLD` rule, as before V9, and every rule stored before V9 became `THRESHOLD`. `GET /api/v1/alerts`
and `DELETE /api/v1/alerts/{id}` are unchanged apart from the new fields (`type`, and a `thresholdPercent` that is `null`
for the types that have none). Anything a type does not take, or a missing required field, is a 400 `ProblemDetail`
whose `parameter` names the field; the stored rules stay as they were.

| `type` | Fields | Fires when |
|---|---|---|
| `THRESHOLD` (default) | `thresholdPercent` required (> 0, at most 300); `kind`, `instrumentId` optional | an entry of its filter goes above the TNA threshold (edge-triggered: it re-arms when the entry drops below) |
| `BEST_CHANGED` | `kind` optional; no `instrumentId`, no `thresholdPercent` | the best entry of its filter differs from the best stored at the previous evaluation |
| `RATE_DROP` | `instrumentId` required, a plazo fijo of a bank or a wallet; `kind` optional but, if sent, must be the instrument's (`PLAZO_FIJO` or `WALLET`, filled in when omitted); no `thresholdPercent` | the TNA the ranking used for it is strictly below the one stored at the previous evaluation |
| `WEEKLY_DIGEST` | `kind` optional; no `instrumentId`, no `thresholdPercent` | the first evaluation whose data date falls in an ISO week (Monday start) without a digest of that rule |

An FCI-backed wallet is a fund class in the store, so `RATE_DROP` refuses it (`instrumentId` 400).

## Evaluation

After a `COMPLETED` `dailyIngestionJob` the evaluation ranks the job's data date once with the alerts' defaults
($1.000.000, 30 days, non-client, every kind, no page cap) and hands that ranking to every active rule by type. A failing
rule is logged and never stops the others.

- **BEST_CHANGED.** The first evaluation stores the baseline and sends nothing. The "best" is the first entry of the
  rule's filter in ranking order, with the classes of one fund folded as the public ranking groups them, so a change of
  class inside one fund is not a change (`alert_state.subject_key` is `fund:<normalized fund name>`; anything else is
  `id:<instrument>`). A day on which the filter ranks nothing changes nothing. Evaluating the same data again sends nothing.
- **RATE_DROP.** The first evaluation stores the rate. The stored rate moves on every evaluation that ranks the
  instrument, so a rise lowers the bar and each new drop below the last seen rate fires once. A day with the instrument
  unranked changes nothing, state included.
- **WEEKLY_DIGEST.** The row in `alert_digest` (primary key rule + week Monday) and the outbox event are written in one
  transaction, so a re-run of the week conflicts on the key and sends nothing. The data date is already an Argentine
  calendar date. The digest lists the top 5 of the filter in ranking order (name, TNA, real return over 30 days, source,
  capture date) and a line when its top differs from the previous digest's. A filter that ranks nothing sends nothing and
  leaves the week free for a later evaluation.

## Outbox payloads and email

Every event is one `outbox_event` row written with the state change, relayed to the same exchange, queue and dead-letter
queue as before. `event_type` is `AlertTriggered`, `BestChanged`, `RateDropped` or `WeeklyDigest`; the JSON payload of the
new types carries `"type": "BEST_CHANGED" | "RATE_DROP" | "WEEKLY_DIGEST"`. The consumer reads `type`: a payload **without
one is today's `AlertTriggered` shape and is delivered as today's email** (a row already queued or stored when this shipped
keeps working); an unknown type is rejected without requeue and ends in the dead-letter queue. One composer per type
(`AlertEmailComposer`, `BestChangedEmailComposer`, `RateDropEmailComposer`, `WeeklyDigestEmailComposer`) writes the Spanish
plain-text email. The recorded payloads are in `src/test/resources/fixtures/alert/`.

## Schema (V9)

`alert_rule.type` (default `THRESHOLD`), a nullable `threshold_rate`, a check tying each type's fields together,
`alert_state` (one row per BEST_CHANGED/RATE_DROP rule) and `alert_digest` (one row per digest sent). Deleting a rule
cascades to its state and digests.
