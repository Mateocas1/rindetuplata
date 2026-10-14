# rindetuplata data

Daily raw captures of sources that only expose today's value, written by the
`Daily raw capture` workflow on `main` (`.github/workflows/daily-capture.yml`).

Layout: `raw/YYYY/MM/DD/<source>.json` plus `raw/YYYY/MM/DD/manifest.json` with status, size and
sha256 per source. Sources and capture rules: `capture/sources.conf` and `capture/capture.sh` on `main`.
The Spring ingestion replays this archive into the database.
