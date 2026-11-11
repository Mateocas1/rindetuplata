# API keys and plan limits

A signed-in user can create API keys to call the public read API with limits of their own instead of the shared
per-address bucket. A key never signs anyone in: it only selects the limits.

## Using a key

```bash
curl -H 'X-Api-Key: rtp_…' 'https://<api-host>/api/v1/ranking?amount=100000&days=30'
```

`X-Api-Key` is read only on `GET /api/v1/ranking`, `/api/v1/simulations`, `/api/v1/instruments[/**]`,
`/api/v1/entities[/**]` and `/api/v1/reports[/**]` (the same paths as the per-address limit, `ApiRateLimitFilter`). On
any other path the header is ignored: `/api/v1/me`, alerts, curation, admin and the key endpoints answer a request with
a key exactly as they answer an anonymous one (401).

| Case | Answer |
|---|---|
| no `X-Api-Key` | the per-address bucket of the anonymous plan (`rindetuplata.api.rate-limit.*`, 120 and 2 per second) |
| valid key | served; limited by the bucket of its plan, per key, and by the daily quota of its plan |
| unknown or revoked key, a malformed key, or an empty header | 401 `ProblemDetail` (`detail`: "The API key is unknown or has been revoked"); never a fall back to the address bucket, but each one costs one token of the per-address bucket, so once that is spent the answer is the address 429 (see below) |
| valid key over its bucket | 429 `ProblemDetail` (`detail`: "Too many requests for this API key; try again later") with `Retry-After` in seconds |
| valid key over its daily quota | 429 `ProblemDetail` (`detail` names the quota) with `Retry-After` in seconds to midnight in `America/Argentina/Buenos_Aires` |

A request the bucket refuses does not count towards the quota.

**Invented keys are bounded by the address.** Whether a key is valid is only known after a Postgres lookup, so every
keyed request first peeks at the address bucket (takes nothing): if the address has spent it, the answer is the
address 429 (`Retry-After` in seconds) and no lookup is made. Otherwise the key is looked up; a valid one is never
charged to the address, a key that is not valid takes one token of it. A flood of well-formed random keys from one
address therefore ends in 429s after `rindetuplata.api.rate-limit.capacity` attempts and stops reading the database.
Trade-off: while an address has spent its bucket, even a valid key from it gets the address 429 until a token is back
(2 per second by default), because telling it apart would need the lookup the rule avoids. `X-Build-Token` (the static site build) still skips every
limit and wins over a key.

## Plans

| Plan | Who | Bucket | Refill | Daily quota |
|---|---|---|---|---|
| anonymous | no key | 120 per address | 2 per second | none |
| `FREE` | every key a user creates | 600 per key | 10 per second | 20000 |
| `PARTNER` | a key the operator promotes in SQL | 3000 per key | 50 per second | 500000 |

The numbers are configuration, `rindetuplata.api.plans.{free,partner}.{capacity,refill-per-second,daily-quota}`
(`application.properties`). Buckets and quotas are in memory, so they assume the single API instance: a restart
resets them. Promoting a key is one SQL statement, documented in [`deploy.md`](deploy.md) ("API keys and plans").

## Managing keys (signed-in user)

Under `/api/v1/account/api-keys`, with the session cookie and, for `POST` and `DELETE`, the CSRF token (the same rules as
`/api/v1/alerts`). Anonymous callers and callers that only send a key get a 401.

| Verb | Path | Answer |
|---|---|---|
| `POST` | `/api/v1/account/api-keys` | 201 with `id`, `name`, `prefix`, `plan`, `createdAt` and `key`, the only time the key is returned. Optional body `{"name": "…"}` (at most 60 characters). A fourth active key is a 400 |
| `GET` | `/api/v1/account/api-keys` | the caller's active keys: `id`, `name`, `prefix`, `plan`, `createdAt` |
| `DELETE` | `/api/v1/account/api-keys/{id}` | 204; an unknown id, somebody else's key and an already revoked key are the same 404 |

A user holds at most 3 active keys; revoking one frees a slot. Revocation is effective on the next request: every keyed
request looks the key up in Postgres, there is no cache to expire.

The web page `/claves-api/` (signed-in, `noindex`) does the three calls: it shows the new key once with a copy button,
lists the active keys by prefix and revokes with a confirmation.

## What is stored

A key is `rtp_` plus 32 random bytes in unpadded base64url (47 characters, 256 bits). Table `api_key` (migration V10)
keeps `key_hash` (lower-case hex SHA-256 of the whole key, unique), `key_prefix` (the first ten characters, for the list),
the optional `name`, `plan`, `created_at` and `revoked_at`. The plaintext is never stored or logged and cannot be
recovered: a lost key is revoked and replaced. With 256 bits of entropy a plain unsalted SHA-256 is enough, there is
nothing to brute-force. A revoked key keeps its row and is not listed.
