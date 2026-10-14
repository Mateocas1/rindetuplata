#!/usr/bin/env bash
# Deterministic tests for capture/capture.sh. Sources point to local fixtures via file:// URLs,
# so no test touches the network.
set -uo pipefail

here="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
script="$here/../capture.sh"
fixtures="$here/fixtures"
work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT

failures=0
pass() { printf 'ok   %s\n' "$1"; }
fail() { printf 'FAIL %s\n' "$1"; failures=$((failures + 1)); }
check() { if eval "$2"; then pass "$1"; else fail "$1"; fi; }

day="2026-10-14"
day_dir="raw/2026/10/14"

cat > "$work/ok.conf" <<EOF
# name|kind|url
bcra-plazos-fijos|json|file://$fixtures/plazos-fijos.json
uala-cuenta-remunerada|html-rate|file://$fixtures/uala.html
EOF

cat > "$work/mixed.conf" <<EOF
bcra-plazos-fijos|json|file://$fixtures/plazos-fijos.json
broken-json|json|file://$fixtures/broken.json
missing|json|file://$fixtures/does-not-exist.json
no-rate-page|html-rate|file://$fixtures/no-rate.html
EOF

# --- usage errors ---------------------------------------------------------------
"$script" >"$work/usage.out" 2>"$work/usage.err"; code=$?
check "usage: missing arguments exit 2" '[ "$code" -eq 2 ]'
check "usage: message on stderr" 'grep -q "usage: capture.sh <sources-file> <output-root> <YYYY-MM-DD>" "$work/usage.err"'
check "usage: nothing on stdout" '[ ! -s "$work/usage.out" ]'

"$script" "$work/ok.conf" "$work/bad-date" "14-10-2026" >/dev/null 2>"$work/date.err"; code=$?
check "usage: invalid date exit 2" '[ "$code" -eq 2 ]'
check "usage: invalid date message" 'grep -q "invalid date: 14-10-2026" "$work/date.err"'
check "usage: invalid date writes nothing" '[ ! -e "$work/bad-date" ]'

# --- all sources ok -------------------------------------------------------------
out="$work/ok"
"$script" "$work/ok.conf" "$out" "$day" >"$work/ok.stdout" 2>"$work/ok.stderr"; code=$?
check "ok: exit 0" '[ "$code" -eq 0 ]'
check "ok: json stored byte-identical" 'cmp -s "$fixtures/plazos-fijos.json" "$out/$day_dir/bcra-plazos-fijos.json"'
check "ok: html-rate stored as json" 'jq -e . "$out/$day_dir/uala-cuenta-remunerada.json" >/dev/null'
check "ok: html-rate keeps every TNA snippet" '[ "$(jq ".matches | length" "$out/$day_dir/uala-cuenta-remunerada.json")" -eq 3 ]'
check "ok: html-rate snippet text without tags" 'jq -r ".matches[]" "$out/$day_dir/uala-cuenta-remunerada.json" | grep -q "rinden 19% TNA sin hacer nada"'
check "ok: html-rate records html sha256" '[ "$(jq -r .html_sha256 "$out/$day_dir/uala-cuenta-remunerada.json")" = "$(sha256sum "$fixtures/uala.html" | cut -d" " -f1)" ]'
check "ok: html-rate does not store raw html" '! grep -q "<script>" "$out/$day_dir/uala-cuenta-remunerada.json"'
check "ok: manifest date" '[ "$(jq -r .date "$out/$day_dir/manifest.json")" = "$day" ]'
check "ok: manifest lists both sources ok" '[ "$(jq "[.sources[] | select(.status == \"ok\")] | length" "$out/$day_dir/manifest.json")" -eq 2 ]'
check "ok: manifest sha256 matches stored file" '[ "$(jq -r ".sources[] | select(.name == \"bcra-plazos-fijos\") | .sha256" "$out/$day_dir/manifest.json")" = "$(sha256sum "$out/$day_dir/bcra-plazos-fijos.json" | cut -d" " -f1)" ]'
check "ok: summary on stdout" 'grep -q "captured 2/2 sources for 2026-10-14" "$work/ok.stdout"'

# --- idempotent rerun -----------------------------------------------------------
before="$(cd "$out" && find . -type f | sort)"
"$script" "$work/ok.conf" "$out" "$day" >/dev/null 2>&1; code=$?
after="$(cd "$out" && find . -type f | sort)"
check "rerun: exit 0" '[ "$code" -eq 0 ]'
check "rerun: same file set, no duplicates" '[ "$before" = "$after" ]'
check "rerun: json still byte-identical" 'cmp -s "$fixtures/plazos-fijos.json" "$out/$day_dir/bcra-plazos-fijos.json"'

