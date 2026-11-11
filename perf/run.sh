#!/usr/bin/env bash
# T3b: load test of the public read API, with the same data and the same load every run, so "before" and "after"
# (T3a: targeted simulate, ranking cache, ETag) are comparable.
#
#   perf/run.sh before      # label of the result files
#   perf/run.sh after
#
# What it does, in order (everything stops again on exit, even on failure):
#   1. takes /tmp/rindetuplata-maven.lock for the whole run (one Maven run at a time; it also keeps another
#      suite from skewing the timings) and builds the jar with ./mvnw (skip with PERF_JAR=<path>);
#   2. starts PostgreSQL 18 (docker, 127.0.0.1:$PG_PORT) and perf/mock_sources.py (127.0.0.1:$MOCK_PORT), a local stand-in
#      for the nine external sources serving recorded captures: no live HTTP;
#   3. starts the jar with RINDETUPLATA_INGEST_RUNONSTARTUP=true, so the application ingests "today" from the mock
#      sources exactly as the daily job does, then waits for that ingestion to finish;
#   4. seeds 21 days of synthetic fund history (perf/seed-fund-history.sql) and prints the data counts;
#   5. runs k6 (perf/read-api.js) with fixed VUs and duration and writes $OUT_DIR/<label>.{json,txt}.
#
# Rate limit: the public read API answers 429 past 120 requests per address. The app starts with
# RINDETUPLATA_API_RATELIMIT_BYPASSTOKEN=$BUILD_TOKEN and k6 sends X-Build-Token: the same bypass the static site build
# uses (docs/deploy.md, "Build token"). k6 counts every 429 as a failure, and a threshold fails the run if one happens.
#
# Needs: docker (rootless is fine), java 21, k6, jq, python3, and the postgres:18-alpine image. Run from any directory.
set -uo pipefail

LABEL=${1:?usage: perf/run.sh <label, e.g. before|after>}
ROOT=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
WORK=$(mktemp -d)
OUT_DIR=$(realpath -m "${OUT_DIR:-$WORK}")

# The scenario. Keep these fixed between before and after.
export RANKING_VUS=${RANKING_VUS:-8} SIMULATION_VUS=${SIMULATION_VUS:-4} REVALIDATE_VUS=${REVALIDATE_VUS:-2}
export DURATION=${DURATION:-60s}
FUND_CLASSES=${FUND_CLASSES:-620}       # fund classes served by the mock (32 recorded + synthetic padding)
DATA_DAY=${DATA_DAY:-2026/10/31}        # day of the data-branch capture used for plazo fijo, wallets and crypto
JAVA_OPTS=${JAVA_OPTS:--Xms1g -Xmx1g}

PG_PORT=${PG_PORT:-55432} MOCK_PORT=${MOCK_PORT:-18081} API_PORT=${API_PORT:-18080}
BUILD_TOKEN=${BUILD_TOKEN:-perf-load-test-token}
PG_NAME=rindetuplata-perf-pg
PIDS=()

cleanup() {
	rc=$?
	for pid in "${PIDS[@]}"; do kill "$pid" 2>/dev/null; done
	for pid in "${PIDS[@]}"; do wait "$pid" 2>/dev/null; done
	docker stop "$PG_NAME" >/dev/null 2>&1   # started with --rm
	echo "stopped app, mock sources and postgres; logs and data are in $WORK"
	exit "$rc"
}
trap cleanup EXIT
trap 'exit 130' INT TERM

fail() { echo "perf/run.sh: $*" >&2; exit 1; }
psql_() { docker exec -i "$PG_NAME" psql -U rindetuplata -d rindetuplata -tA -v ON_ERROR_STOP=1 "$@"; }
wait_for() {  # wait_for <seconds> <description> <command...>
	local limit=$1 what=$2; shift 2
	for ((i = 0; i < limit; i++)); do "$@" >/dev/null 2>&1 && return 0; sleep 1; done
	fail "timed out waiting for $what (logs in $WORK)"
}

