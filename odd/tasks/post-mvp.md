# rindetuplata post-MVP

The six work points of the post-MVP audit (Engram `rindetuplata/post-mvp-audit`, observation 4475 rev 2), in the recommended order.
Branch `feat/post-mvp` from `origin/main` (437f3e4). Read every section until `## Log` before working on a task.

## Specs

Each spec quotes the accepted recommendation (L2) verbatim; the indented items quote the audit findings it refers to.

- **S1 Hardening and bugs.** "Hardening y bugs (1-2 días): tope de `days`/`amount`, rate limit, bug de alertas, eje del gráfico, escape del JSON-LD. Son errores, no features."
  - "DoS en la API pública. `days` solo tiene cota inferior (`RankingRequest.java`). `RemuneratedAccount.accrue` hace un loop por día y por billetera con `BigDecimal` DECIMAL128. Un `GET /api/v1/ranking?days=2000000000` deja la CPU tomada indefinidamente, y sin rate limit cualquiera lo repite. [...] Se arregla con un tope de `days` (por ejemplo 1825), un tope de `amount` y rate limit en Caddy."
  - "Alertas que nunca se disparan. `AlertEvaluationService` pide el ranking con `limit=200` y recién después filtra por tipo o instrumento. En el fixture hay 398 instrumentos rankeados, así que una regla sobre algo que quedó del puesto 201 para abajo nunca dispara."
  - "Gráfico engañoso. `chart.ts` pone en el mismo eje la TNA anual (30-40%) y la inflación INDEC **mensual** (~2%)."
  - "JSON-LD sin escapar `<` (`Seo.astro:52`). Un `</script>` en un nombre rompe el bloque."
- **S2 Financial model.** "Modelo financiero: renovación del plazo fijo, rendimiento de 7 días en FCI, agrupar clases, mapeo de billeteras por clave estable. Sin esto, el ranking puede recomendar mal."
  - "El plazo fijo queda castigado en plazos largos. Para más de 30 días se calcula interés simple a la TNA de 30 días, mientras que las billeteras capitalizan todos los días. Ejemplo: 30% de TNA a 180 días da 14,79% simple contra 15,73% renovando mes a mes. [...] Lo realista es modelar la renovación mensual."
  - "El rendimiento de los FCI sale de un solo día de variación de la cuotaparte. Es ruidoso, y la convención de la industria es el rendimiento de 7 días hacia atrás."
  - "174 de las 200 filas del ranking son clases de fondos, con muchas casi duplicadas (clase A, B, C del mismo fondo). Para el usuario es ruido: conviene agrupar por fondo."
  - "Mercado Pago se identifica por el string `\"Mercado Fondo - Clase A\"`. Si ArgentinaDatos le cambia el nombre, desaparece como \"historial insuficiente\", que además es la razón equivocada. Hay que usar una clave estable."
- **S3 Performance.** "Performance: `fundSeries` bien acotado, caché + ETag por corrida de ingesta, k6 antes/después para el README."
  - "`simulate()` arma el ranking entero (todos los tipos y todas las series de fondos) para simular un solo instrumento."
  - "Así como está, rompe la lógica. `FundEvaluator` necesita las dos últimas **no inválidas** más la más nueva para el chequeo de staleness."
- **S4 Real deploy.** "Deploy real: backups con restore probado, límites de memoria, job de backfill para FCI/macro/dólar, alerta de staleness y control de frescura en el build."
  - "No hay un script de backup instalado ni nunca se probó un restore."
  - "No hay `mem_limit` ni rotación de logs. Con `MaxRAMPercentage=75` y sin límite de contenedor, la JVM le compite memoria a Postgres y a RabbitMQ."
  - "No hay perfil `prod`: `secure-cookie=false` es el default y depende de que el overlay lo pise."
  - "La imagen no se buildea en CI y no hay Dependabot."
  - "El build no tiene ningún control de frescura ni de cantidad mínima de datos. Si la API está caída o vacía, igual publica un sitio vacío o viejo, y eso lo indexa Google."
  - "Cada build hace unos 30 requests de ranking idénticos, sin timeout ni retry."
  - "FCI, macro y dólar están marcados como \"recuperables\", pero no hay ningún job de backfill, solo `ultimo/penultimo`."
- **S5 Coverage.** "Cobertura: más billeteras y el MEP como devaluación de equilibrio."
  - "Lo honesto es una devaluación de equilibrio: \"el plazo fijo te gana al dólar si el MEP sube menos de X% en N días\"."