# --- failing sources are isolated -----------------------------------------------
mixed="$work/mixed"
"$script" "$work/mixed.conf" "$mixed" "$day" >"$work/mixed.stdout" 2>"$work/mixed.stderr"; code=$?
check "mixed: exit 1 when any source fails" '[ "$code" -eq 1 ]'
check "mixed: good source still stored" 'cmp -s "$fixtures/plazos-fijos.json" "$mixed/$day_dir/bcra-plazos-fijos.json"'
check "mixed: invalid json not stored" '[ ! -e "$mixed/$day_dir/broken-json.json" ]'
check "mixed: unreachable source not stored" '[ ! -e "$mixed/$day_dir/missing.json" ]'
check "mixed: page without rates not stored" '[ ! -e "$mixed/$day_dir/no-rate-page.json" ]'
check "mixed: manifest marks 3 failures" '[ "$(jq "[.sources[] | select(.status == \"failed\")] | length" "$mixed/$day_dir/manifest.json")" -eq 3 ]'
check "mixed: invalid json reason" '[ "$(jq -r ".sources[] | select(.name == \"broken-json\") | .error" "$mixed/$day_dir/manifest.json")" = "invalid json" ]'
check "mixed: no-rate reason" '[ "$(jq -r ".sources[] | select(.name == \"no-rate-page\") | .error" "$mixed/$day_dir/manifest.json")" = "no TNA rate found in page" ]'
check "mixed: failures reported on stderr" 'grep -q "failed: broken-json (invalid json)" "$work/mixed.stderr"'
check "mixed: summary on stdout" 'grep -q "captured 1/4 sources for 2026-10-14" "$work/mixed.stdout"'

# --- a failing rerun never deletes an earlier good capture of the same day ------
cat > "$work/now-broken.conf" <<EOF
bcra-plazos-fijos|json|file://$fixtures/broken.json
EOF
"$script" "$work/now-broken.conf" "$out" "$day" >/dev/null 2>&1; code=$?
check "keep: exit 1" '[ "$code" -eq 1 ]'
check "keep: earlier good capture kept" 'cmp -s "$fixtures/plazos-fijos.json" "$out/$day_dir/bcra-plazos-fijos.json"'
check "keep: manifest says previous capture kept" '[ "$(jq -r ".sources[] | select(.name == \"bcra-plazos-fijos\") | .kept_previous" "$out/$day_dir/manifest.json")" = "true" ]'
check "keep: manifest keeps sha256 of the kept file" '[ "$(jq -r ".sources[] | select(.name == \"bcra-plazos-fijos\") | .sha256" "$out/$day_dir/manifest.json")" = "$(sha256sum "$out/$day_dir/bcra-plazos-fijos.json" | cut -d" " -f1)" ]'

# --- hostile or sloppy sources files ---------------------------------------------
printf '  # indented comment\r\nbcra-plazos-fijos|json|file://%s/plazos-fijos.json\r\n../escape|json|file://%s/plazos-fijos.json\r\noption-url|json|--version\r\n' \
  "$fixtures" "$fixtures" > "$work/hostile.conf"
hostile="$work/hostile"
"$script" "$work/hostile.conf" "$hostile" "$day" >"$work/hostile.stdout" 2>/dev/null; code=$?
check "hostile: exit 1" '[ "$code" -eq 1 ]'
check "hostile: indented comment skipped" 'grep -q "captured 1/3 sources for 2026-10-14" "$work/hostile.stdout"'
check "hostile: CRLF line still captured" 'cmp -s "$fixtures/plazos-fijos.json" "$hostile/$day_dir/bcra-plazos-fijos.json"'
check "hostile: path-like name rejected" '[ "$(jq -r ".sources[] | select(.name == \"../escape\") | .error" "$hostile/$day_dir/manifest.json")" = "invalid source name" ]'
check "hostile: nothing written outside the day dir" '[ ! -e "$hostile/raw/2026/10/escape.json" ]'
check "hostile: url is never read as a curl option" 'jq -r ".sources[] | select(.name == \"option-url\") | .error" "$hostile/$day_dir/manifest.json" | grep -q "^fetch failed"'

# --- unwritable output root ------------------------------------------------------
touch "$work/not-a-dir"
"$script" "$work/ok.conf" "$work/not-a-dir" "$day" >"$work/nodir.stdout" 2>"$work/nodir.stderr"; code=$?
check "nodir: exit 2" '[ "$code" -eq 2 ]'
check "nodir: message on stderr" 'grep -q "cannot create output directory" "$work/nodir.stderr"'
check "nodir: no summary on stdout" '[ ! -s "$work/nodir.stdout" ]'

if [ "$failures" -gt 0 ]; then
  printf '\n%d check(s) failed\n' "$failures"
  exit 1
fi
printf '\nall checks passed\n'
