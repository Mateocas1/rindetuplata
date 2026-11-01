# FCI-backed wallets

A wallet is FCI-backed when its own terms say the remunerated balance is invested in one money-market fund class.
The ranking does not read a wallet rate for it: it reads the daily share value of that class (trailing 7-day yield,
S2) and shows the wallet once, as a `WALLET`, instead of also listing the class as a `FUND`.

The mapping lives in `src/main/resources/fci-backed-wallets.json` (`id`, `name`, `fund`, optional `aliases`). The
source publishes no class id (L7), so a class is matched by its normalized name (`FundClassKey`). A mapped class that
the source stops publishing reads `FUND_CLASS_NOT_FOUND`.

## Admission rule (T5a, L24)

A wallet is added only when **both** checks pass, and the URL of the second is recorded here:

1. The exact class name appears in one live
   `GET https://api.argentinadatos.com/v1/finanzas/fci/mercadoDinero/ultimo`.
2. The wallet's own official page, or its official legal / fund-terms page, names that fund class.

A candidate that fails either check is not added. The verbatim rows of the classes that were confirmed live are kept
in `src/test/resources/fixtures/argentinadatos/fci-mercado-dinero-wallet-classes-2026-11-01.json` (the older
recorded fixtures are not re-recorded, L16); `FciBackedWalletCatalogTest` checks every mapping against them.

## Mapped wallets

| Wallet | Class (as ArgentinaDatos publishes it) | Official source naming the class | Checked |
|---|---|---|---|
| Mercado Pago | `Mercado Fondo - Clase A` | Existing mapping (T3), unchanged | 2026-11-01 live |
| Personal Pay | `Delta Pesos - Clase X` | https://www.personal.com.ar/pay/cuentas-remuneradas ("Personal Pay ofrece Rendimientos por medio del FCI Delta Pesos Clase X"; fund rules at https://deltaam.com.ar/fondos_de_inversion/delta-pesos/) | 2026-11-01 |

## Candidates checked and not added (2026-11-01)

| Candidate | Reason |
|---|---|
| Prex | Its help page says the balance is invested in "un Fondo Común de Inversión (FCI) de Liquidez de Allaria Ledesma Fondos Administrados SGFCI SA" (https://www.prexcard.com.ar/centro-de-ayuda/mis-rendimientos/cuenta-remunerada-en-pesos), but no official page names the class. Allaria publishes several (`Allaria Ahorro - Clase A/B/C/E`, `Allaria Ahorro Dinámico - Clase A-E`), so any pick would be a guess. |
| Claro Pay | Its terms name the fund, "SBS Ahorro Pesos FCI" (https://claropay.com.ar/p/claropay/legals.html, https://claropay.com.ar/p/claropay/faqs.html), not a class. The live response has four (`SBS Ahorro Pesos - Clase A` to `D`) with different fees. |
| Cocos | The balance in Cocos Pay earns nothing ("Los fondos disponibles ... no generan intereses", https://cocos.capital/legales/cocos-pay/terminos-y-condiciones-cocos-pay.pdf). Cocos Ahorro is a fund the user chooses, and the class (A or B) depends on the account ("Desde la app podés ver cuál aplica a tu cuenta", https://cocos.capital/fondos-comunes-de-inversion/cocos-ahorro). `Cocos Ahorro - Clase A` is listed as a fund. |
| Brubank | FCIs are an optional investment among six SBS funds chosen by the user (https://brubank.com/fondos-comunes-de-inversion); the account balance is not invested in one class, and no class is named. |
| Naranja X | The remunerated account pays its own fixed TNA (https://www.naranjax.com/cuenta-remunerada); it mentions FCIs only as a comparison. |
| Lemon | The yield offered on the official page is for digital dollars in DeFi protocols (https://lemon.me/); no peso fund is named. |
| Belo | The official page (https://www.belo.app/) is rendered by script and the text we could read names no fund or peso yield, so nothing confirms a class. |
| Ualá | Already a direct rate source (`UalaWalletAdapter`). |

## Adding a wallet

1. Run both checks above and record the URL in this file.
2. Add the verbatim live row of the class to a new dated fixture excerpt (do not edit the recorded fixtures).
3. Test first: the catalog maps the wallet, a ranking shows it as `WALLET` once and not also as `FUND`, and a missing
   class reads `FUND_CLASS_NOT_FOUND`.
4. Add the entry to `fci-backed-wallets.json` and mention the wallet in `web/src/pages/metodologia.astro`.