- **S6 Content and retention.** "Contenido y retención: reporte mensual, páginas por entidad, alertas nuevas, API keys, MCP."
  - Fable's audit items it adopts (L3): "\"Quién le ganó a la inflación este mes\". Un reporte mensual generado desde tu propio histórico, con INDEC real."; "Páginas por entidad. /bancos/banco-nacion con la tasa hoy, el gráfico de 90 días y \"cuántas veces fue la mejor\"."; "Agregá \"cambió la mejor opción\", \"mi banco bajó la tasa\" y un resumen semanal."; "Sumá API keys, límites por plan, un llms.txt y un servidor MCP en Java sobre el mismo ranking."
- **S7 Environment.** "antes, instala jdk 21 y dale acceso a docker, probablemente en este entorno notengamos docker, deja todo instalado y correctamente setteado para continuar con los 6 puntos".

- **S8 Autonomous orchestration and session handoff (L13).** "excelente, entonces dejemos eso, corre autonomamente hasta terminar con todo. haz el paso a una nueva sesion, podes hacerlo mediante un pane nuevo con herdr cli, abriendo sesion nueva de gs con el modelo opus-5.5 en effort high, con esta misma directiva persistida y a el tambien sobre el \"pasar la posta alrededor del 40%, siempre al final de una ola de tareas y nunca en el medio. el techo 60%\"" Accepted rule it refers to: "pasar la posta alrededor del 40% de la ventana (unos 400k tokens), siempre al final de una ola de tareas y nunca en el medio. El techo duro es el 55-60%." Every orchestrating session runs the remaining tasks without asking for approval between them, and hands off by the recipe in `AGENTS.md` (Session handoff) to a new `gs` (`gentle-shell`) session on Opus 5.5, effort high, passing this same directive on.
- **S9 Context check per wave and closing audit loop (L15).** "perfecto, si no esta persistido, da la directiva que se evalue el contexto como vas a hacer ahora para ver si se sigue o no. por otro lado, me interesaria que al finalizar con todo, se vuelva a hacer la misma auditoria que hiciste, lo mejor posible, para ver si debemos hacer otra tanda de correciones o agregados, asi hasta no tener que hacerlo mas, asegurate, que si se puede, dejar de alguna forma al auditor, que no es necesario encontrar mejoras, es decir, pueden no existir, no hace falta \"mejorar por mejorar\" o \"corregir por corregir\", para no entrar en el famoso loop de correcciones, se entiende" Rules in `AGENTS.md` (Session handoff: context check; Closing audit loop).

## Tasks

| ID | Specs | Task | Route | Status | Commit |
|---|---|---|---|---|---|
| T0 | S7 | JDK 21 (mise, Temurin 21.0.13) and rootless Docker (no sudo available; user systemd service, `rootless` context, `~/.testcontainers.properties`); baseline `./mvnw test` | parent | done | — (machine setup) |
| T0b | S7 | Two tests that race under full-suite load (L6): `StartupIngestionTest.aSecondReadyEventOnAnAlreadyCompletedDayIsANoOp`, `OutboxTopologyIntegrationTest.theBindingsRouteAPublishedMessageToTheQueueAndADeadLetterToOneOfItsOwn` | worker (with T1b) | done | 772cb32 |
| T1a | S1 | API input caps (`days` ≤ 1825, `amount` ≤ 1e12) as 400 ProblemDetail on ranking and simulations; per-IP rate limit on the public read API (429) | worker | done | ca86057, 5109440 |
| T1b | S1 | Alert evaluation filters by rule before any page limit | worker | done | d486016 |
| T1c | S1 | History chart on one comparable basis (annualized INDEC inflation supplied by the API) and JSON-LD `<` escaping | worker | done | d1169b7 |
| T1d | S1 | Rate-limit follow-ups of the T1a verification (L9): key IPv6 buckets by /64, cap the bucket map, take the sweep off the per-request lock | worker | pending | |
| T2a | S2 | Plazo fijo renewal: horizons of two or more minimum terms compound term by term | worker | done | 883eb35 |
| T2b | S2, S3 | FCI trailing 7-day yield, `fundSeries` bounded by that window | worker | done | b41a814 |
| T2c | S2 | Group fund classes: one row per fund by default; USD-denominated classes and "Ley N° 27.743" classes are not ranked against peso inflation (L7); web fixtures are not re-recorded (L16) | worker | pending | |
| T2d | S2 | FCI-backed wallets matched by a normalized class key with aliases (the source has no id, L7), with the right exclusion reason | worker | done | b41a814 |
| T3a | S3 | Targeted `simulate()`, ranking cache and ETag per ingestion run | worker | pending | |
| T3b | S3 | k6 load script and before/after numbers in the README | worker | pending | |
| T4a | S4 | Backfill job for FCI, macro and dollar history | worker | pending | |
| T4b | S4 | Ingestion staleness health indicator and metric; `prod` profile that fails fast on dev defaults | worker | pending | |
| T4c | S4 | Backup script + timer + tested restore; compose memory limits and log rotation | worker | pending | |
| T4d | S4 | Web build: freshness and minimum-data gate, one memoized ranking snapshot, fetch timeout and retry | worker | pending | |
| T4e | S4 | CI: Dockerfile build, deploy script tests, Dependabot | worker | pending | |
| T7 | S9 | Closing audit round 1 (`AGENTS.md`, Closing audit loop); a round with no qualifying finding ends the loop | parent + explorers | pending | |
| T5a | S5 | More FCI-backed wallets, class names confirmed against live data | worker | pending | |
| T5b | S5 | Dollar MEP break-even devaluation in the API and the UI | worker | pending | |
| T6a | S6 | Monthly "who beat inflation" report page | worker | pending | |
| T6b | S6 | Entity pages | worker | pending | |
| T6c | S6 | New alert kinds and weekly digest | worker | pending | |
| T6d | S6 | API keys with plan limits, llms.txt, MCP server | worker | pending | |

