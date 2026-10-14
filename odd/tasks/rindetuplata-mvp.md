# rindetuplata MVP

Argentine yield comparator ("¿Dónde rinde tu plata hoy?"): flagship portfolio project in Java/Spring Boot, built to become a real product.
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
- **S8 Portfolio first, real product ready (L8).** "si, la idea inicial es que es de portfolio, pero de todas formas, me interesa mucho que pueda llegar a ser un producto real con usuarios reales. por tanto, l-f y l-g son importantes". L-F = rate fine print (caps, tiers, conditions, client vs non-client, minimum terms, taxes) modeled in the domain and respected by the simulator. L-G = informational, non-advice language and a disclaimer.
- **S9 Target user (L9).** "si obvio, supongo que primero deberiamos trabajar sobre a para luego hacer b, pero si me interesa llegar a b". (a) everyday saver: plazo fijo, remunerated wallets, money-market funds; MVP. (b) light investor: LECAP/BONCAP, cauciones, dólar MEP; second phase. The domain and source ports must accept market-priced instruments later without rewrites.
- **S10 Real return uses two inflation indices (L12).** "no, usar las 2, es lo correcto, que el usuario comun 'se adapte' aunque tambien debemos hacer una ux/ui friendly para este tipo de usuarios." Expected inflation (BCRA REM, variable 29) for today's ranking and the simulator; past INDEC inflation (variable 27) for history; the UI always labels which index is used and uses progressive disclosure (plain-language headline first, TNA/TEA/index detail on demand). REM's 12-month figure is converted to the investment horizon by compounding, never divided linearly.
- **S11 Neutral product (L13).** "realmente no lo tengo pensado con criterio, pero me suena bien y correcta la opcion a" — option (a): no money from ranked entities (no affiliate links); ranking objective and explainable, with source and data date per row.
- **S12 Wallet rates without an API (D1, L10, L14, L16).** Hybrid accepted ("hibrido estaria bien"), owner as curator ("dale, vamos con tu recomendacion."): admin panel to load/confirm rates; watcher scrapers where the entity web allows (Ualá yes, Naranja X blocks automation); warnings when a rate is unverified for N days or changed; BCRA variable 1196 (average PSP remunerated-deposit TNA) as benchmark; later a moderated user "report rate" button. Every rate carries a confidence level (official-automatic, scraped-from-entity-web, curated-manual with verified-at date); stale data is shown flagged, never as current. FCI-backed wallets (Mercado Pago, Personal Pay, Prex, Cocos, Astropay) are derived from FCI data.
- **S13 Alerts channel (L14).** "si , la c es la mejor" — web account with passwordless magic-link login and email alerts in the MVP; Telegram later as a linkable channel.
- **S14 Frontend and SEO (L17, L19).** "el seo va a ser uno de nuestros principales objetivos, ademas de que es un gran indicador para nosotros como para el perfil/cv . me interesa si, seguir lasmejores practicas de seo y demas" and "es importante tambien el ux/ui. tenemos que saber como mostrar los datos, grafos si hay, etc." Frontend: "si, vamos con astro + react" — Astro with React islands; verify current versions (Context7) before T7. All financial logic lives in the Java API.
- **S15 Personalized ranking (L18).** "si, personalizado, con TU monto y TU plazo, totalmente personalizado". Ranking sorted by pesos earned for the user's amount and term (caps, tiers and minimum terms applied); default $1.000.000 for 30 days; real-return badge; TNA secondary.
- **S16 Hosting (L20).** "se, esta bien vps propio. me cierran los 5-8 usd, de todas formas, me interesaria poder probarlo local quizas al inicio, algo como en docker quizas?" Own VPS (USD 5–8/month) with Docker Compose at launch; local Docker Compose until then; the same Compose file runs in both; Astro static site on a CDN.
- **S17 Daily raw capture from day one (L21).** "se, es una buena opcion ir por publico, esta bueno hacerlo opensource, why not? por otro lado, super lo de github actions, es algo como lo que hicimos con ofertasuper, perfecto! vamos con eso!" Accepted proposal: "un cron diario en GitHub Actions (gratis) que solo descargue las respuestas de las fuentes no recuperables y las guarde como archivos JSON fechados en un repositorio aparte", public, replayed later by the idempotent Spring ingestion. Location (L22, "dale, vamos con b"): the main repo `Mateocas1/rindetuplata` is published as public now; the workflow lives on `main` (scheduled workflows only run from the default branch) and commits captured data to an orphan `data` branch.

