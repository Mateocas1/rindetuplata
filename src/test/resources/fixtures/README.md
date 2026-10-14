# Recorded fixtures

Real responses recorded on 2026-10-14, trimmed to keep edge cases. Tests never call live HTTP.

| File | Source | Recording |
|---|---|---|
| `bcra/transparencia-plazos-fijos.json` | `https://api.bcra.gob.ar/transparencia/v1.0/PlazosFijos` | From the T9 archive (`data` branch, `raw/2026/10/14/`), 33 of 914 rows: Galicia, Ualá, Naranja Digital, Brubank, null/zero rates, null/zero term codes |
| `bcra/monetarias-{27,29,1196}.json` | `https://api.bcra.gob.ar/estadisticas/v4.0/monetarias/{id}?limit=5` | Live, 5 most recent values |
| `bcra/monetarias-unknown-variable.json` | same, id 999999 | Live HTTP 400 error body |
| `argentinadatos/plazo-fijo.json` | `https://api.argentinadatos.com/v1/finanzas/tasas/plazoFijo` | T9 archive, complete (32 institutions) |
| `argentinadatos/rendimientos.json` | `https://api.argentinadatos.com/v1/finanzas/rendimientos` | T9 archive, complete (13 platforms) |
| `argentinadatos/fci-mercado-dinero-{ultimo,penultimo}.json` | `https://api.argentinadatos.com/v1/finanzas/fci/mercadoDinero/{ultimo,penultimo}` | Live, 32 of 621 classes incl. Mercado Fondo, Ualintec, Cocos and two dead classes dated 2020 |
| `argentinadatos/inflacion.json` | `https://api.argentinadatos.com/v1/finanzas/indices/inflacion` | Live, last 24 months |
| `dolarapi/dolar-bolsa.json` | `https://dolarapi.com/v1/dolares/bolsa` | Live |
| `uala/cuenta-remunerada-extract.json` | T9 `html-rate` extract of `https://www.uala.com.ar/inversiones/cuenta-remunerada` | T9 archive |
| `uala/cuenta-remunerada.html` | Shaped like `https://www.uala.com.ar/inversiones/cuenta-remunerada` | Hand-trimmed sample, not a recording: recorded rate text in minimal markup, plus script/style decoys |
| `uala/capture-sample.html` | Same page | Hand-made sample, byte-identical to `capture/test/fixtures/uala.html`, so Java and `extract_rates.py` share one parity case |
