# rindetuplata MVP

Argentine yield comparator ("¿Dónde rinde tu plata hoy?"): flagship portfolio project in Java/Spring Boot.
Owner: Mateo. Built with AI under ODD. Read every section until `## Log` before working on a task.

## Specs

- **S1 Scale and stack.** "algo mas grande, tipo como ofertasuper, pero con este stack" — the stack is Java + Spring Boot ("me interesa que crees lo necesario...", L4; Java/Spring chosen in L2: "en su momento note que se pide mucho java/spring. me gusta sumarlo").
- **S2 Argentine present.** "que demuestre entender el presente argentino tambien, que demuestre entender el problema y solucionarlo".
- **S3 Product (accepted proposal, L3).** Verbatim from the accepted proposal:
  - "Un comparador diario de rendimientos (plazo fijo por banco, billeteras remuneradas, fondos, dólar), con el **rendimiento real**: cuánto le ganás o le perdés a la inflación."
  - "Un **histórico**: cómo rindió cada opción en los últimos meses."
  - "Un **simulador**: \"tengo $500.000 por 60 días, ¿qué me conviene?\"."
  - "**Alertas**: \"avisame si alguna billetera pasa del X% anual\"."
- **S4 Technical core (accepted proposal, L3).** "**Ingesta diaria** con Spring Batch desde fuentes **oficiales**", "**Cálculo financiero serio**: TNA contra TEA, interés compuesto, `BigDecimal`, bien testeado", "**Eventos** para las alertas: RabbitMQ y el patrón outbox", "**Seguridad** para los usuarios y sus alertas".
- **S5 Source resilience (accepted, L4).** "Hay que diseñar un *adaptador por fuente*, para poder cambiar a la fuente primaria (BCRA, la cámara de fondos, el INDEC) si alguna falla." Plazo fijo history is built by our own daily ingestion ("El histórico lo construís vos con la ingesta diaria, igual que en ofertaSUPER").
- **S6 Built with AI.** "creo que lo haria con ia".
- **S7 Session-ready repo.** "me interesa que crees lo necesario para poder ir directamente a crear una sesion directamente en ese dir y avanzar desde ahi".

## Tasks

| ID | Specs | Task | Route | Status | Commit |
|---|---|---|---|---|---|
| T1 | S7, S1 | Bootstrap: Spring Boot 4.1.1 / Java 21 skeleton (web, validation, JPA, PostgreSQL, Flyway, actuator, Testcontainers), compose.yaml, AGENTS.md, this document | inline | done | dc8ede2 |
| T2 | S3, S4 | Domain math: `Money`/rate value objects on `BigDecimal`, TNA→TEA, compound interest, real return vs monthly inflation, simulator for amount + days; exhaustive unit tests incl. rounding and day-count conventions | worker | pending | |
| T3 | S3, S5 | Source adapters behind one port per data kind: plazo fijo by bank, money-market FCI cuotapartes, crypto/fintech yields, monthly inflation, dólar MEP, BCRA variables; recorded JSON fixtures, contract tests, no live calls in tests | worker | pending | |
| T4 | S1, S3, S4, S5 | Persistence + daily ingestion: Flyway schema for instruments and daily snapshots, idempotent Spring Batch (or scheduled) jobs that accumulate history, FCI yield derived from cuotaparte deltas | worker | pending | |
| T5 | S3, S2 | Public REST API: today's ranking with real return, history per instrument, simulator endpoint; OpenAPI docs; Testcontainers integration tests | worker | pending | |
| T6 | S3, S4 | Users and alerts: Spring Security, alert rules, transactional outbox + RabbitMQ, notifier (Telegram first) | worker | pending | |
| T7 | S1, S2 | Public minimal frontend + deploy (decisions D2, D3 first) | worker | blocked: D2, D3 | |
| T8 | S1, S2 | README with live numbers and architecture, CV/portfolio line | inline | pending | |

### Open decisions (ask Mateo before the task that needs them)

- **D1** Bank-account wallets (Ualá, Naranja X, Brubank) are in no verified source: curated manual table, another source, or out of MVP? Needed by T3/T5.
- **D2** Hosting for the public site and API (free tier vs AWS like ShelfOps). Needed by T7.
- **D3** Frontend technology (Next.js reuse vs server-rendered Thymeleaf vs none for MVP). Needed by T7.
- **D4** Spring Batch vs plain `@Scheduled` jobs for ingestion. Needed by T4; S4 names Spring Batch.

## Verified data sources (2026-10-14, live)

| Data | Endpoint | Notes |
|---|---|---|
| Plazo fijo by bank | `https://api.argentinadatos.com/v1/finanzas/tasas/plazoFijo` | 32 institutions, `tnaClientes`, `tnaNoClientes`, tiers by term/amount; current snapshot only, no history |
| Money-market FCI | `https://api.argentinadatos.com/v1/finanzas/fci/mercadoDinero/ultimo` (also `/penultimo`, date-based) | 621 classes, daily `vcp`/`ccp`/`patrimonio`; dates lag (latest 2026-10-09); includes "Mercado Fondo" (Mercado Pago) |
| Crypto/fintech yields | `https://api.argentinadatos.com/v1/finanzas/rendimientos` | 13 platforms (lemoncash, belo, ripio, fiwind, ...), APY per currency |
| Monthly inflation | `https://api.argentinadatos.com/v1/finanzas/indices/inflacion` | INDEC series since 1943; 2026-09 = 1.9 |
| Dólar MEP | `https://dolarapi.com/v1/dolares/bolsa` | buy/sell + timestamp |
| BCRA official variables | `https://api.bcra.gob.ar/estadisticas/v4.0/monetarias` | BADLAR (id 7), TAMAR (44/45), deposits; primary source |

ArgentinaDatos is a community aggregator: keep one adapter per source so a primary source (BCRA, CAFCI, INDEC) can replace it.

## Log

- **L1** (user, original request): "si. me gusta eso. de todas formas creo que lo haria con ia. digamos y gracias por la intencion de ayudarme a entender y demas, pero , tengo otro pensamiento respecto a eso. de todas formas gracias, pero me interesa... ver que proyecto top podriamos hacer? es decir, algo mas grande, tipo como ofertasuper, pero con este stack"
- **L2** (user, earlier context): "si, me re sirve, de hecho, en su momento note que se pide mucho java/spring. me gusta sumarlo, que podemos hacer?" and "me interesa igual, que si bien ataque problemas reales de forma rica, que demuestre entender el presente argentino tambien, que demuestre entender el problema y solucionarlo"
- **L3** (user): "siii. me encanta la 1, vamos por esa!" — accepts idea 1, the yield comparator, as proposed.
- **L4** (user): "si rey, vamos con eso, me interesa que crees lo necesario para poder ir directamente a crear una sesion directamente en ese dir y avanzar desde ahi" — accepts the name `rindetuplata` and the adapter-per-source approach.
- **L5** (evidence, 2026-10-14): data sources above verified live. Best plazo fijo TNA ~23% vs September inflation 1.9% monthly (~25% annualized): real return about zero, a product hook.
- **L6** (T1): skeleton generated from start.spring.io (Boot 4.1.1, Java 21, Maven wrapper); Testcontainers image pinned to `postgres:18-alpine`; `./mvnw test` passed (1 test, context loads against a real Postgres container, 18 s). Commit dc8ede2.
