# rindetuplata UI clarity pass

A design pass over the static site (`web/`) after the owner read a design critique of the T13 "Cobalto Cristal" rollout
(2026-11-05). Branch `ccr-36134cd3-lj3f49` from `origin/main` (326b872). Read every section until `## Log` before working
on a task.

## Specs

- **S1 Owner request (L1).** "me interesó todo lo que nombraste. Me gustaría de alguna forma, si querés, implementar todo
  de la mejor forma que encuentres, siguiendo las convenciones. Y cómo haría la implementación un senior." and "Si crees
  que es controversial o era algo que necesitaba de mí, toma la decisión, pero déjala documentada en un documento."
- **S2 Autonomy (L1).** "La idea es que no frenes, no te bloquees pidiéndome alguna decisión, porque ahora voy a irme a
  dormir y por 8 horas no voy a responder." Every open point is decided by the orchestrator and recorded under
  `### Decisions`; the owner may override any of them.
- **S3 Accepted critique (L2).** The critique the owner accepted, by point (quoted from the session's answer):
  - "Hay una capa decorativa de plantilla de landing SaaS 2025 encima" — "palabra en serif itálica de color dentro de un
    titular sans", "tarjeta de vidrio esmerilado con brillo cobalto y grilla fina detrás", "mayúsculas monoespaciadas en
    todos lados".
  - "Cuatro familias [...] son demasiadas para un sitio de datos" — "Archivo, que ya está cargada y tiene eje de ancho
    (wdth 62–125)"; "prosa en monoespaciada".
  - "El sitio dice que su razón de ser es el rendimiento real, pero la cifra gigante es la ganancia en pesos" — fiwind:
    "$ 13.414,01 en grande y -0,24% real en gris neutro, sin signo de color".
  - "Precisión falsa y empates" — "mostraría los empates como '=4' y agregaría 'vs. el mejor: −$411'".
  - Tables: "la columna 'Fuente y fecha' repite el mismo valor en todas las filas"; "todos los nombres subrayados";
    "REBA COMPAÑIA FINANCIERA S.A. (en mayúsculas y sin tilde)" — "hace falta un nombre para mostrar".
  - Layout: "media pantalla vacía en escritorio"; "etiquetas desalineadas"; "el campo muestra 1000000 sin formato";
    "la raya azul [...] aparece en [...] hasta en 'Hoy no ofrece plazo fijo'".
  - Mobile: "el menú se desplaza de costado con nueve ítems"; "la tabla del ranking también se desplaza de costado [...]
    lo que queda fuera de la pantalla es justo el rendimiento real".
  - Copy: "'Es información, no asesoramiento financiero' aparece tres veces en la página de instrumento"; simulator
    "arranca con una tarjeta vacía [...] botón 'Simular' con estilo secundario".
  - Product: "no se muestra la liquidez [...] ni la garantía de depósitos"; "el título debería ser la respuesta".
  - Not pursued from the critique: "Fondos mide 20.300 px de alto" — a fixture artifact: the API groups fund classes
    since post-mvp T2c, the web fixtures predate it (post-mvp L16).

## Tasks

| ID | Specs | Task | Route | Status | Commit |
|---|---|---|---|---|---|
| T1 | S3 | Type and surface foundation: one family (Archivo, `wght` + `wdth` axes), sans labels, no serif accent, no glass/glow/grid; tokens, recipes, their tests, font dependencies (D1-D3) | Sonnet worker | done | fbdadc6 |
| T2 | S3 | API `realEarned` (pesos above or below expected inflation) on ranked entries and simulations; OpenAPI, web schema/types, fixtures derived by script (D4) | Sonnet worker | done | 6345cea |
| T3 | S3 | Web presentation helpers: entity display names, liquidity per kind, competition ranks with ties and the gap to the best in exact cents (D5, D7, D8, D13) | Haiku worker | done | e50351b |
| T4 | S3 | Ranking, home and category landings: answer sentence, real result line, ties, gap to best, source caption, row links, mobile card list, one formatted amount field, liquidity chips (D6-D9, D15, D16) | Sonnet worker | done | 0e04f9d |
| T5 | S3 | Detail pages: instrument (two columns, live simulator, signed real result), banks and bank, monthly report, methodology (liquidity and guarantee section, TOC), 404; display names; rail only for the best (D12-D14) | Sonnet worker | done | de3b77a |
| T6 | S3 | Chrome and account: mobile menu, footer trimmed to one disclaimer, account pages (ingresar, alertas, claves-api, admin) aligned to the new recipes (D10, D11) | Sonnet worker | done | 2eec290 |
| T3b | S3 | Parent between waves: shared `RealResult` line (D19, 42c42a0) and es-AR amount input helpers (D16, b59c610) | parent | done | 42c42a0, b59c610 |
| T7 | S3 | Independent design review against S3 (base build vs new, light/dark, 320/390/1280/1440, contrast, focus); "no findings" is a valid result | Opus reviewer | done | L6 |
| T8 | S3 | Review round 1 fixes: instrument grid one shrinkable column below lg; conditional "hasta" offers read as earnings; curated source named in Spanish | parent | done | 4c11e7e |
| T9 | S3 | Review round 2 (fresh eyes on the fixed branch) | Sonnet reviewer | in progress | |

Waves: T1, T2 and T3 run in parallel (disjoint files, own worktrees); T4, T5 and T6 run in parallel on top of them
(disjoint surfaces); T7 reviews the integrated branch; a fix round follows only for findings that pass the AGENTS.md
finding bar.

### Decisions (orchestrator, under S2; the owner may override)

- **D1 The two Nocturno Ember gestures go (supersedes part of L80/L81 of `rindetuplata-mvp.md`).** The owner picked the
  serif "plata" and the glass card in L80; S1 accepts a critique that names both as template tropes. Kept from Cobalto
  Cristal: graphite neutrals, one cobalt accent, the surface ladder, nearly square panels, capsule segmented controls,
  the index gutter. Reverting T1's commit restores the gestures.
- **D2 One family: Archivo Variable with the width axis.** Display, section and figure roles use `font-stretch` around
  112% (SemiExpanded) at weights 600-700, so headlines and numbers keep a voice without a second family; text and data
  stay at 100%, every number with `tabular-nums`. Space Grotesk, Geist Mono and Instrument Serif are removed from
  `package.json`. Code-like strings (API keys) use the system `ui-monospace` stack.
- **D3 Labels in sentence case, sans.** `type-label` becomes Archivo 500 at the label step, sentence case, no tracking;
  the role names stay, so components keep their classes and only the recipes change. Prose never uses a mono or a
  label role.
- **D4 The real result in pesos is computed by the API** (S14 of the MVP: all financial logic lives in Java):
  `realEarned = finalAmount / (1 + inflation over the horizon) − amount`, `BigDecimal`, scale 2, `HALF_EVEN`, null when
  `realReturn` is null. The recorded web fixtures do not have it; a script derives it with the same formula and the
  fixture README says so. The web treats it as optional and falls back to the percentage.
- **D5 Liquidity per kind, guarantee only in the methodology.** Row chips: plazo fijo "Al vencimiento", billeteras and
  cripto/fintech "Disponible al instante", fondos "Rescate en el día". No per-row deposit-guarantee chip: the cover cap
  changes (BCRA Com. "A" 8407, $50.000.000 from 2026-04-01) and term deposits above a multiple of the BCRA reference
  rate are excluded, a rate this system does not ingest, so a blanket chip could be false. The methodology explains
  it: deposits in pesos at BCRA-regulated entities are covered up to a cap and with exclusions; FCI shares are not
  deposits; PSP payment accounts are not deposits (the BCRA-mandated PSP notice). Sources: BCRA "Aplicación del sistema
  de seguro de garantía de los depósitos" (texto ordenado, t-seggar.pdf).
- **D6 Pesos earned stays the sort key and the row figure** (MVP S15, L77). The real result moves from a 13px mono
  badge to a line in words, sign and color right under it: "$ 3.806 por encima de la inflación" / "$ 2.401 por debajo
  de la inflación", with the percentage secondary.
- **D7 Ties share a position** (standard competition ranking 1-2-2-4): equal `earned` in cents shows the same position,
  marked "=" ("=04"), with "empate" for assistive technology.
- **D8 Gap to the best instead of the final amount in the row.** Rows 2+ show "−$ 411 vs. la mejor" (exact cents from
  the API amounts, integer arithmetic: presentation, not financial logic). The final amount stays on the best-option
  answer and the instrument page.
- **D9 Mobile ranking is a card list.** Below `sm` the ranking renders as an ordered list of cards (position, name,
  earned, real result, TNA, liquidity); at `sm` and up it is the table. The hidden one is `display: none`, so assistive
  technology reads one of them.
- **D10 Mobile navigation is a zero-JS `<details>` menu** below `md`; the sideways-scrolling nav goes.
- **D11 One disclaimer per page, in the footer** (MVP S8 L-G stays satisfied on every page). The footer keeps one
  paragraph and its links; page-level repeats go.
- **D12 Detail pages use the page width.** Instrument: main column plus a sticky simulator aside at `lg`. Methodology:
  a sticky table of contents aside at `lg`. Bank and report: hero plus key facts side by side at `lg`, tables and
  charts full width.
- **D13 Display names are presentation only.** `displayName()` drops legal suffixes (S.A., S.A.U., ...), title-cases
  all-caps names, keeps known acronyms (BBVA, CMF, ICBC, HSBC, BICA, ...) and restores Spanish accents (Compañía,
  Crédito); URLs, slugs and API values never change; the legal name stays visible as secondary text on the entity page.
- **D14 The cobalt rail marks only the best option** (home answer and row 01). Instrument, bank and report heroes are
  plain cards.
- **D15 The home H1 stays the question** (brand and SEO target, MVP S14); the answer comes right under it as one live
  sentence ("Hoy, con $ 1.000.000 a 30 días, lo que más rinde es Reba: ganás $ 19.726,03, $ 3.806 por encima de la
  inflación esperada.") and the best-option card keeps the figure.
- **D16 One amount field, formatted.** The field shows "1.000.000" (es-AR grouping) when not focused and accepts digits
  with or without dots; presets stay as the capsule segmented control under their field; labels share one baseline.
- **D18 Source and date stay on every row (MVP S11, L13), but not as a column.** The critique asked to move them to a
  caption; S11 is an earlier owner decision ("with source and data date per row"), so the row keeps them in its
  secondary line under the name ("Plazo fijo · ArgentinaDatos · 14/10"), and the dedicated "Fuente y fecha" column goes.
  A curated or stale rate keeps its flag in that line. The table caption states the common source and date once.
- **D19 One shared real-result component.** `web/src/components/ui/RealResult.tsx` (parent, between waves) renders D6's
  line for the ranking, the best option and the instrument page, so the three never drift.
- **D20 Navigation by audience.** No page linked to `/alertas` (only the login panel redirected there), so the alerts
  of MVP S3/S13 were unreachable from the site. The header keeps the eight reading sections and adds "Alertas" as its
  one right-aligned action; "Claves API" (a developer page) moves to the footer links.
- **D21 Worktrees start from `origin/main`**, not from this branch: every worker first resets its own worktree branch to
  `origin/ccr-36134cd3-lj3f49`, which the parent pushes before each wave.
- **D17 Workers leave their changes uncommitted in their worktrees; the parent verifies and commits** (AGENTS.md "The
  parent always verifies pane output and makes the commit"). The Engram mirror is not reachable from this cloud
  session; this document is the record.

## Log

- **L1** (2026-11-05, owner): S1 and S2 verbatim above; routing left to the orchestrator ("Delega la cantidad que
  quieras en el modelo que veas necesario y el effort que veas necesario [...] si delegas en [Haiku], asegúrate que no
  sobrepase los 100K de contexto").
- **L2** (2026-11-05, evidence): critique from a fixture build (`WEB_FIXTURES=1`), 30 screenshots light/dark × 1440/390
  of home, plazo fijo, billeteras, fondos, instrument, bank, banks, report, methodology, ingresar, alertas, 404. Baseline:
  vitest 634/634 in 45s. Docker started inside the cloud container (`dockerd`, root), `postgres:18-alpine` pulls and runs.
- **L3** (evidence, wave 1): three workers in parallel worktrees. T1 (Sonnet high, ~6.5 min, 125k tokens): RED 10 failing
  (serif span in the H1, glass class on the home, Space Grotesk in the CSS, token assertions) → GREEN; integrated with T3:
  astro check 0/0/0, vitest 706/706; only the three Archivo `wdth` files ship. T2 (Sonnet high, ~12 min, 138k): RED
  compile errors (`Inflation.realEarned`, `RankedEntry` arity) → GREEN; full Maven suite 905/0/0 in its worktree, the six
  touched test classes rerun on the branch (90 tests green); `openapi.json` produced from the running app, kept to the
  4-line `realEarned` addition (the fresh dump reordered unrelated `JsonNode` properties); REBA derives 3805.67 on
  $1.000.000/30d; 529 fixture rows recomputed independently. T3 (Haiku xhigh, ~3.5 min, 107k — over the 100k guide
  by 7%, task stayed bounded): RED missing modules → GREEN 77 tests. Parent: `RealResult` and `amountInput` test-first
  (RED missing module → GREEN). Retro: the Agent tool's worktrees start from `origin/main`, not from the working branch
  (D21); a NBSP-aware text helper is needed when asserting es-AR money in rendered markup (`\s` folds U+00A0).
- **L4** (decision evidence, D5): BCRA Com. "A" 8407 (texto ordenado al 05/03/2026) sets the deposit-guarantee cap at
  $50.000.000 from 2026-04-01 and keeps the rate-based exclusion for term deposits; press sources still quote $25M, so the
  site names no amount and links the BCRA text.
- **L5** (evidence, wave 2): three workers in parallel worktrees, integrated with `git apply -3` (disjoint hunks of
  `build-smoke.test.ts` merged cleanly). T6 (Sonnet high, ~8.5 min, 142k): RED 10 → GREEN; header 56px + border at 390,
  menu links 44px, focus ring on every Tab stop. Deviation accepted: the menu `<details>` uses the shared `disclosure`
  chevron (motion.test requires every `<details>` on that recipe). T5 (Sonnet high, ~14.5 min, 271k): RED 17 + 4 → GREEN;
  fiwind reads "▼ $ 2.408 por debajo de la inflación −0,24% real". Deviations accepted: page titles and breadcrumbs use
  the display name, JSON-LD keeps the source name; "No ofrece" in the banks table (the header gives the context); the
  simulator aside shows its figure at the row step (the full step overflows ~270px). T4 (Opus high, ~26 min, 316k):
  RED 22 + 13 + 5 → GREEN; integrated suite: astro check 0/0/0, vitest 791/791. Deviations accepted: the board switches
  to cards below `md` (640-767px left the name ~130px wide), curated wallet rows read "Carga manual verificada dd/mm"
  instead of the API's English source name, the answer sentence hides on a failed query like the best-option card.
  Retro: a staged deletion (`git rm`) in a worker's worktree is missed by `git diff`; the parent checks
  `git diff --cached` too before applying.
- **L6** (evidence, T7 round 1): Opus reviewer, read-only, ~17 min, 182k tokens; base 326b872 vs 87a9af7 built side by
  side, 340 full-page shots (17 pages × 5 widths × light/dark × base/new), overflow on all 250 pages at 320/390/768,
  contrast on 18 pages, Tab walk, reduced motion, 7,509 internal hrefs (0 broken). S3 scorecard: every point fixed
  except mobile (partly). Qualifying: (1) severe, introduced by T5: 201 of 229 instrument pages 694px wide at 320/390
  (`instrumentos/[slug].astro` grid had no template below lg; the chart's fixed width grew the implicit column);
  (2) medium, made visible by D8: Ualá's "Hasta $ 1.019.915,26" (a final balance) beside 30-day earnings. Not pursued
  (below the bar): methodology TOC left edge at 264 vs 196, the real-result line wrapping to three lines at 768, the
  menu panel without a separator, the "Última tasa publicada" table scrolling in its box at 320, the error state
  keeping the previous excluded list, the cripto caption naming the newest date. Lowest measured contrast: muted on
  subtle surface 5.51:1, gain on raised 5.66:1.
- **L7** (evidence, T8): parent, test-first (RED 6: grid class, hasta as earnings in row/hero, curated label in the
  provenance and meta, `earnedFrom`, `sourceLabel`) → GREEN 797/797, astro check 0/0/0. Rendered check: scrollWidth 390 at
  390 on the REBA and Ualá instrument pages, `/billeteras/` and `/`. Also fixed while there: the simulator repeated the
  same final-balance "Hasta" and the English curated source. `sourceLabel` now serves the table, the best option, the
  instrument page and the simulator (one rule, four views). Retro: the T5 worker's screenshots of instrument pages
  were of fiwind (no chart) at desktop widths for the grid tweak; a layout change gets re-measured on a page that has
  every block (chart included) at the narrowest width.

