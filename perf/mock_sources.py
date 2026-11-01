#!/usr/bin/env python3
"""Local stand-in for the external data sources, so the load test never touches live HTTP.

Serves, on 127.0.0.1 only, every path the ingestion adapters call (one prefix per source):

  /bcra-transparency/PlazosFijos             <- DATA_DIR/bcra-plazos-fijos.json       (recorded, data branch)
  /argentinadatos/tasas/plazoFijo            <- DATA_DIR/argentinadatos-plazo-fijo.json (recorded, data branch)
  /argentinadatos/rendimientos               <- DATA_DIR/argentinadatos-rendimientos.json (recorded, data branch)
  /argentinadatos/indices/inflacion          <- DATA_DIR/inflacion.json               (recorded fixture)
  /bcra-statistics/monetarias/{27,29,1196}   <- DATA_DIR/monetarias-<id>.json         (recorded fixtures)
  /dolarapi/dolares/bolsa                    <- DATA_DIR/dolar-bolsa.json             (recorded fixture)
  /uala/inversiones/cuenta-remunerada        <- DATA_DIR/uala-cuenta-remunerada.html  (hand-trimmed fixture)
  /argentinadatos/fci/mercadoDinero/{ultimo,penultimo}
        the 32 RECORDED fund classes of the fixture, re-dated to the last two business days, plus
        SYNTHETIC classes up to FUND_CLASSES in total (the source lists about 620 and no archive holds them).

Usage: mock_sources.py DATA_DIR PORT [FUND_CLASSES]
"""
import json
import sys
from datetime import date, timedelta
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from urllib.parse import urlparse

DATA_DIR = Path(sys.argv[1])
PORT = int(sys.argv[2])
FUND_CLASSES = int(sys.argv[3]) if len(sys.argv) > 3 else 620

STATIC = {
    "/bcra-transparency/PlazosFijos": ("bcra-plazos-fijos.json", "application/json"),
    "/argentinadatos/tasas/plazoFijo": ("argentinadatos-plazo-fijo.json", "application/json"),
    "/argentinadatos/rendimientos": ("argentinadatos-rendimientos.json", "application/json"),
    "/argentinadatos/indices/inflacion": ("inflacion.json", "application/json"),
    "/bcra-statistics/monetarias/27": ("monetarias-27.json", "application/json"),
    "/bcra-statistics/monetarias/29": ("monetarias-29.json", "application/json"),
    "/bcra-statistics/monetarias/1196": ("monetarias-1196.json", "application/json"),
    "/dolarapi/dolares/bolsa": ("dolar-bolsa.json", "application/json"),
    "/uala/inversiones/cuenta-remunerada": ("uala-cuenta-remunerada.html", "text/html; charset=utf-8"),
}


def last_business_days(today, count):
    days, day = [], today - timedelta(days=1)
    while len(days) < count:
        if day.weekday() < 5:
            days.append(day)
        day -= timedelta(days=1)
    return days


def fund_payloads(today):
    """(latest, previous): recorded classes re-dated, dead ones kept as recorded, then synthetic padding."""
    latest_day, previous_day = last_business_days(today, 2)
    recorded = {
        "ultimo": json.loads((DATA_DIR / "fci-mercado-dinero-ultimo.json").read_text()),
        "penultimo": json.loads((DATA_DIR / "fci-mercado-dinero-penultimo.json").read_text()),
    }
    newest = max(row["fecha"] for row in recorded["ultimo"])
    out = {"ultimo": [], "penultimo": []}
    for key, day in (("ultimo", latest_day), ("penultimo", previous_day)):
        for row in recorded[key]:
            row = dict(row)
            if row["fecha"] >= (date.fromisoformat(newest) - timedelta(days=7)).isoformat():
                row["fecha"] = day.isoformat()  # a live class: quoted on the last business days
            out[key].append(row)
    # Synthetic classes: deterministic, daily growth between 0.04% and 0.07% (about 15% to 26% TNA).
    for index in range(FUND_CLASSES - len(recorded["ultimo"])):
        fund, cls = index // 3 + 1, "ABC"[index % 3]
        growth = 1 + (4 + (index * 7) % 4) / 10000
        vcp = 1000 + (index * 37) % 9000
        for key, day, value in (("penultimo", previous_day, vcp), ("ultimo", latest_day, vcp * growth)):
            out[key].append({
                "fondo": f"Synthetic Money Market {fund:03d} - Clase {cls}",
                "horizonte": "corto",
                "fecha": day.isoformat(),
                "vcp": round(value, 3),
                "ccp": 1000000000 + index,
                "patrimonio": 100000000000 + index * 1000,
            })
    return out


class Handler(BaseHTTPRequestHandler):
    funds = None

    def do_GET(self):
        path = urlparse(self.path).path
        if path in STATIC:
            name, content_type = STATIC[path]
            body = (DATA_DIR / name).read_bytes()
        elif path in ("/argentinadatos/fci/mercadoDinero/ultimo", "/argentinadatos/fci/mercadoDinero/penultimo"):
            body = json.dumps(Handler.funds[path.rsplit("/", 1)[1]]).encode()
            content_type = "application/json"
        else:
            self.send_error(404)
            return
        self.send_response(200)
        self.send_header("Content-Type", content_type)
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def log_message(self, fmt, *args):
        print("mock-sources:", fmt % args, flush=True)


if __name__ == "__main__":
    Handler.funds = fund_payloads(date.today())
    print(f"mock-sources: {len(Handler.funds['ultimo'])} fund classes, listening on 127.0.0.1:{PORT}", flush=True)
    ThreadingHTTPServer(("127.0.0.1", PORT), Handler).serve_forever()