## Tasks

Order of work: T9 first (history clock), then T2 onward.

| ID | Specs | Task | Route | Status | Commit |
|---|---|---|---|---|---|
| T1 | S7, S1 | Bootstrap: Spring Boot 4.1.1 / Java 21 skeleton (web, validation, JPA, PostgreSQL, Flyway, actuator, Testcontainers), compose.yaml, AGENTS.md, this document | inline | done | dc8ede2 |
| T9 | S17, S5, S12 | Daily raw capture: public `Mateocas1/rindetuplata` repo with a scheduled Actions workflow that archives dated raw responses of non-recoverable sources (BCRA transparency PlazosFijos, ArgentinaDatos plazoFijo and rendimientos, Ualá rate extract) plus a per-day manifest; independent failure per source; deterministic test with fixtures | inline | done | f475ac7 |
| T2 | S3, S4, S8, S10, S15 | Domain math: `Money`/rate value objects on `BigDecimal`, TNA→TEA, compound interest, daily-accrual accounts, caps/tiers/conditions, REM horizon conversion, real return, simulation-based personalized ranking; exhaustive unit tests incl. rounding and day-count conventions | worker | pending | |
| T3 | S3, S5, S9, S12 | Source adapters behind one port per data kind: plazo fijo (BCRA transparency primary, ArgentinaDatos secondary), money-market FCI cuotapartes, crypto/fintech yields, inflation (BCRA 27) and REM (29), PSP benchmark (1196), dólar MEP, wallet rates (scraper where possible + curated); validation layer that flags bad rows; confidence level per rate; recorded fixtures, no live calls in tests | worker | pending | |
| T4 | S1, S3, S4, S5, S17 | Persistence + daily ingestion: Flyway schema for instruments and daily snapshots, idempotent jobs that accumulate history, replay of the T9 raw archive, FCI yield from cuotaparte deltas, trigger static-site rebuild after ingestion | worker | pending | |
| T5 | S3, S2, S10, S15 | Public REST API: personalized ranking (amount + days) with real return, history per instrument, simulator; OpenAPI docs; Testcontainers integration tests | worker | pending | |
| T6 | S3, S4, S12, S13 | Users and alerts: Spring Security with magic-link login, admin role and curation panel API, alert rules, transactional outbox + RabbitMQ, email notifier first, Telegram later | worker | pending | |
| T7 | S1, S2, S14, S16 | Astro + React frontend (SEO pages, charts, simulator island) and deploy: full Docker Compose stack, VPS at launch | worker | pending | |
| T8 | S1, S2, S8, S11 | README with live numbers and architecture, disclaimer, CV/portfolio line | inline | pending | |

### Decisions

- **D1** Wallets without a source → resolved: S12.
- **D2** Hosting → resolved: S16.
- **D3** Frontend → resolved: S14 (Astro + React; replaces the earlier Next.js choice in the same session).
- **D4** Spring Batch vs plain `@Scheduled` jobs for ingestion. Needed by T4; S4 names Spring Batch. Open.

### Known limits and mitigations

| # | Limit | Mitigation |
|---|---|---|
| L-A | Monthly inflation is lagged | REM expected inflation for decisions (S10) |
| L-B | No per-bank plazo fijo or wallet history at the sources | T9 raw capture from day one; FCI, inflation and BCRA series are recoverable later |
| L-C | Wallet account rates in no source, official included | S12 |
| L-D | Community aggregator dependency | Adapter per source; BCRA transparency as primary for plazo fijo |
| L-E | Sources lag and mix dates (FCI, BCRA transparency rows) | Show data date per row; validation layer |
| L-F | Fine print (caps, tiers, conditions, minimum terms) | Modeled in the domain (T2), visual in UI |
| L-G | Perceived financial advice | Neutral product (S11), disclaimer, informational wording |
| L-H | Argentine government APIs may block foreign IPs | Not blocked from GitHub's US runners (L23); re-check from the VPS region before renting |

## Verified data sources (2026-10-14, live)

