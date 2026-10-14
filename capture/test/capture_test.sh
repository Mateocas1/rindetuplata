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

# --- empty or comment-only sources file ------------------------------------------
: > "$work/empty.conf"
"$script" "$work/empty.conf" "$work/empty-out" "$day" >"$work/empty.stdout" 2>"$work/empty.stderr"; code=$?
check "empty: empty sources file exit 2" '[ "$code" -eq 2 ]'
check "empty: empty sources file message on stderr" 'grep -q "no sources in sources file" "$work/empty.stderr"'
check "empty: empty sources file writes nothing" '[ ! -e "$work/empty-out" ]'
check "empty: no summary on stdout" '[ ! -s "$work/empty.stdout" ]'

printf '# only comments\n\n   \n' > "$work/comments.conf"
"$script" "$work/comments.conf" "$work/comments-out" "$day" >/dev/null 2>"$work/comments.stderr"; code=$?
check "empty: comment-only sources file exit 2" '[ "$code" -eq 2 ]'
check "empty: comment-only writes nothing" '[ ! -e "$work/comments-out" ]'

# --- degraded-but-valid json never replaces a good capture ------------------------
cat > "$work/degraded.conf" <<EOF
bcra-plazos-fijos|json|file://$fixtures/degraded.json
EOF
cat > "$work/degraded-results.conf" <<EOF
bcra-plazos-fijos|json|file://$fixtures/degraded-results.json
EOF

degraded="$work/degraded"
"$script" "$work/ok.conf" "$degraded" "$day" >/dev/null 2>&1
"$script" "$work/degraded.conf" "$degraded" "$day" >"$work/degraded.stdout" 2>"$work/degraded.stderr"; code=$?
check "degraded: empty json body exit 1" '[ "$code" -eq 1 ]'
check "degraded: good capture kept" 'cmp -s "$fixtures/plazos-fijos.json" "$degraded/$day_dir/bcra-plazos-fijos.json"'
check "degraded: manifest keeps previous capture" '[ "$(jq -r ".sources[] | select(.name == \"bcra-plazos-fijos\") | .kept_previous" "$degraded/$day_dir/manifest.json")" = "true" ]'
check "degraded: reason names the degraded body" '[ "$(jq -r ".sources[] | select(.name == \"bcra-plazos-fijos\") | .error" "$degraded/$day_dir/manifest.json")" = "degraded json: empty body" ]'
check "degraded: kept sha256 is of the kept file" '[ "$(jq -r ".sources[] | select(.name == \"bcra-plazos-fijos\") | .sha256" "$degraded/$day_dir/manifest.json")" = "$(sha256sum "$degraded/$day_dir/bcra-plazos-fijos.json" | cut -d" " -f1)" ]'
check "degraded: reported on stderr" 'grep -q "failed: bcra-plazos-fijos (degraded json: empty body)" "$work/degraded.stderr"'

"$script" "$work/degraded-results.conf" "$degraded" "$day" >/dev/null 2>&1; code=$?
check "degraded: a 200 with an empty results list is degraded too" '[ "$code" -eq 1 ] && cmp -s "$fixtures/plazos-fijos.json" "$degraded/$day_dir/bcra-plazos-fijos.json"'

"$script" "$work/degraded.conf" "$work/degraded-first" "$day" >/dev/null 2>&1; code=$?
check "degraded: degraded body never stored as a capture" '[ "$code" -eq 1 ] && [ ! -e "$work/degraded-first/$day_dir/bcra-plazos-fijos.json" ]'

# --- strict UTF-8 decoding in extract_rates.py ------------------------------------
python3 -I "$here/../extract_rates.py" < "$fixtures/invalid-utf8.html" >/dev/null 2>"$work/utf8.raw.err"; code=$?
check "utf8: extract_rates.py exits non-zero on invalid bytes" '[ "$code" -ne 0 ]'
check "utf8: extract_rates.py message names invalid utf-8" 'grep -q "invalid utf-8 input" "$work/utf8.raw.err"'
check "utf8: extract_rates.py prints no traceback" '! grep -q "Traceback" "$work/utf8.raw.err"'

