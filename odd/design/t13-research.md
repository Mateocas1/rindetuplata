# rindetuplata — visual direction research (T13)

Scope: read-only study of premium/precise finance and editorial references, to answer the owner's brief —
"le falta jerarquía por textos, se mezclan los textos, se siente básico; que se sienta más premium, más único,
sin mucha info en pantalla". Nothing in the repository was edited or committed.

Deliverable: `/tmp/t13/research.md`, plus section C's three directions and section D's asset shortlist.

## Method and coverage

| Source | Result |
|---|---|
| `getdesign.md` DESIGN.md analyses (mercury, stripe, wise, nerdwallet, revolut, linear.app, wired, oura, mastercard) | **Success.** The site's `llms.txt` index is JS-only and blocked, but the official CLI (`npx getdesign@latest add <brand>`) returned the same full `DESIGN.md` files. Read in `/tmp/t13/getdesign/<brand>/DESIGN.md` (24–36 KB each: exact colors, per-role font sizes/weights/tracking, components). |
| `style.gallery` | **Success.** `library/expression/index/index.md` (entry point) plus directions `fintech-clarity`, `nocturne-editorial`, `warm-print`, `developer-tool`. Note: `directions/index.md` itself 404s; the Expression index is the working route. |
| `seesaw.website` | **Success** (via Jina reader). Fintech + Crypto category pages, then 5 standout sites fetched from origin: fey.com, titan.com, usecache.com, goldsand.fi, moment.com. |
| `land-book.com` | **Partial.** Direct fetch returns 403 (also through the reader). Content retrieved indirectly: `land-book.com/?industry=Finance` via reader (30 KB) + `web_search` on land-book finance/landing-page indexes. Usable but only headline-level. |
| `21st.dev` | **Success** (via Jina reader). Category catalog (12k+ components) + per-component pages with author, library, license and npm dependencies. |

Current state of our UI (read-only): `web/src/styles/tokens.css` — Inter Variable, cool neutrals hue 255,
one blue accent hue 259, status hues 152/25/85, roles-only token contract, `tokens.test.ts` measures every text
and UI pair against WCAG 2.2 in both appearances. Any reskin below must keep `tokens.test.ts` green.

---

# A. Per reference

## A1. Mercury (getdesign.md, fintech/banking)
- **Type**: proprietary `arcadiaDisplay` (display/stats) + `arcadia` (body/UI), substituting Inter 350–480. No mono, no separate numeral face — stats are set in the display face. Hierarchy comes from size, colour steps and small uppercase eyebrows, never from weight above 500.
- **Scale**: stat 60px/480/lh 1.1 · display 52/480 · h1 42/480/+0.42px · h2 28/480 · lead 24/480 · body 18/360 · body 16/400 · UI 16/420 · eyebrow 14/500 uppercase · legal 12/400. Headlines get **positive** tracking instead of the usual tightening.
- **Colour**: light canvas `#fbfcfd`, surface `#fff`, lavender card `#edecfb`, ink `#28253b`, ink-2 `#554e7b`; night `#171721`, raised `#1e1e2a`, fog `#c3c3cc`; accent indigo `#5266eb` on exactly one solid pill per group.
- **Signature**: (1) 40px translucent capsule for secondary actions next to one solid indigo pill; (2) 60px/480 stat over a small fog caption, four in a row; (3) tonal elevation — cards separated by fill and 1px fog rules, no shadows.
- **For a comparator**: light-weight oversized numerals for yields, 14px uppercase column heads, lavender fill cards 12px radius/32px padding, ink-2 as the second text step. Do **not** copy the no-status-colour rule: a ranking needs gain/loss signal.