for tool in docker java k6 jq python3 curl flock; do command -v "$tool" >/dev/null || fail "missing $tool"; done
docker inspect "$PG_NAME" >/dev/null 2>&1 && fail "container $PG_NAME already exists; stop it first"

# 1. Maven lock + jar. The lock lives on fd 9; the long-running children are started with 9>&- so a leftover one can
#    never keep the lock after this script is gone.
exec 9>/tmp/rindetuplata-maven.lock
echo "waiting for /tmp/rindetuplata-maven.lock ..."
flock 9
JAR=${PERF_JAR:-}
if [[ -z $JAR ]]; then
	(cd "$ROOT" && ./mvnw -B -q -DskipTests package) > "$WORK/build.log" 2>&1 || { tail -30 "$WORK/build.log"; fail "build failed"; }
	JAR=$(ls "$ROOT"/target/rindetuplata-*.jar | grep -v plain | head -1)
fi
[[ -f $JAR ]] || fail "no jar at $JAR"

# 2. Data, PostgreSQL, mock sources.
DATA=$WORK/data; mkdir -p "$DATA"
FIX=$ROOT/src/test/resources/fixtures
ARCHIVE_REF=${ARCHIVE_REF:-origin/data}
for pair in bcra-transparencia-plazos-fijos:bcra-plazos-fijos.json argentinadatos-plazo-fijo:argentinadatos-plazo-fijo.json \
		argentinadatos-rendimientos:argentinadatos-rendimientos.json; do
	git -C "$ROOT" show "$ARCHIVE_REF:raw/$DATA_DAY/${pair%%:*}.json" > "$DATA/${pair##*:}" \
		|| fail "cannot read $ARCHIVE_REF:raw/$DATA_DAY/${pair%%:*}.json (git fetch origin data?)"
done
cp "$FIX/argentinadatos/inflacion.json" "$FIX/argentinadatos/fci-mercado-dinero-ultimo.json" \
	"$FIX/argentinadatos/fci-mercado-dinero-penultimo.json" "$FIX/bcra/monetarias-27.json" "$FIX/bcra/monetarias-29.json" \
	"$FIX/bcra/monetarias-1196.json" "$FIX/dolarapi/dolar-bolsa.json" "$DATA/"
cp "$FIX/uala/cuenta-remunerada.html" "$DATA/uala-cuenta-remunerada.html"

docker run -d --rm --name "$PG_NAME" -p "127.0.0.1:$PG_PORT:5432" -e POSTGRES_DB=rindetuplata -e POSTGRES_USER=rindetuplata \
	-e POSTGRES_PASSWORD=rindetuplata postgres:18-alpine > "$WORK/pg.log" 2>&1 || { cat "$WORK/pg.log"; fail "postgres did not start"; }
wait_for 60 postgres docker exec "$PG_NAME" pg_isready -U rindetuplata -d rindetuplata

python3 -I "$ROOT/perf/mock_sources.py" "$DATA" "$MOCK_PORT" "$FUND_CLASSES" > "$WORK/mock.log" 2>&1 9>&- &
PIDS+=($!)
wait_for 20 "mock sources" curl -sf "http://127.0.0.1:$MOCK_PORT/dolarapi/dolares/bolsa"