| Data | Endpoint | Notes |
|---|---|---|
| Plazo fijo by entity (official) | `https://api.bcra.gob.ar/transparencia/v1.0/PlazosFijos` | 914 products, 73 entities (incl. Ualá Bank 384, Brubank, Naranja Digital 45030); `tasaEfectivaAnualMinima` (TEA), minimum amount, minimum days, channel, `fechaInformacion` per row (mixed Jun–Oct); some minimum-days values look wrong (1–5 days for traditional); strict TLS ok |
| Savings accounts (official) | `https://api.bcra.gob.ar/transparencia/v1.0/CajasAhorros` | No rate field: useless for remunerated accounts |
| Plazo fijo by bank | `https://api.argentinadatos.com/v1/finanzas/tasas/plazoFijo` | 32 institutions, `tnaClientes`, `tnaNoClientes`; current snapshot only, no history |
| Money-market FCI | `https://api.argentinadatos.com/v1/finanzas/fci/mercadoDinero/ultimo` (also `/penultimo`, date-based) | 621 classes, daily `vcp`/`ccp`/`patrimonio`; dates lag; includes "Mercado Fondo" (Mercado Pago) |
| Crypto/fintech yields | `https://api.argentinadatos.com/v1/finanzas/rendimientos` | 13 platforms (nexo, fiwind, letsbit, belo, lemoncash, ripio, astropay, ...); no Naranja X, Ualá, Brubank |
| Monthly inflation | `https://api.argentinadatos.com/v1/finanzas/indices/inflacion` | INDEC series since 1943; 2026-09 = 1.9 |
| Dólar MEP | `https://dolarapi.com/v1/dolares/bolsa` | buy/sell + timestamp |
| BCRA variables | `https://api.bcra.gob.ar/estadisticas/v4.0/monetarias` | 27 monthly inflation, 28 year-over-year, 29 REM expected 12-month inflation (21.1), 1196 PSP remunerated demand deposits average TNA (14.13 on 2026-10-09), BADLAR 7, TAMAR 44/45 |
| Ualá remunerated account | `https://www.uala.com.ar/inversiones/cuenta-remunerada` | HTTP 200, rates in static HTML (19/20/24% TNA, conditional); ~330 KB page |
| Naranja X remunerated account | `https://www.naranjax.com/cuenta-remunerada` | HTTP 500 to curl and headless Chromium: not automatable; owner-verified 20% TNA up to $1.000.000 matches TN / La Voz / Diario Huarpe (2026-10-13) |

ArgentinaDatos is a community aggregator: keep one adapter per source so a primary source (BCRA, CAFCI, INDEC) can replace it.

## Log

