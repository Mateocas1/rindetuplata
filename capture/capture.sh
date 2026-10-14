#!/usr/bin/env bash
# Archive one day of raw responses from sources that keep no history of their own.
#
# usage: capture.sh <sources-file> <output-root> <YYYY-MM-DD>
#
# Each non-comment line of the sources file is `name|kind|url` (name: lowercase letters, digits, dashes):
#   json       store the response body byte for byte after checking it is valid JSON
#   html-rate  store only the snippets that announce a TNA rate, plus the page sha256
#
# Output: <output-root>/raw/YYYY/MM/DD/<name>.json and manifest.json. Re-running a day replaces
# that day's files; a failing source never deletes an earlier good capture of the same day.
# Exit codes: 0 all sources captured, 1 at least one source failed, 2 usage error.
set -uo pipefail

usage() {
  echo "usage: capture.sh <sources-file> <output-root> <YYYY-MM-DD>" >&2
  exit 2
}

[ "$#" -eq 3 ] || usage
sources_file="$1"
output_root="$2"
day="$3"

if ! [[ "$day" =~ ^[0-9]{4}-[0-9]{2}-[0-9]{2}$ ]] || ! date -d "$day" +%F >/dev/null 2>&1; then
  echo "invalid date: $day" >&2
  exit 2
fi
if [ ! -r "$sources_file" ]; then
  echo "cannot read sources file: $sources_file" >&2
  exit 2
fi

here="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
day_dir="$output_root/raw/${day:0:4}/${day:5:2}/${day:8:2}"
tmp="$(mktemp -d)"
trap 'rm -rf "$tmp"' EXIT
if ! mkdir -p "$day_dir" 2>/dev/null; then
  echo "cannot create output directory: $day_dir" >&2
  exit 2
fi

user_agent="rindetuplata-capture/1.0 (+https://github.com/Mateocas1/rindetuplata)"
entries="$tmp/entries.jsonl"
: > "$entries"
total=0
captured=0

record() { # name url status error sha256 bytes kept_previous
  jq -cn --arg name "$1" --arg url "$2" --arg status "$3" --arg error "$4" \
    --arg sha256 "$5" --arg bytes "$6" --argjson kept "$7" \
    '{name: $name, url: $url, status: $status}
     + (if $error == "" then {} else {error: $error} end)
     + (if $sha256 == "" then {} else {sha256: $sha256, bytes: ($bytes | tonumber)} end)
     + (if $kept then {kept_previous: true} else {} end)' >> "$entries"
}

fail_source() { # name url error; an earlier good capture of the day is kept and described
  local kept_file="$day_dir/$1.json"
  echo "failed: $1 ($3)" >&2
  if [ -f "$kept_file" ]; then
    record "$1" "$2" failed "$3" "$(sha256sum "$kept_file" | cut -d' ' -f1)" "$(wc -c < "$kept_file")" true
  else
    record "$1" "$2" failed "$3" "" "" false
  fi
}

seen=" "
while IFS= read -r line || [ -n "$line" ]; do
  line="${line%$'\r'}"
  trimmed="${line#"${line%%[![:space:]]*}"}"
  case "$trimmed" in ''|'#'*) continue ;; esac
  IFS='|' read -r name kind url <<< "$trimmed"
  total=$((total + 1))

  if ! [[ "$name" =~ ^[a-z0-9][a-z0-9-]*$ ]]; then
    echo "failed: $name (invalid source name)" >&2
    record "$name" "$url" failed "invalid source name" "" "" false
    continue
  fi
  if [[ "$seen" == *" $name "* ]]; then
    echo "failed: $name (duplicate source name)" >&2
    record "$name" "$url" failed "duplicate source name" "" "" false
    continue
  fi
  seen+="$name "
  body="$tmp/$name.body"
  result="$tmp/$name.json"

  if ! curl --silent --show-error --fail --location --max-time 45 --retry 2 --retry-delay 5 \
      --user-agent "$user_agent" --output "$body" --url "$url" 2>"$tmp/$name.err"; then
    fail_source "$name" "$url" "fetch failed: $(tail -n 1 "$tmp/$name.err")"
    continue
  fi

  case "$kind" in
    json)
      if ! jq -e . "$body" >/dev/null 2>&1; then
        fail_source "$name" "$url" "invalid json"
        continue
      fi
      cp "$body" "$result"
      ;;
    html-rate)
      matches="$(python3 -I "$here/extract_rates.py" < "$body")" || {
        fail_source "$name" "$url" "html parse failed"
        continue
      }
      if [ "$(jq 'length' <<< "$matches")" -eq 0 ]; then
        fail_source "$name" "$url" "no TNA rate found in page"
        continue
      fi
      jq -n --arg url "$url" --arg sha "$(sha256sum "$body" | cut -d' ' -f1)" \
        --argjson bytes "$(wc -c < "$body")" --argjson matches "$matches" \
        '{url: $url, html_sha256: $sha, html_bytes: $bytes, matches: $matches}' > "$result"
      ;;
    *)
      fail_source "$name" "$url" "unknown kind: $kind"
      continue
      ;;
  esac

  if ! mv "$result" "$day_dir/$name.json" 2>/dev/null; then
    fail_source "$name" "$url" "write failed"
    continue
  fi
  record "$name" "$url" ok "" "$(sha256sum "$day_dir/$name.json" | cut -d' ' -f1)" \
    "$(wc -c < "$day_dir/$name.json")" false
  captured=$((captured + 1))
done < "$sources_file"

jq -s --arg date "$day" --arg captured_at "$(date -u +%Y-%m-%dT%H:%M:%SZ)" \
  '{date: $date, captured_at: $captured_at, sources: .}' "$entries" > "$day_dir/manifest.json" || {
  echo "cannot write manifest: $day_dir/manifest.json" >&2
  exit 1
}

echo "captured $captured/$total sources for $day"
[ "$captured" -eq "$total" ]