# 3. The application, ingesting today from the mock sources.
M=http://127.0.0.1:$MOCK_PORT
env SERVER_PORT="$API_PORT" \
	SPRING_DATASOURCE_URL="jdbc:postgresql://127.0.0.1:$PG_PORT/rindetuplata" SPRING_DATASOURCE_USERNAME=rindetuplata \
	SPRING_DATASOURCE_PASSWORD=rindetuplata \
	RINDETUPLATA_SOURCES_BCRATRANSPARENCYURL="$M/bcra-transparency" RINDETUPLATA_SOURCES_BCRASTATISTICSURL="$M/bcra-statistics" \
	RINDETUPLATA_SOURCES_ARGENTINADATOSURL="$M/argentinadatos" RINDETUPLATA_SOURCES_DOLARAPIURL="$M/dolarapi" \
	RINDETUPLATA_SOURCES_UALAURL="$M/uala" \
	RINDETUPLATA_INGEST_RUNONSTARTUP=true RINDETUPLATA_INGEST_ENABLED=true \
	RINDETUPLATA_API_RATELIMIT_BYPASSTOKEN="$BUILD_TOKEN" \
	RINDETUPLATA_OUTBOX_RELAYENABLED=false RINDETUPLATA_OUTBOX_CONSUMERENABLED=false \
	SPRING_MAIL_HOST=127.0.0.1 MANAGEMENT_HEALTH_MAIL_ENABLED=false MANAGEMENT_HEALTH_RABBIT_ENABLED=false \
	java $JAVA_OPTS -jar "$JAR" > "$WORK/app.log" 2>&1 9>&- &
PIDS+=($!)
wait_for 180 "the API to answer" curl -sf "http://127.0.0.1:$API_PORT/actuator/health"
ingestion_completed() { [[ $(psql_ -c "select count(*) from batch_job_execution where status = 'COMPLETED'") == 1 ]]; }
wait_for 180 "the startup ingestion to complete" ingestion_completed

# 4. Fund history, then what the run holds.
psql_ < "$ROOT/perf/seed-fund-history.sql" > "$WORK/seed.log" 2>&1 || { cat "$WORK/seed.log"; fail "fund history seed failed"; }
echo "--- data of this run ---"
psql_ -c "select 'instruments ' || kind || ': ' || count(*) from instrument group by kind order by kind"
echo "fund history seed: $(grep -o 'INSERT 0 [0-9]*' "$WORK/seed.log") synthetic quotes"
psql_ -c "select 'fund_quote rows in total: ' || count(*) || ', classes quoted: ' || count(distinct instrument_id) from fund_quote"
psql_ -c "select 'rate_snapshot capture_date ' || capture_date || ': ' || count(*) from rate_snapshot group by capture_date order by capture_date"
for query in "limit=1" "limit=1&groupFunds=false"; do
	curl -sf "http://127.0.0.1:$API_PORT/api/v1/ranking?$query" -H "X-Build-Token: $BUILD_TOKEN" \
		| jq -r --arg q "$query" '"ranking?\($q): ranked=\(.totals.ranked) ineligible=\(.totals.ineligible) excluded=\(.totals.excluded) asOf=\(.request.asOf)"'
done

# 5. The load. PERF_HOLD=<seconds> keeps the stack up for that long instead, for poking at it by hand.
if [[ -n ${PERF_HOLD:-} ]]; then echo "holding the stack for $PERF_HOLD s: API on 127.0.0.1:$API_PORT"; sleep "$PERF_HOLD" 9>&- & PIDS+=($!); wait $!; exit 0; fi
echo "--- k6: $RANKING_VUS ranking + $SIMULATION_VUS simulation + $REVALIDATE_VUS revalidation VUs, $DURATION ---"
mkdir -p "$OUT_DIR"
# k6 runs in the background and is waited for, so a TERM to this script reaches the trap at once (bash defers a trap
# while a foreground command runs).
(cd "$ROOT/perf" && exec env BASE_URL="http://127.0.0.1:$API_PORT" BUILD_TOKEN="$BUILD_TOKEN" SUMMARY_JSON="$OUT_DIR/$LABEL.json" \
	SUMMARY_TXT="$OUT_DIR/$LABEL.txt" k6 run --quiet read-api.js) 9>&- &
PIDS+=($!)
wait $!
K6_RC=$?
echo "k6 exit code: $K6_RC (results in $OUT_DIR/$LABEL.{json,txt}, app log $WORK/app.log)"
exit "$K6_RC"