- **L1** (user, original request): "si. me gusta eso. de todas formas creo que lo haria con ia. digamos y gracias por la intencion de ayudarme a entender y demas, pero , tengo otro pensamiento respecto a eso. de todas formas gracias, pero me interesa... ver que proyecto top podriamos hacer? es decir, algo mas grande, tipo como ofertasuper, pero con este stack"
- **L2** (user, earlier context): "si, me re sirve, de hecho, en su momento note que se pide mucho java/spring. me gusta sumarlo, que podemos hacer?" and "me interesa igual, que si bien ataque problemas reales de forma rica, que demuestre entender el presente argentino tambien, que demuestre entender el problema y solucionarlo"
- **L3** (user): "siii. me encanta la 1, vamos por esa!" — accepts idea 1, the yield comparator, as proposed.
- **L4** (user): "si rey, vamos con eso, me interesa que crees lo necesario para poder ir directamente a crear una sesion directamente en ese dir y avanzar desde ahi" — accepts the name `rindetuplata` and the adapter-per-source approach.
- **L5** (evidence, 2026-10-14): data sources above verified live. Best plazo fijo TNA ~23% vs September inflation 1.9% monthly (~25% annualized): real return about zero, a product hook.
- **L6** (T1): skeleton generated from start.spring.io (Boot 4.1.1, Java 21, Maven wrapper); Testcontainers image pinned to `postgres:18-alpine`; `./mvnw test` passed (1 test, context loads against a real Postgres container, 18 s). Commit dc8ede2.
- **L7** (user, refinement session): "rey, revisa lo que tenemos de proyecto, me interesa de todas formas seguir "cerrando" mejor la idea a nivel proyecto y demas, me interesa que me vayas guiando con preguntas para cerrar mejor la idea y el proyecto en si, declarar (si existen) limites, ver si existen soluciones a esos mismos, ej: no tenemos acceso a "x" informacion,solucion: pagina con esos datos. la idea es 'cranearlo' lo mejor posible para luego pasar a la implementacion"
- **L8** (user): "si, la idea inicial es que es de portfolio, pero de todas formas, me interesa mucho que pueda llegar a ser un producto real con usuarios reales. por tanto, l-f y l-g son importantes"
- **L9** (user): "si obvio, supongo que primero deberiamos trabajar sobre a para luego hacer b, pero si me interesa llegar a b"
- **L10** (user): "hibrido estaria bien, pero... realmente no existe fuente que hable sobre esto? ni siquiera oficial? deberia haber algo que diga eso"
- **L11** (evidence): BCRA transparency API probed live: PlazosFijos covers 73 entities; CajasAhorros has no rate. BCRA variables 29 (REM) and 1196 (PSP average) found.
- **L12** (user): "no, usar las 2, es lo correcto, que el usuario comun 'se adapte' aunque tambien debemos hacer una ux/ui friendly para este tipo de usuarios."
- **L13** (user): "va a ser una complicacion lo de D1 eh, vamos a tener que meterle ahi. con respecto a la pregunta 5, realmente no lo tengo pensado con criterio, pero me suena bien y correcta la opcion a"
- **L14** (user): "okay, te tengo el 20% que ofrece rendimiento ahora mismo naranjax, fijate si esa informacion coincide con algo publico, es decir, intenta 'matchearla' si es lo que hace falta. si , la c es la mejor"
- **L15** (evidence): Naranja X 20% TNA matched in TN, La Voz and Diario Huarpe (2026-10-13), cap $1.000.000, manual activation, balance kept until 00:00, daily interest = balance × TNA / 365. naranjax.com returns HTTP 500 to automation; Ualá page scrapable.
- **L16** (user, D1 curator): "dale, vamos con tu recomendacion."
- **L17** (user): "concuerdo contigo, el seo va a ser uno de nuestros principales objetivos, ademas de que es un gran indicador para nosotros como para el perfil/cv . me interesa si, seguir lasmejores practicas de seo y demas. vamos con la opcion a . es importante tambien el ux/ui. tenemos que saber como mostrar los datos, grafos si hay, etc."
- **L18** (user): "aun que, espera, next.js es lo mejor aqui? o podemos quizas meter otra tecnologia? algo mas moderno? o esta bien esto? yo para no quedarnos en lo 'basico' si no hace falta. si, personalizado, con TU monto y TU plazo, totalmente personalizado"
- **L19** (user): "si, vamos con astro + react , excelente, vamos a tener que verificar versiones actuales y demas, probablemente con context7, whatever. avancemos"
- **L20** (user): "se, esta bien vps propio. me cierran los 5-8 usd, de todas formas, me interesaria poder probarlo local quizas al inicio, algo como en docker quizas? para no arrancar con 5-8 usd en "perdida" al no lanzarlo directamente, se entiende?"
- **L21** (user): "se, es una buena opcion ir por publico, esta bueno hacerlo opensource, why not? por otro lado, super lo de github actions, es algo como lo que hicimos con ofertasuper, perfecto! vamos con eso!"
- **L22** (user, capture location): "dale, vamos con b" — option (b): publish the main repo as public and store captures on an orphan `data` branch.
- **L23** (evidence, T9): capture tests 43/43 locally and in CI (`Capture tests` run on push); independent verify found no blocking defect, its non-blocking findings #2-#5 and #7 were fixed test-first (kept file sha256 in manifest, write-failure exits, source name validation, `--url`, CRLF/indented comments, 45 s per request, 20 min job). Repo published public at https://github.com/Mateocas1/rindetuplata; orphan `data` branch created; first `Daily raw capture` run 38529063474 (workflow_dispatch) captured 4/4 sources from GitHub runners and committed `raw/2026/10/14/` (BCRA transparency 393501 B, ArgentinaDatos plazo fijo 20511 B and rendimientos 7327 B, Ualá extract 765 B, manifest). BCRA does not block GitHub's US runners. Commit f475ac7.