cat > "$work/utf8.conf" <<EOF
invalid-utf8|html-rate|file://$fixtures/invalid-utf8.html
EOF
utf8="$work/utf8"
"$script" "$work/utf8.conf" "$utf8" "$day" >/dev/null 2>"$work/utf8.stderr"; code=$?
check "utf8: invalid page fails its source" '[ "$code" -eq 1 ]'
check "utf8: reason carries the decoder message" 'jq -r ".sources[0].error" "$utf8/$day_dir/manifest.json" | grep -q "^html parse failed: invalid utf-8 input"'
check "utf8: nothing stored" '[ ! -e "$utf8/$day_dir/invalid-utf8.json" ]'
check "utf8: failure reported on stderr" 'grep -q "failed: invalid-utf8" "$work/utf8.stderr"'

# --- response size cap ------------------------------------------------------------
cap_exact="$(wc -c < "$fixtures/plazos-fijos.json")"
cat > "$work/cap-boundary.conf" <<EOF
bcra-plazos-fijos|json|file://$fixtures/plazos-fijos.json
EOF
CAPTURE_MAX_BYTES="$cap_exact" "$script" "$work/cap-boundary.conf" "$work/cap-boundary" "$day" >/dev/null 2>&1; code=$?
check "cap: a body exactly at the limit is accepted" '[ "$code" -eq 0 ]'

cap="$work/cap"
CAPTURE_MAX_BYTES=200 "$script" "$work/ok.conf" "$cap" "$day" >"$work/cap.stdout" 2>"$work/cap.stderr"; code=$?
check "cap: exit 1 when one body is oversized" '[ "$code" -eq 1 ]'
check "cap: the small source is still captured" 'cmp -s "$fixtures/plazos-fijos.json" "$cap/$day_dir/bcra-plazos-fijos.json"'
check "cap: the oversized source is not stored" '[ ! -e "$cap/$day_dir/uala-cuenta-remunerada.json" ]'
check "cap: reason names the size and the limit" '[ "$(jq -r ".sources[] | select(.name == \"uala-cuenta-remunerada\") | .error" "$cap/$day_dir/manifest.json")" = "response too large: $(wc -c < "$fixtures/uala.html") bytes (limit 200)" ]'
check "cap: summary counts only the captured source" 'grep -q "captured 1/2 sources" "$work/cap.stdout"'

CAPTURE_MAX_BYTES=notanumber "$script" "$work/ok.conf" "$work/cap-bad" "$day" >/dev/null 2>"$work/cap-bad.err"; code=$?
check "cap: invalid cap exits 2" '[ "$code" -eq 2 ]'
check "cap: invalid cap message" 'grep -q "invalid CAPTURE_MAX_BYTES: notanumber" "$work/cap-bad.err"'
check "cap: invalid cap writes nothing" '[ ! -e "$work/cap-bad" ]'

# --- a partial rerun keeps the manifest entries of sources not rerun -------------- 
cat > "$work/one.conf" <<EOF
bcra-plazos-fijos|json|file://$fixtures/plazos-fijos.json
EOF
merge="$work/merge"
"$script" "$work/ok.conf" "$merge" "$day" >/dev/null 2>&1
"$script" "$work/one.conf" "$merge" "$day" >"$work/merge.stdout" 2>"$work/merge.stderr"; code=$?
check "merge: partial rerun exits 0" '[ "$code" -eq 0 ]'
check "merge: manifest still lists both sources" '[ "$(jq ".sources | length" "$merge/$day_dir/manifest.json")" -eq 2 ]'
check "merge: no duplicate entries" '[ "$(jq "[.sources[].name] | unique | length" "$merge/$day_dir/manifest.json")" -eq 2 ]'
check "merge: rerun source recorded once as ok" '[ "$(jq "[.sources[] | select(.name == \"bcra-plazos-fijos\" and .status == \"ok\")] | length" "$merge/$day_dir/manifest.json")" -eq 1 ]'
check "merge: source not rerun keeps its entry and sha256" '[ "$(jq -r ".sources[] | select(.name == \"uala-cuenta-remunerada\") | .sha256" "$merge/$day_dir/manifest.json")" = "$(sha256sum "$merge/$day_dir/uala-cuenta-remunerada.json" | cut -d" " -f1)" ]'
check "merge: summary counts only the rerun sources" 'grep -q "captured 1/1 sources" "$work/merge.stdout"'