Out of scope until the owner decides (`docs/deploy.md` owner decisions): renting the VPS, the domain, the SMTP provider and the region. S4 covers everything the repository can own.

## Log

- **L1** (2026-11-01, owner): "podrias avanzar con esos 6 puntos que me recomendas. antes, instala jdk 21 y dale acceso a docker, probablemente en este entorno notengamos docker, deja todo instalado y correctamente setteado para continuar con los 6 puntos, segui el orden de pasos que veas correcto"
- **L2** (2026-11-01, accepted recommendation, Opus 5.5 audit): "1. Hardening y bugs (1-2 días): tope de `days`/`amount`, rate limit, bug de alertas, eje del gráfico, escape del JSON-LD. Son errores, no features. 2. Modelo financiero: renovación del plazo fijo, rendimiento de 7 días en FCI, agrupar clases, mapeo de billeteras por clave estable. Sin esto, el ranking puede recomendar mal. 3. Performance: `fundSeries` bien acotado, caché + ETag por corrida de ingesta, k6 antes/después para el README. 4. Deploy real: backups con restore probado, límites de memoria, job de backfill para FCI/macro/dólar, alerta de staleness y control de frescura en el build. 5. Cobertura: más billeteras y el MEP como devaluación de equilibrio. 6. Contenido y retención: reporte mensual, páginas por entidad, alertas nuevas, API keys, MCP."
- **L3** (2026-11-01): S6 adopts the content items of Fable 5.1's first audit as the owner pasted them; the full audit text is in the owner's message of this session and in Engram observation 4475.
- **L4** (2026-11-01, environment): sudo needs a password, so the owner cannot be added to the `docker` group from the session. Rootless Docker 29.9.2 runs instead: extras and a static `slirp4netns` 1.3.7 in `~/.local/share/docker-rootless`, symlinked into `~/.local/bin`; user service `docker.service` enabled with linger; CLI context `rootless`; Testcontainers through `~/.testcontainers.properties` (`docker.host=unix:///run/user/1000/docker.sock`). JDK: `mise use -g java@temurin-21`.
- **L6** (2026-11-01, baseline on `origin/main` code): `./mvnw test` under rootless Docker ran 450 tests, 2 failures: `StartupIngestionTest:135` (expected 6 source runs, was 8: it counts while the job is still writing steps) and `OutboxTopologyIntegrationTest:88` (no message on the email queue within 5 s). Both pass alone twice in a row, so they race under load; the environment is sound.
- **L7** (2026-11-01, live check of `GET https://api.argentinadatos.com/v1/finanzas/fci/mercadoDinero/ultimo`): 623 rows with only `fondo`, `horizonte`, `fecha`, `vcp`, `ccp`, `patrimonio`; there is no class id, so the FCI-backed wallet key (T2d) can only be a normalized class name plus explicit aliases, and a missing class must say so (not INSUFFICIENT_HISTORY). The same list carries USD money-market classes ("Balanz Money Market USD - Clase A", "Cocos Dólar Money Market - Clase A", "Delta Dólares - Clase A") and tax-amnesty classes ("... Clase L Ley N° 27.743") that the ranking currently treats as peso funds open to anyone.
- **L8** (2026-11-01, T1 integration): T1a, T0b, T1b and T1c were built in parallel worktrees and cherry-picked onto `feat/post-mvp` without conflicts. T0b root cause for the outbox race: Spring 7.0.9 pauses an unused cached test context and its restart starts every Rabbit listener container regardless of `autoStartup`.
- **L9** (2026-11-01, independent verification of T1a): the four S1 items pass; the client address cannot be spoofed (the API publishes no port, Caddy replaces `X-Forwarded-For`, Tomcat trusts 172.16/12). F1 (blocking, fixed in 5109440): `amount=1e999999999` parses as a valid BigDecimal and the 400 message's `toPlainString()` built a billion-character string. Follow-ups (T1d, not blocking): an IPv6 attacker rotating a /64 gets a fresh bucket per address; the map grows with throughput x 60 s between sweeps; `sweep` takes one lock per request. Also fixed: the API now trims the bypass token as the web build does.
- **L16** (2026-11-01, T2b/T2d integration and T2c decisions): `fundSeries` reads [asOf - 21, asOf]; a class with no quote in 21 days is no longer listed as STALE but absent (accepted: it is not an available instrument; a mapped wallet then reads FUND_CLASS_NOT_FOUND). Copy in `web/src/lib/categories.ts` updated to the 7-day yield by the parent. Worker suite: Maven 499/0/0, web 410. Decision: the recorded web ranking fixtures (2026-10-14 snapshot) are NOT re-recorded in T2c: funds are not archived, so a fresh database has no 7-day history and a re-record would replace the whole dataset; they stay a shape snapshot until a real deploy records new ones (T4). New T2c fields are covered with synthetic test data.
- **L15** (2026-11-01, owner): "perfecto, si no esta persistido, da la directiva que se evalue el contexto como vas a hacer ahora para ver si se sigue o no. por otro lado, me interesaria que al finalizar con todo, se vuelva a hacer la misma auditoria que hiciste, lo mejor posible, para ver si debemos hacer otra tanda de correciones o agregados, asi hasta no tener que hacerlo mas, asegurate, que si se puede, dejar de alguna forma al auditor, que no es necesario encontrar mejoras, es decir, pueden no existir, no hace falta \"mejorar por mejorar\" o \"corregir por corregir\", para no entrar en el famoso loop de correcciones, se entiende" Recorded as S9 and T7.
- **L14** (2026-11-01, T2a): exact 180-day renewal at 30% TNA is 15.737%, so the copy says 15,74% (S2 quoted 15,73%); the rule starts at `days >= 2 * minimumDays` (60 days), so the copy says "60 días o más". Worker suite: Maven 495/0/0, web 410.
- **L13** (2026-11-01, owner): "excelente, entonces dejemos eso, corre autonomamente hasta terminar con todo. haz el paso a una nueva sesion, podes hacerlo mediante un pane nuevo con herdr cli, abriendo sesion nueva de gs con el modelo opus-5.5 en effort high, con esta misma directiva persistida y a el tambien sobre el \"pasar la posta alrededor del 40%, siempre al final de una ola de tareas y nunca en el medio. el techo 60%\"" Recorded as S8; the recipe lives in `AGENTS.md` (Session handoff).
- **L12** (2026-11-01, owner): "con respecto a las decisiones mias del punto 4, por ahora seria que no a todas, mas adelante, primero quiero pulir al maximo el producto." Consequence: no VPS, domain, SMTP or region now; the product points (S2, S3, S5, S6) go before the repository-side deploy work of S4, which moves to the end.
- **L11** (2026-11-01): integrated T1 suite on feat/post-mvp, alone: Maven 488 tests, 0 failures, 0 errors; web 410 tests, astro check clean (T1a fix 5109440 is Java only).
- **L10** (2026-11-01, environment): with rootless Docker two Maven suites at once can collide on published ports (`RootlessKit PortManager.AddPort(): bind: address already in use`), which failed StartupIngestionTest's context in the first integrated run. Run one Maven suite at a time on this machine.
- **L5** (2026-11-01, decisions taken under the session's standing permission): `days` cap 1825 (5 years) and `amount` cap 1e12 ARS; plazo fijo renewal splits the horizon into `floor(days/30)` terms, the last one absorbing the remainder, at the same TNA (the same constant-rate assumption the wallets use); grouped funds show the best class for the request and say how many classes were folded.
