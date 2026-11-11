# MCP server

The API embeds a read-only [Model Context Protocol](https://modelcontextprotocol.io) server, so an AI assistant can
ask the same ranking the site shows. It uses the official MCP Java SDK 2.1.0 (`io.modelcontextprotocol.sdk:mcp-core`
and `mcp-json-jackson3`; no Spring AI) as a stateless Streamable HTTP transport registered as its own servlet
(`ar.rindetuplata.mcp.McpConfiguration`).

## Connect

| | |
|---|---|
| Endpoint | `POST /mcp` of the API host (`https://api.<domain>/mcp` in production, `http://localhost:8080/mcp` locally) |
| Transport | Streamable HTTP, **stateless**: no session, no `Mcp-Session-Id`, nothing kept between two requests |
| Auth | none; public like the read API |
| Other methods | `GET`, `PUT`, `DELETE` and any other path are refused by the security chain |

```bash
curl -s http://localhost:8080/mcp \
  -H 'Content-Type: application/json' -H 'Accept: application/json, text/event-stream' \
  -d '{"jsonrpc":"2.0","id":1,"method":"tools/call","params":{"name":"get_ranking","arguments":{"amount":500000,"days":30,"limit":3}}}'
```

A client that speaks Streamable HTTP (Claude Desktop through `mcp-remote`, the MCP Inspector, the Java SDK client of the
integration tests) only needs the URL.

## Tools

All five are read-only (`readOnlyHint: true`) and read the services the REST API reads. Every result is JSON, sent both as
`structuredContent` and as one text block, and states the **`dataDate`** it rests on and its **`source`** (`sources` for a
ranking). `dataDate` and `source` are `null` only when the answer rests on no datum: an empty history range, no closed
month yet, or a simulation that came out ineligible or excluded.

| Tool | Arguments | Answers |
|---|---|---|
| `get_ranking` | `amount`, `days`, `kind[]`, `clientOf[]`, `asOf`, `limit`, `groupFunds` (all optional) | The ranked page of `GET /api/v1/ranking`: request echo, inflation, dollar MEP, rows (`RankedEntryView`) and `totals`. The ineligible and excluded lists are counted in `totals`, not listed |
| `simulate_instrument` | `instrumentId` (required), `amount`, `days`, `client` | `status` `SIMULATED` with its `entry`, or `INELIGIBLE` / `EXCLUDED` with the reason; same rules as `GET /api/v1/simulations` |
| `get_instrument_history` | `instrumentId` (required), `from`, `to` | The `InstrumentHistory` of `GET /api/v1/instruments/{id}/history` (default last 90 days, at most 366) |
| `list_monthly_reports` | none | The closed months with a report, newest first, with the INDEC inflation of each |
| `get_monthly_report` | `month` (required, `yyyy-MM`) | The "who beat inflation" report of `GET /api/v1/reports/monthly/{month}` |

Rows have the field names and scales of the REST API (money at scale 2, fractions at scale 6, `HALF_EVEN`), so
`docs/monthly-report.md` and the OpenAPI document describe them too. Get ids for the second and third tool from the
`id` of a `get_ranking` row.

### Same parameters and caps as the REST API

`amount` is greater than 0 and at most `RankingQuery.MAX_AMOUNT` (1e12 ARS), `days` is between 1 and
`RankingQuery.MAX_DAYS` (1825), `limit` between 1 and `RankingQuery.MAX_LIMIT` (200), `clientOf` at most
`RankingQuery.MAX_CLIENT_OF` (100) ids (there are fewer than 100 banks in the BCRA list), counted as sent, repeats
included, before the list is copied or echoed; defaults are 1,000,000 ARS, 30
days, 50 rows, every kind, fund classes grouped, today in `America/Argentina/Buenos_Aires`. The bounds are read from the
`RankingQuery` constants, and the input schemas of `tools/list` show them. The REST request classes are package-private,
so `ToolArguments` mirrors their validation; `McpArgumentEquivalenceApiTest` sends every boundary (`days`
0/1/cap/cap+1, `amount` 0/cap/cap+1, `limit` 0/1/200/201, `clientOf` 100/101 ids, an unknown `kind`, a malformed `asOf`, unknown and missing
`instrumentId`) to both and fails if one accepts what the other rejects or if they name a different field.

## Errors

A bad argument is an MCP **tool error** (`isError: true`), which a model can read and retry, never a protocol error and
never a stack trace:

```json
{"error": "invalid_argument", "argument": "days", "message": "Argument 'days': must be between 1 and 1825: 2000000000"}
```

`error` is `invalid_argument` (wrong, missing or unknown argument, a range the service refuses) or `not_found` (an
`instrumentId` no ranked instrument carries, a month that is not closed, has no INDEC figure or no stored history). The
message is also the text block. A failure inside the server is `internal_error` with a fixed message; the detail is only
in the log. An unknown tool name is the SDK's protocol error. Numbers may arrive as JSON numbers or as numeric strings; an
argument a tool does not declare is refused so a typo is never silently ignored.

## Security and limits

- `SecurityConfiguration` opens exactly `POST /mcp` (`permitAll`), exempts it from CSRF and leaves everything else on the
  explicit list ending in `denyAll()`. There is no session: no `SESSION` cookie and no `spring_session` row. The shared
  SPA CSRF handler still hands an `XSRF-TOKEN` cookie to any caller that has none, REST reads included; an MCP client
  ignores it.
- `ApiRateLimitFilter` limits `POST /mcp` with the same per-address token bucket as the read API (`rindetuplata.api.rate-limit.*`),
  keyed by the same remote address and **sharing one bucket** with the REST reads. Every JSON-RPC message is one POST, so a
  client session costs three requests before its first tool call (`initialize`, `notifications/initialized`, the call).
  Over the limit the answer is the 429 `ProblemDetail` with `Retry-After`. The `X-Build-Token` bypass applies as it does
  to the REST reads.
- The transport reads at most 64 KiB of request body (`McpConfiguration.MAX_REQUEST_BYTES`, the SDK's
  `maxRequestSize`; its default is 16 MiB). A larger body is refused by the SDK with `413 Content Too Large` before any
  tool runs (`McpBodyLimitApiTest`). Together with the `clientOf` cap, one request cannot put an unbounded list in the
  ranking cache key or in the query a cached ranking keeps.
- Tools never write, never touch accounts, alerts or curation, and answer from the ranking cache (`PublicRanking`) like
  the REST ranking.

## Deployment

`web/` serves `/llms.txt` ([llmstxt.org](https://llmstxt.org)) naming the site, its sections, the public API, its OpenAPI
document and this endpoint; the API host comes from `PUBLIC_API_URL` (the site origin when empty).

Every Caddyfile (`deploy/Caddyfile.vps`, `.local`, `.preview`) proxies `/api/*`, `/v3/api-docs` and `/mcp` to the API,
the three paths `llms.txt` names; `deploy/test/caddy-routes-test.sh` adapts each one with the stacks' `caddy:2-alpine`
image and fails when the proxied set differs.

## Tests

`ar.rindetuplata.mcp.*` drives the server through the SDK's own client over real HTTP (`RANDOM_PORT`): `initialize`,
`tools/list`, each tool, bad arguments, the stateless surface and rate limiting (`McpRateLimitApiTest`), and the
equivalence with the REST endpoints. `ApiRateLimitFilterTest` pins the rate-limited list; `web/test/llms.test.ts` the
`llms.txt` format and links.