corrupt="$work/corrupt"
mkdir -p "$corrupt/$day_dir"
printf '{not json' > "$corrupt/$day_dir/manifest.json"
"$script" "$work/one.conf" "$corrupt" "$day" >/dev/null 2>"$work/corrupt.stderr"; code=$?
check "merge: corrupt previous manifest exits 0" '[ "$code" -eq 0 ]'
check "merge: corrupt previous manifest warns on stderr" 'grep -q "cannot read previous manifest" "$work/corrupt.stderr"'
check "merge: corrupt previous manifest is rebuilt from this run" '[ "$(jq ".sources | length" "$corrupt/$day_dir/manifest.json")" -eq 1 ]'

# --- failed writes exit non-zero and leave the previous manifest intact -----------
ro="$work/ro"
cp -r "$merge" "$ro"
chmod 500 "$ro/$day_dir"
ro_manifest_before="$(cat "$ro/$day_dir/manifest.json")"
ro_bcra_before="$(sha256sum "$ro/$day_dir/bcra-plazos-fijos.json" | cut -d" " -f1)"
"$script" "$work/one.conf" "$ro" "$day" >"$work/ro.stdout" 2>"$work/ro.stderr"; code=$?
check "write: unwritable day dir exits non-zero" '[ "$code" -ne 0 ]'
check "write: result write failure reported on stderr" 'grep -q "(write failed)" "$work/ro.stderr"'
check "write: manifest write failure reported on stderr" 'grep -q "cannot write manifest" "$work/ro.stderr"'
check "write: no summary claims success" '! grep -q "captured" "$work/ro.stdout"'
check "atomic: previous manifest left byte-identical" '[ "$(cat "$ro/$day_dir/manifest.json")" = "$ro_manifest_before" ]'
check "atomic: previous manifest still complete" '[ "$(jq ".sources | length" "$ro/$day_dir/manifest.json")" -eq 2 ]'
check "atomic: no half-written manifest left in the day dir" '[ ! -e "$ro/$day_dir/manifest.json.tmp" ] && [ -z "$(find "$ro/$day_dir" -name "*.tmp" -o -name ".manifest*")" ]'
check "write: earlier capture untouched" '[ "$(sha256sum "$ro/$day_dir/bcra-plazos-fijos.json" | cut -d" " -f1)" = "$ro_bcra_before" ]'
chmod 700 "$ro/$day_dir"

# --- duplicate names and unknown kinds --------------------------------------------
cat > "$work/dup.conf" <<EOF
bcra-plazos-fijos|json|file://$fixtures/plazos-fijos.json
bcra-plazos-fijos|json|file://$fixtures/broken.json
EOF
dup="$work/dup"
"$script" "$work/dup.conf" "$dup" "$day" >"$work/dup.stdout" 2>"$work/dup.stderr"; code=$?
check "dup: exit 1" '[ "$code" -eq 1 ]'
check "dup: second occurrence rejected as duplicate" '[ "$(jq -r ".sources[1].error" "$dup/$day_dir/manifest.json")" = "duplicate source name" ]'
check "dup: first occurrence captured" 'cmp -s "$fixtures/plazos-fijos.json" "$dup/$day_dir/bcra-plazos-fijos.json"'
check "dup: summary counts both attempts" 'grep -q "captured 1/2 sources" "$work/dup.stdout"'

cat > "$work/kind.conf" <<EOF
weird-kind|xml|file://$fixtures/plazos-fijos.json
EOF
kind="$work/kind"
"$script" "$work/kind.conf" "$kind" "$day" >/dev/null 2>"$work/kind.stderr"; code=$?
check "kind: unknown kind exit 1" '[ "$code" -eq 1 ]'
check "kind: unknown kind reason" '[ "$(jq -r ".sources[0].error" "$kind/$day_dir/manifest.json")" = "unknown kind: xml" ]'
check "kind: unknown kind stores nothing" '[ ! -e "$kind/$day_dir/weird-kind.json" ]'

if [ "$failures" -gt 0 ]; then
  printf '\n%d check(s) failed\n' "$failures"
  exit 1
fi
printf '\nall checks passed\n'