## A2. Stripe (getdesign.md)
- **Type**: single family `sohne-var` (fallback SF Pro Display) for every role; `ss01` globally, `tnum` on numerics. Weight 300 for display and body, 400 only for buttons/captions.
- **Scale**: display-xxl 56/300/lh 1.03/**-1.4px** · xl 48/300/-0.96 · lg 32/300/-0.64 · md 26/300/-0.26 · body 15/300 · tabular 14/300/-0.42 + `tnum` · caption 13/400/-0.39 + `tnum` · micro-cap 10/400 uppercase. Tracking scales with size (−1.4px at 56px, 0 at 18px).
- **Colour**: canvas `#fff`, soft `#f6f9fc`, ink `#0d253d` (navy-tinted, never black), ink-2 `#273951`, mute `#64748d`, hairline `#e3e8ee`, accent indigo `#533afd` (also link colour, never body text), featured card `#1c1e54`, shadow `rgba(0,55,112,.08) 0 1px 3px`.
- **Signature**: (1) pastel gradient mesh across the top third of the hero (heavy — skip or reduce to a band); (2) thin display with heavy negative tracking; (3) `tnum` on every money cell with tightened tracking.
- **For a comparator**: `tnum` on all rate/peso columns is the single strongest takeaway; a dark inverted card for the "best option" row; navy ink rather than pure black; one filled CTA.

## A3. Wise (getdesign.md)
- **Type**: `Wise Sans` 900 for brand moments + `Inter` for everything else (fallback Inter 900 / Manrope 800-900).
- **Scale**: mega 126/900 · xxl 96/900 · xl 64/900 · md 40/900 · Inter 32/600 · 24/600 · lead 20/400 · body 16/400 · strong 16/600 · small 14/400 · caption 12/400. Display line-height ≈ 0.85× the size; hierarchy is 900 vs 600 vs 400.
- **Colour**: sage page `#e8ebe6` with white cards; ink `#0e0f0c`, body `#454745`, mute `#868685`; accent lime `#9fe870` (pale `#e2f6d5` for badges); 1px ink borders on inputs and the converter card; semantic positive `#2ead4b`, warning `#ffd11a`, negative `#d03238`.
- **Signature**: (1) 126px/900 hero with 0.85 line-height; (2) sage canvas + white 24px-radius cards — surface contrast replaces shadows; (3) a converter card **on the hero** with a 1px ink border hosting from/to inputs.
- **For a comparator**: an interactive monto/días card in the hero (a calculator converts better than a paragraph); pale-green/dark-maroon pill badges for real-return signs; one lime CTA. Do not copy 126px/900 at 900 on a data-dense page.

## A4. NerdWallet (getdesign.md — itself a comparison product)
- **Type**: Gotham (body/UI/hero/stats) + GothamDisplay (headings 14–40px, overlapping the mid-scale), Chronicle Display serif only for long-form. Body weight **300** throughout; headings pure `#000`, letter-spacing 0. Substitute: Montserrat/Proxima Nova/Jost.
- **Scale**: hero 52/700 · display 40/700 · h1 36/700 · h2 28/700 · h3 20/700 · stat-value 24/700 · body-lg 18/300 · body 16/300 · body-sm 14/300 (table copy) · UI-label 14/700 uppercase (buttons) · caption 12/500 · eyebrow 12/700 uppercase in muted grey · micro 11/500. **Hierarchy = weight contrast 300 vs 700 far more than size.**
- **Colour**: canvas `#fff`, soft `#f7f7f7`, mint ladder `#effcf8/#e3faf2/#b7ecda`, lime emphasis `#e8fdac`, hero `#9ae600`, primary green `#008254` (deep `#006642`), ink `#000`, body `#3d4045`, muted `#64666a`, hairlines `#ebebec`/`#d8d9da`, link `#005fb9`. Green only on actions/active states; mints carry large surfaces.
- **Signature (directly reusable)**: (1) `comparison-row` — 5 columns (institution, rating, rate, bonus, action), rows separated by a hairline with **no vertical dividers**, header in eyebrow type on `#f7f7f7`; (2) best pick marked by a square-ended mint award pill pinned top-left, plus a lime badge-compare pill with 10px radius; (3) **every figure gets a caption label above it, never inline**; (4) near-square 2px buttons in uppercase, 16px padding; (5) below tablet, rows reflow into stacked cards with a full-width button.
- **For a comparator**: this is the closest structural match in the whole set — copy the row/typography discipline, not the green.

## A5. Revolut (getdesign.md)
- **Type**: Aeonik Pro 500 for display ≥20px, Inter 400/600/700 for body/UI. No mono, no numeral face.
- **Scale**: display-xxl 136/500/lh 1.0/-2.72px (once per page) · xl 80/500/-0.8 · lg 48/500/v1.21/-0.48 · md 40/500/-0.4 · h-lg 32/500 · h-md 24/500 · body 16/400/+0.24 · body-bold 16/600 · caption 13/400 · button 16/600. No eyebrow or stat role defined.
- **Colour**: canvas `#000` (never `#0a0a0a`) or `#fff`, soft `#f4f4f4`, elevated `#16181a`, text `#191c1f`→`#c9c9cd` ladder, hairlines `#e2e2e7` / `rgba(255,255,255,.12)`, brand cobalt-violet `#494fdf` **only** on the featured card, wordmark and badge; primary CTA is a white pill on black.
- **Signature**: (1) full-bleed black/white band alternation with no soft transitions (magazine spread); (2) 80–136px display at lh 1.0, never looser; (3) 20px-radius/32px-padding cards with one cobalt card among dark ones; 36px (44px mobile) pill filter chips at `#f4f4f4`.
- **For a comparator**: at most one cobalt element per viewport; pill chips for filters/sorts; luminance ladder instead of shadows; 88/120px band spacing. No table spec — it only hosts them on white bands.

## A6. Linear (getdesign.md)
- **Type**: Linear Display / Linear Text (one voice) + Linear Mono, but **mono lives only inside product screenshots**. Fallback Inter 500/600/700 + JetBrains Mono/Geist Mono.
- **Scale**: display-xl 80/600/lh 1.05/**-3.0px** · lg 56/600/-1.8 · md 40/600/-1.0 · headline 28/600/-0.6 · card-title 22/500 · subhead 20/400 · body 16/400 · body-sm 14/400 · caption 12/400 · button 14/500 · mono 13/400. **Eyebrow 13/500 with +0.4px positive tracking** — the positive/negative contrast marks taxonomy.
- **Colour**: canvas `#010102`, surfaces `#0f1011/#141516/#18191a`, hairlines `#23252a/#34343a/#3e3e44`, text `#f7f8f8/#d0d6e0/#8a8f98/#62666d`, accent lavender `#5e6ad2` (hover `#828fff`), success `#27a644` as the only semantic colour. Lavender never fills — brand mark, primary CTA, focus ring, links only.
- **Signature**: (1) surface-ladder hierarchy — hover/selected/featured move up one surface step, the selected pricing tab is a lift, not a colour change; (2) a faint white highlight on the top edge of lifted panels; (3) focus ring 2px at 50%, cards 12px + 1px hairline, buttons 8px (never pills).
- **For a comparator**: surface steps for rank/hover/selected; mono for rates and status tokens; 13px/+0.4px eyebrow for column headings; changelog-row as list-row (24px vertical padding, bottom hairline); tight 8px 14px buttons.

## A7. WIRED (getdesign.md)
- **Type**: WiredDisplay (tall narrow high-contrast serif, display only, weight **never above 400**) + BreveText (humanist serif, article body/bylines) + Apercu (sans for nav, eyebrows, captions, buttons). Serifs never carry nav or buttons; sans never carries article body.
- **Scale**: display 64/400/-0.5 · lg 48/-0.4 · md 32/-0.3 · sm 26 · sans micro-heading 20/700/-0.28 · serif body 16/24/+0.09 · sans body 17 · small 14/18/+0.4 · caption 12/16 · eyebrow Apercu 14/700 caps. Hierarchy = large jumps (64/48/32/26) + serif-vs-sans swap + grey `#757575` metadata.
- **Colour**: canvas `#fff`, soft `#f5f5f5`, ink `#000`, meta grey `#757575`, hairline `#e0e0e0`; **the only chroma is link blue `#057dbc`**; black is the accent (wordmark, CTA, footer).
- **Signature**: (1) 1px hairline-divided story rows as the only elevation cue (no shadows); (2) square 0px corners on every button and input; (3) magazine grid — one large cover card, a 2-up row, then a stack of rows; (4) 12px/400 caption type in table headers on `#f5f5f5`.
- **For a comparator**: hairline rows, 0px geometry, grey-step metadata, one non-brand chroma. Gap: no numeral or mono treatment at all — tabular figures must come from another face.

## A8. Oura (getdesign.md)
- **Type**: Editorial New (light high-contrast serif, weights 100/300, display + lead only) + AkkuratLL grotesque (300/400/600/700) for everything else, including 40px stats. Rule: "serif for feeling, sans for facts."
- **Scale**: display 110/300/lh 1.05 · display-2 80 · h1 68 · h2 40/300 · heading-3 (large stat) 40/400 sans · title 32 · lead 24/100 · body-lg 20/1.5 · body 18/1.5 · body-sm 16 · ui 14 · caption 13 · eyebrow 12/700 uppercase/+1px. Hierarchy from scale (110→12) and thin weights; colour steps stay quiet.
- **Colour**: warm paper `#f7f1e8` (never white), white for raised cards only, ink `#1c1b1a`, stone `#4a4741`, ash `#838280`, mute `#a8a5a0`, hairline `#e1dcd3`, action blue `#2a72de` **one moment per view**, earth ramps (gold `#af751b`, clay `#6b443d`, sage) for bands/data-viz only, error `#e33a22`.
- **Signature**: (1) circular stat badges — a perfect circle with a large number and small caption ("99%"); (2) three surface worlds in rotation: cream → ink band `#19191c` with cream clipping cards → silver product stage; (3) uppercase 12px eyebrows, pill tabs, very large vertical silences.
- **For a comparator**: sans stat numbers in a badge/circle, 12px uppercase eyebrow table headers, `#e1dcd3` hairlines, one-accent CTA, earth ramps for chart series. Weak fit: 110px display and photo-led hierarchy.

## A9. Mastercard (getdesign.md)
- **Type**: one geometric sans (`MarkForMC`) for everything, no serif/mono/numeral face; contrast from scale, weight and tracking.
- **Scale**: h1 64/500/lh 1.0/**-2% tracking** · h2 36/500/-0.72 · h3 24/500/-0.48 · body 16/450/lh 1.4 (weight 450 "load-bearing") · nav/button 16/500 · eyebrow 14/700 **uppercase +4% + a small accent dot** · footer column head 12–14/700 uppercase. Line-height tightens as size grows (1.0 → 1.2 → 1.4).
- **Colour**: canvas cream `#F3F0EE`, lifted `#FCFBFA`, white nav pill, ink `#141413`, slate `#696969`, dust taupe `#D1CDC7`, footer ink; signal orange `#CF4500` (consent actions only), orbit orange `#F37338` (dots/arcs), link `#3860BE`; **CTAs are ink-black pills**; ghost watermark in `#E8E2DA`.
- **Signature**: (1) oversized radius scale — 20px buttons, 40px hero frame, 999px pills, **nothing at 8–12px**; (2) ghost cream-on-cream watermark headlines + dotted uppercase eyebrow; (3) floating white nav pill with `rgba(0,0,0,.04) 0 4px 24px` and circular portraits with an arrow satellite.
- **For a comparator**: body at weight 450, −2% heading tracking, dotted uppercase eyebrow as category label, pill chips for filters, ink-pill CTA, cream canvas + lifted-cream nested panel.

## A10. style.gallery — Fintech Clarity Direction
- **Mood**: "the moment a number you were worried about turns out fine". Distilled from Toss, KakaoBank, Banksalad, Stripe, Revolut, Wise, Mercury, Ramp, Upbit, Flex.
- **Ground**: `--paper #fbfaf7`, `--card #f1efe9`, `--ink #111`, `--ink-2 #5c5c57` (4.5:1 on card), `--clause #74746e` (large text only), `--line #e3e0d8`, **exactly one** accent (`#3182f6` blue family | `#e4f222` lime on `#163300` forest | `#5266eb` indigo).
- **Type**: Inter Tight/Pretendard sans; JetBrains Mono 12px uppercase eyebrows; hero display 600–700 at 58px max over a 14px body (ratio 3.7), tracking ≈ −0.01em Latin; **numerals tabular, weight 700–800, the largest thing in their section, units at 40–50% size**.
- **Two-tone heading**: first clause in `--ink`, continuation in a `<span>` at `--clause`.
- **Rules**: one filled accent button per view; make one number interactive (Wise converter / Revolut currency switcher — a calculator beats a paragraph); keep the accent off decoration; radius 8px for B2B, full pills for consumer; measured fintech medians: body 14px, largest 58px, display tracking −0.009em, 9% gradient area, ~72% of homepage area for imagery; motion = count-ups on first view + one scrubbed reveal, no ambient loops.
- **Verification warning worth reusing**: mid grey `#8e8e88` on `#f1efe9` measured **2.86:1** and had to darken to `#74746e` — check grey copy against the *card* colour, not the page.

## A11. style.gallery — Nocturne Editorial Direction
- **Mood**: "a city at 3am seen through glass": ink ground, warm glow, oversized serif display, quiet mono labels.
- **Tokens**: `--ink #07070a`, `--ink-2 #111118`, `--paper #efe9df` (text, never pure white), `--paper-dim rgb(239 233 223/.62)`, `--line rgb(239 233 223/.14)`, accents `--ember #ff5a36`, `--violet #7b5cff`, `--haze #9bd1ff`. Serif `Instrument Serif`, sans `Inter Tight` 350, mono `JetBrains Mono`.
- **Roles**: hero display serif 400 `clamp(2.8rem,1.6rem+4.4vw,6.5rem)` tracking −0.02em lh 1.02 `text-wrap: balance`; section display 400 `clamp(2rem,…,3.6rem)`; body sans 350 `clamp(1rem,…,1.125rem)` lh 1.55 dimmed; labels mono .75rem uppercase +0.14em; **accent word = italic serif with a three-stop gradient clipped to text, one word per headline**; cards `--ink-2` + 1px line + 1.5rem radius + two corner radial gradients; primary pill is paper-on-ink; grain at 0.22 opacity `mix-blend-mode: overlay`.
- **Opinionated**: gradient on whole sentences "turns into a gaming UI"; a dark page of type + aurora reads empty after the first screen — give every screen something to look at; keep blurred blobs ≤3 and stop them under reduced motion.

## A12. style.gallery — Warm Print Direction
- **Mood**: "a well-made independent magazine": off-white paper, heavy grotesk display, ink text, one saturated spot colour, **visible grid lines**, halftone texture.
- **Tokens**: `--paper #f4efe6`, `--paper-2 #ebe4d6`, `--ink #141210`, `--ink-dim rgb(20 18 16/.64)`, `--rule rgb(20 18 16/.18)`, `--spot #1f4bff` (optional second spot `#ff4a1c`, never beside the first); fonts `Bricolage Grotesque` (grotesk), `Newsreader` (text serif), `IBM Plex Mono` (labels).
- **Roles**: hero display grotesk 800 `clamp(2.8rem,1.4rem+5vw,7rem)` −0.03em lh 1.0 (uppercase optional); section display 700 `clamp(2rem,…,3.8rem)`; body serif 400 1.125rem lh 1.6 **max 34rem measure**; labels mono .72rem uppercase +0.1em **preceded by a hairline**; **visible 1px column lines on a 12-column grid at ≥64rem**; flat `--paper-2` surfaces, no shadows, radius ≤0.25rem; images duotone (`mix-blend-mode: multiply` over the spot) or grayscale + halftone.
- **Opinionated**: "light editorial pages die from timidity, but the cure is pictures and a strict grid, not size" — one headline may break the grid, everything else stays strictly on it. Multiply blending needs an opaque ancestor: set `--paper` on the section, not only `body`.

## A13. style.gallery — Developer Tool Direction (supporting, for mono discipline)
- Use: mono labels/ids, surface ladder, hairline lists, keyboard-visible UI. Dark `#08090a`/`#0f1011`, light `#fafafa`/`#fff`, accent `#5e6ad2` for focus + one CTA + live indicators. Hero display 460–560 clamp(2.4rem→4rem), −0.02em. Buttons: primary is text-on-ground inverted, **not** the accent. ~116 hairline elements per page median. Motion: hover responses 100–160ms, no ambient loops.

## A14. seesaw.website picks (origin fetch: fey.com, titan.com, usecache.com, goldsand.fi, moment.com)
seesaw lists ~140 fintech and ~90 crypto entries; the five below are the closest to a *numbers-first comparator*, and all five are live sites (not shots).
- **Fey — Stock Finder** (`fey.com/features/finder`, from the "smartest stock finder" claim): data-screener positioning, Wealthsimple-sans display (`Wealthsimple-Sans-Display-Bold.woff2` is the only named font file in the HTML) — i.e. a thin grotesk + dense tabular results; the product *is* a table of names and figures, so the marketing page is quiet and lets the screener hero do the work.
- **Titan** (`titan.com`): private-wealth tone; `--font-neue-montreal` (Neue Montreal: precise neutral grotesk) with a near-black/white band rhythm and headline "The wealth manager for tech professionals", plus a "Trusted by employees of…" logo band — the classic trust pattern, executed with tight display type and generous band spacing.
- **Cache** (`usecache.com`): dominates with a warm-cream editorial palette (raw-frequency evidence in the HTML: `#F4EFE7` ×146, `#CCAF7E` ×91, `#B89352` ×79, `#49381F` ×57 — a paper/ochre/bronze ladder, not the usual blue) and headline-as-question copy ("Is your financial future riding on one or two stocks?"). Lesson: a **warm neutral ladder + one metallic accent** reads premium and unique without any gradient.
- **Goldsand** (`goldsand.fi`): "High yield savings with no interest" — a savings-yield product like ours; copy-led, plain sections ("How it works", "Earn more", "Start earning up to 6% returns"), so its differentiation has to come from typography and restraint rather than chrome.
- **Moment** (`moment.com`): fixed-income trading/data; near-black green-tinted palette (`#111D1B`, `#0A1312`, `#183334`, green `#3fbf3f`/`#9bdd9b`) with "The AI Partner for Investment Management" — a dark, instrument-panel mood for institutional data.
- **Recurring across the five**: the headline is one sentence, the product surface (table/screener/mock) is the visual, one accent hue, and figures are set larger than any adjective. None of them has a three-column feature row with icons.

## A15. land-book (partial)
- Direct fetch 403; the reader on `https://land-book.com/?industry=Finance` returns the index (~30 KB) and `web_search` exposes per-site entries. Confirmed finance entries include **Ramp, Lightspark, PayPath, Flex Elite, Clay, Patch, Cofactr**, plus the recurring curated names Stripe/Lightspark/Revolut on the landing-page index.
- Takeaway that matters here: land-book's finance winners are **product-mockup-led with a single accent**, and the "template-shaped" entries (PayPath-style dark gradient hero, Flex Elite fintech/credit landing) are exactly the ones that read interchangeable — useful as the negative example in section B.

---

# B. Patterns and anti-patterns

## Recurring in premium finance/editorial sites
1. **Exactly one accent, with one job.** Mercury indigo = one pill; Stripe indigo = CTA + link; Revolut cobalt = the featured card only; Linear lavender never fills; Oura blue = one moment per view; fintech-clarity says it outright: "keep the accent off decoration".
2. **Tabular numerals everywhere money is shown** (`tnum`), so count-ups and sorting do not jitter the layout; units set at 40–50% of the figure's size.
3. **Hierarchy built from something other than more greys**: size jumps (Revolut 136→80→48), weight contrast (NerdWallet 300 vs 700), case (12px uppercase eyebrows with 0.1–0.14em tracking), a four-step text ladder, or colour steps.
4. **Two-tone headings** — one clause in ink, the continuation in a lighter clause grey ("One platform. *Five jobs off your plate.*").
5. **Hairline structure instead of boxes**: 1px rules between rows, no vertical dividers, no shadows on dark; borders for structure, shadows only for depth.
6. **Surface luminance ladders** for rank/hover/selected (Linear, Mercury, Mastercard's lifted cream).
7. **A single interactive number in the hero** (Wise converter, Revolut currency switch): a calculator outperforms a paragraph.
8. **One editorial moment** — a serif at 400, an italic word, a magazine grid — inside an otherwise disciplined sans system.
9. **Texture as a finishing layer at low opacity**: grain 0.22 overlay, halftone, duotone, 1px visible grid at ≥64rem.
10. **Measured restraint**: fintech medians of body 14px, largest type 58px (ratio ≈3.7), display tracking ≈ −0.009em, gradient area ≈9%, motion = count-up + one scrubbed reveal at ~300ms and nothing ambient.
11. **Radius as a brand choice, not a default**: 0px (WIRED), ≤4px (Mastercard small type/paper), 8px (B2B Stripe/Ramp), 20/40/999px (Mastercard consumer), pills (Toss/Wise/Revolut).
12. **Status colour is separate from brand colour** and always paired with a sign or a word (our own token contract already enforces "never colour alone").

## Anti-patterns that read as "generic AI/Tailwind template"
- Centred hero, two pill buttons ("Empezar" / "Ver más"), purple-to-blue gradient, `rounded-2xl` cards with a soft shadow.
- Three-column feature row with an icon in a tinted circle and a two-word title; "Trusted by" logo wall with greyed logos.
- Every section opened by a centred uppercase grey eyebrow at 12px and 0.2em tracking — including sections that need no label.
- Inter (or system-ui) at three sizes only: 12/16/32, with hierarchy promised by `font-bold` alone.
- Grey body text below 4.5:1 (the `#8e8e88`-on-`#f1efe9` = 2.86:1 case) and muted-on-muted "captions" used as text.
- Bento grid of glassmorphic cards, neon glow borders, count-up animation on every figure, gradient on whole sentences.
- Accent used as decoration (illustration fills, gradients, section backgrounds) so the primary action stops being findable.
- Mono used as ornament (fake code labels, `//` prefixes, brackets) instead of carrying data.
- Numbers as text: figures in proportional digits, no alignment, units glued at full size, gain/loss shown by colour alone.
- Density with no silence: every band the same height, every block the same width, no 88–120px band breaks, no vertical silence.
- Everything centred — no left rail, no grid lines, no asymmetry, so the page has no "spine".

---

# C. Three visual directions

All three: numbers-first, WCAG AA (verified pairs listed), es-AR copy with voseo, one accent per view, and all type
open-licensed and confirmed on npm (`npm view <pkg> version` → 5.3.0, OFL-1.1 for all fonts listed below).
Because our tokens live in `web/src/styles/tokens.css` and `tokens.test.ts` measures every pair, each direction is
expressed as **token changes plus new roles**, not as component rewrites.

---

## C1. Papel Bursátil
**Mood**: the well-made weekend economics supplement of a serious newspaper — paper, rules and figures, no chrome.

**Typefaces** (3, all verified):
- Display: **Bricolage Grotesque** — `@fontsource-variable/bricolage-grotesque@5.3.0` (OFL-1.1), weights 700/800.
- Text: **Newsreader** — `@fontsource-variable/newsreader@5.3.0` (OFL-1.1), 400/500 + italic for the entradilla.
- Numerals/labels: **IBM Plex Mono** — `@fontsource/ibm-plex-mono@5.3.0` (OFL-1.1), 500/600, `font-variant-numeric: tabular-nums`.

**Colour concept**
- Light: paper `#f4efe6`, inset `#ebe4d6`, ink `#141210`, ink-dim `rgba(20,18,16,.64)` (≈7:1 on paper),
  rule `rgba(20,18,16,.18)`, spot `#1f4bff` (≈6.3:1 on paper), gain `#14663c`, loss `#b3261e`, warn `#8a5a00`.
- Dark: canvas `#141210`, raised `#1e1a17`, text `#f1ece2`, dim `rgba(241,236,226,.70)`, rule `rgba(241,236,226,.16)`,
  spot `#8fa4ff` (≈8:1 on `#141210`), gain `#5fbf86`, loss `#ff8a7a`.

**Hierarchy system (7 roles)**
| Role | Spec | Use |
|---|---|---|
| Kicker / edición | IBM Plex Mono 12px · 500 · UPPERCASE · +0.1em · ink-dim · preceded by a 1px rule | section and page metadata |
| Titular | Bricolage Grotesque 800 · `clamp(2.6rem,1.4rem+4.5vw,5.5rem)` · lh 1.0 · −0.03em · ink | hero only |
| Sección | Bricolage Grotesque 700 · `clamp(1.75rem,1.2rem+1.8vw,2.6rem)` · lh 1.1 · −0.025em · ink | "Cómo se calcula" |
| Entradilla | Newsreader italic 400 · 20px · lh 1.5 · ink-dim · measure ≤ 34rem | lead paragraph |
| Cifra | IBM Plex Mono 600 · 40–64px · tabular · ink · unit at 45% size · gain/loss only via sign + colour | the yield |
| Cuerpo | Newsreader 400 · 17px · lh 1.6 · measure ≤ 34rem · ink | prose |
| Celda / meta | IBM Plex Mono 500 · 13px · tabular · ink; meta Newsreader 400 13px ink-dim | ranking rows, captions |

**Signature devices**
1. A **visible 12-column hairline grid** (1px `rule` at ≥64rem) that only appears behind data blocks — it is the "edition" frame, and it disappears below 64rem.
2. Ranking rows as **líneas de cotización**: hairline dividers, no vertical dividers, a mono index `01–08` in the left gutter, every figure right-aligned tabular, and the spot colour used on exactly one thing per view — the 2px left rule of row 01.
3. A **mono pizarra** strip (three figures separated by rules) plus low-opacity halftone grain on the hero plate; the only "effect" on the page.

**Home hero**: paper ground; kicker "COMPARADOR · EDICIÓN 15/10"; Titular "¿Dónde rinde tu plata *hoy*?" with "hoy" in spot colour (not a gradient); a one-line mono summary "INFLACIÓN MENSUAL 2,4% · 8 ALTERNATIVAS · MEJOR RETORNO REAL 1,9%"; under it the pizarra of three figures; the monto/días calculator as an ink-bordered plate (1px ink, no shadow) with mono inputs.
**Ranking**: a full-width "pizarra de rendimientos" — sticky mono header row on inset paper, hairline rows, `01` gutter index, columns TNA / TEA / retorno real / plazo / acción; row 01 keeps a spot left rule and a mono "MEJOR" chip; gain/loss as sign + arrow + colour; a footer note in Newsreader italic explaining the inflation source.

**Risk**: the serif body plus a dense table can drift into newspaper clutter, so the measure and the 6-role discipline must be enforced; Bricolage/Newsreader have different x-heights and can shift baselines next to Plex Mono; the grid lines and halftone are decorative and must be dropped under `prefers-reduced-data`/small screens (and must never sit under body text). Highest effort of the three (grid + texture), highest distinctiveness.

---

## C2. Nocturno Ember
**Mood**: a market desk at 3am, seen through glass — ink ground, one warm glow, everything else whisper-quiet.

**Typefaces** (3, all verified):
- Display: **Instrument Serif** — `@fontsource/instrument-serif@5.3.0` (OFL-1.1), 400 + italic (there is **no** variable package: use the static `@fontsource` one).
- Text: **Inter Tight** — `@fontsource-variable/inter-tight@5.3.0` (OFL-1.1), 350–500 (a real change from today's Inter: narrower, tighter, lighter body).
- Numerals/labels: **JetBrains Mono** — `@fontsource-variable/jetbrains-mono@5.3.0` (OFL-1.1), 400/600.

**Colour concept**
- Dark (primary): ink `#07070a`, raised `#111118`, text `#efe9df` (warm, never pure white), dim `rgba(239,233,223,.66)`,
  line `rgba(239,233,223,.14)`, ember `#ff5a36` (use as the one action/notice), violet `#7b5cff`, haze `#9bd1ff` (data only),
  gain `#7fd6a5`, loss `#ff8f7a`.
- Light: paper `#f4f1ea`, raised `#fff`, ink `#141310`, dim `rgba(20,19,16,.62)`, line `rgba(20,19,16,.14)`,
  ember `#c93a17` (AA on paper), violet `#5b46c9`, gain `#14663c`, loss `#b3261e`.

**Hierarchy system (6 roles)**
| Role | Spec | Use |
|---|---|---|
| Etiqueta | JetBrains Mono 12px · 400 · UPPERCASE · +0.14em · dim | eyebrows, table heads, units |
| Titular | Instrument Serif 400 · `clamp(2.6rem,1.6rem+4.2vw,5.75rem)` · lh 1.02 · −0.02em · `text-wrap: balance` | hero |
| Sección | Instrument Serif 400 · `clamp(1.75rem,1.2rem+2.2vw,3.25rem)` · lh 1.08 | section titles |
| Entradilla | Inter Tight 350 · 18px · lh 1.55 · dim | lead |
| Cifra | Inter Tight 300 · `clamp(2.75rem,2rem+3vw,4.5rem)` · tabular · unit at 45% | the headline yield |
| Dato | JetBrains Mono 400–600 · 13px · tabular · text; dim for the previous-day figure | ranking rows, history |

**Signature devices**
1. **One word in italic serif with the gradient clipped to the text** — ember → warm amber → violet — and only one word per headline (the source direction's own warning: whole sentences "turn into a gaming UI").
2. **Grain at 0.22 opacity, `mix-blend-mode: overlay`**, plus at most three blurred gradient blobs behind the hero; cards are `raised` + 1px line + two corner radial gradients from the card's own accent pair.
3. **A single oversized figure per screen** with a mono label above and a mono unit at 45% — the page's whole argument in one number.

**Home hero**: ink ground; a slow ember/violet aurora behind (reduced-motion: static); "¿Dónde rinde tu <em>plata</em> hoy?" with only "plata" in the italic gradient serif; one figure "34,5% TNA" over "MEJOR PLAZO FIJO HOY"; a mono strip "DÓLAR MEP · INFLACIÓN · 8 ALTERNATIVAS"; the monto/días input as a hairline-bordered pill pair; one ember filled action.
**Ranking**: dark cards in a ladder (`#07070a` page → `#111118` row → `#16161f` hover), the winner card carries a corner ember glow and a mono "MEJOR" chip, each row shows a value plus a 60×16px haze-blue sparkline of the last 30 days, gain/loss as sign + word + colour; a vertical rhythm of 88–120px between bands; the light appearance is a paper spread of the same system (no aurora, no grain).

**Risk**: dark + glow + grain is one bad decision away from crypto/gaming; dim body text over a bright aurora can fall below 4.5:1 (the source direction warns about exactly this); blur is GPU-costly on low-end phones; the gradient-on-a-word device is the most "borrowed" of the three and must stay to one word. Cheapest to implement (a token swap plus one effect layer), strongest mood, weakest differentiation from current crypto-adjacent sites.

---

## C3. Grafito Cobalto
**Mood**: a Swiss precision instrument — cold, exact, one cobalt needle, not a line more than needed.

**Typefaces** (3, all verified):
- Display/figures: **Space Grotesk** — `@fontsource-variable/space-grotesk@5.3.0` (OFL-1.1), 500/700 (distinctive `1`, `g`, `3`; tabular figures that read as designed).
- Text: **Archivo** — `@fontsource-variable/archivo@5.3.0` (OFL-1.1), 400/500 (grotesk with generous apertures at 15–16px).
- Numerals/labels: **Geist Mono** — `@fontsource-variable/geist-mono@5.3.0` (OFL-1.1), 400/500.

**Colour concept**
- Light: canvas `#f7f8fa`, raised `#ffffff`, inset `#eef0f4`, ink `#0b0d12`, muted `#5a6070` (≈5.9:1 on white),
  hairline `#e2e5ea`, cobalt `#2947e0` (≈6.5:1 on white), gain `#0f6b3d`, loss `#b3261e`, warn `#8a5a00`.
- Dark: canvas `#0b0d12`, raised `#141721`, inset `#1b1f2b`, text `#f2f4f8`, muted `#a9b0be` (≈8:1 on canvas),
  hairline `rgba(255,255,255,.10)`, cobalt `#7c92ff`, gain `#5fbf86`, loss `#ff8a7a`.
- The cobalt appears **once per viewport**: the selected segment, the CTA, or the #1 rail — never two of them at once.

**Hierarchy system (6 roles)**
| Role | Spec | Use |
|---|---|---|
| Etiqueta | Geist Mono 12px · 400 · UPPERCASE · +0.08em · muted | column heads, units, "hoy / 7 días / 30 días" |
| Titular | Space Grotesk 500 · `clamp(2.2rem,1.5rem+3vw,3.75rem)` · lh 1.05 · −0.02em · ink | hero |
| Sección | Space Grotesk 500 · 28px · lh 1.2 · −0.01em | section titles |
| Cifra | Space Grotesk 700 · `clamp(2.5rem,2rem+2.5vw,3.75rem)` · tabular · ink | the yield |
| Cuerpo | Archivo 400 · 16px · lh 1.5 · ink (muted only for secondary) | prose |
| Dato | Geist Mono 400 · 13px · tabular · ink | table cells, ids, timestamps |
| Índice | Geist Mono 400 · 13px · muted | `01–08` gutter column |

**Signature devices**
1. **Surface luminance ladder instead of shadows**: base → raised (`+1` step) → inset, and hover/selected/ranked move one step up; on light, a 1px inset top highlight (an inner white line) marks the lifted panel; shadows stay only on the one sticky header.
2. **A mono index gutter** (`01 … 08`) on the left of every data list, plus 1px hairlines between rows — the page's "instrument" quality comes from the gutter, not from ornament.
3. **One capsule segmented control** (monto/días presets and "hoy / 7 / 30") with a sliding thumb animating via `transform` + the existing `--ease-standard` (CSS only, no motion library), and a 2px cobalt rail on the selected/top row.

**Home hero**: canvas ground with one inset band; mono eyebrow "COMPARADOR · 15 OCT 2026"; Titular "¿Dónde rinde tu plata hoy?" in Space Grotesk 500 at ~56px; beneath it the instrument: a segmented control (monto, días), one enormous tabular figure that recalculates, and a 120×28px cobalt sparkline of the best 30-day rate; a hairline three-column readout (MEJOR TNA / MEJOR REAL / PROMEDIO PF) with mono labels; one cobalt filled CTA.
**Ranking**: sticky header of mono column labels on inset; hairline rows with no vertical dividers; mono cells right-aligned and tabular; index gutter; delta chips that always carry a sign and a word; the top row lifted one surface step with a 2px cobalt left rail and a mono "MEJOR" chip; on hover a row lifts one step (no colour change); on mobile each row reflows into a stacked card with a full-width action, exactly as NerdWallet's rows do.

**Risk**: cold light Swiss is the closest of the three to what we have today, so without the index gutter, the ladder and the segmented control it will read as "another SaaS dashboard"; Space Grotesk is quirky at small sizes (use ≥15px for body-adjacent text and check its tabular alignment); surface ladders are easy to make invisible on cheap displays (keep ΔL between steps ≥3% or add the inset highlight). Lowest risk, lowest effort, moderate distinctiveness — the safest base if the owner wants one direction to iterate on rather than to choose.

---

# D. 21st.dev shortlist (fits the directions above, ≤8)

All are copy-paste shadcn-registry components: install with `npx shadcn@latest add "https://21st.dev/r/<author>/<slug>"`, which
requires a free 21st API key (`API_KEY_21ST`, limited installs/day). **Tailwind 4 caveat for every item**: the registry
targets Tailwind 3-era classes — expect to fix `bg-gradient-to-*` → `bg-linear-to-*`, `outline-none` → `outline-hidden`,
`shadow-sm` → `shadow-xs`/explicit values, and `tailwindcss-animate`/`tw-animate-css` for keyframes.

| # | Component | What it gives us | Deps | License |
|---|---|---|---|---|
| 1 | Sparkline — https://21st.dev/@redrob-labs/components/sparkline | Minimal SVG trend line with **gaps rendered as breaks, not interpolated** — exactly right for our history with missing days. No animation, no library. Best fit of the whole list (verified page + MIT). | none listed | MIT |
| 2 | Segmented Control — https://21st.dev/@halaska-studio/components/segmented-control | iOS-style thumb that slides between options; the "hoy / 7 / 30" and monto/días control in C3. Verify the thumb animation is CSS `transform` (no listed motion dep). | none listed | MIT (verified) |
| 3 | Marquee — https://21st.dev/@kevingirelli/components/marquee | Seamless ticker track that **pauses on hover, on keyboard focus and off-screen** — the accessible way to do the Papel Bursátil rate strip. | none listed | MIT (verified) |
| 4 | Ocean grain — https://21st.dev/@hazelgrace3026/components/ocean-grain | Pure-CSS gradient + film grain, zero-dependency React — the C1 halftone/grain layer without WebGL. | none (pure CSS) | MIT (verified) |
| 5 | Grain Gradient (Paper Shaders) — https://21st.dev/@paper-design/components/grain-gradient | Zero-dependency React WebGL shader for the C2 hero atmosphere (has a CSS-only fallback path at #4). Adapted from Paper Shaders under Apache-2.0. | none listed (WebGL) | Apache-2.0 (snippet; re-verify on page) |
| 6 | Number Ticker — https://21st.dev/@danielpetho/components/basic-number-ticker | Count-up with ref-based trigger — a *once per view* count-up on the hero figure, never on every number. | `motion` | MIT (snippet; re-verify) |
| 7 | Card — https://21st.dev/@wensity/components/card | Stat/KPI card with header/content/footer slots and sparkline slot — a reference for the C3 readout panel, not necessarily to install. | `clsx`, `framer-motion`, `tailwind-merge`, `@tabler/icons-react` | MIT (verified) |
| 8 | Table — https://21st.dev/@originui/components/table | Headless, unstyled table primitives from Origin UI; the ranking table in any direction keeps our own hairlines and tokens. | none listed | MIT |

**Considered and rejected** (keeps the page out of "template" territory and out of licensing trouble):
`Financial Markets Table` (isaiahbjork) — license *unknown* + `next-themes`, `framer-motion`; `Number Ticker`
(shadcnspace) — license "*no-license*"; `Comparison Table` (kvnkld) — "Free to use" but not a recognized license;
most KPI/stat cards (`beratberkayg`, `kavikatiyar`, `ssychui`, `wensity`) — `framer-motion`, i.e. a new animation
dependency we deliberately do not have (`web/package.json` carries no motion library today).

**Asset guidance for all three directions**: prefer items 1–4 and 8 (no new runtime dependency, CSS/SVG only), and
keep any borrowed component to *behaviour* while the tokens, radius, type and colour stay ours — that is what keeps
the result from looking like an assembled template.

---

## Appendix — evidence trail
- `getdesign.md` DESIGN.md files (full, exact values): `/tmp/t13/getdesign/{mercury,stripe,wise,nerdwallet,revolut,linear.app,wired,oura,mastercard}/DESIGN.md`
- `style.gallery`: `/tmp/t13/sg-{fintech-clarity,nocturne-editorial,warm-print,developer-tool}.md`
- seesaw: `/tmp/t13/seesaw-{fintech,crypto}.md`, detail pages under `/tmp/t13/seesaw/`, origin HTML under `/tmp/t13/sites/`
- land-book: `/tmp/t13/landbook-finance.md`
- 21st.dev: `/tmp/t13/c21-stat.md`, `/tmp/t13/c21-stat.html`, `/tmp/t13/c21-llms.txt`
- Font verification: `npm view <pkg> version` → all `5.3.0`, license `OFL-1.1`
  (`@fontsource-variable/bricolage-grotesque`, `@fontsource-variable/newsreader`, `@fontsource/ibm-plex-mono`,
  `@fontsource/instrument-serif`, `@fontsource-variable/inter-tight`, `@fontsource-variable/jetbrains-mono`,
  `@fontsource-variable/space-grotesk`, `@fontsource-variable/archivo`, `@fontsource-variable/geist-mono`;
  also available: `@fontsource-variable/fraunces`, `-public-sans`, `-roboto-mono`, `-dm-sans`, `-playfair-display`).
