// Load test of the public read API (GET /api/v1/ranking, GET /api/v1/simulations, ranking revalidation).
// Run it through perf/run.sh, which starts the application with real data; to point it at another instance:
//   BASE_URL=http://127.0.0.1:8080 BUILD_TOKEN=<bypass token> k6 run perf/read-api.js
//
// Three scenarios run together for the same fixed duration (closed model, no think time, so req/s is the capacity of the
// application on this machine):
//   ranking       RANKING_VUS virtual users, a fixed weighted mix of amount/days/kind/groupFunds combinations
//   simulation    SIMULATION_VUS virtual users, instruments of every kind cycled with a few amount/days pairs
//   revalidation  REVALIDATE_VUS virtual users: GET the default ranking, then repeat it with If-None-Match set to the
//                 ETag of the first answer. Without ETag support the repeat is an ordinary 200; with it
//                 the repeat must be a 304. Both statuses are accepted for this endpoint only.
//
// Rate limit: every request carries X-Build-Token (the application's rindetuplata.api.rate-limit.bypass-token), the
// same bypass the static site build uses. A 429 is never a success: it is an error in every endpoint's error rate and
// the `rate_limited` counter has a zero threshold.
//
// Every request is tagged `endpoint` (ranking | simulation | ranking_revalidate) and `variant` (the query shape).
import http from 'k6/http';
import exec from 'k6/execution';
import { check } from 'k6';
import { Counter } from 'k6/metrics';

const BASE_URL = __ENV.BASE_URL || 'http://127.0.0.1:8080';
const BUILD_TOKEN = __ENV.BUILD_TOKEN || '';
const DURATION = __ENV.DURATION || '60s';
const num = (name, fallback) => (__ENV[name] === undefined ? fallback : Number(__ENV[name]));

const rateLimited = new Counter('rate_limited');
const notModified = new Counter('revalidation_304');
const noEtag = new Counter('revalidation_without_etag');

// Weighted, deterministic mix: the index into this list is the iteration number modulo its length.
const RANKING_MIX = [
  ['default', ''],
  ['default', ''],
  ['default', ''],
  ['default', ''],
  ['amount500k_60d', '?amount=500000&days=60'],
  ['amount500k_60d', '?amount=500000&days=60'],
  ['amount5M_180d', '?amount=5000000&days=180'],
  ['amount250k_7d', '?amount=250000&days=7'],
  ['groupFunds_false', '?amount=1000000&days=30&groupFunds=false'],
  ['groupFunds_false', '?amount=1000000&days=30&groupFunds=false'],
  ['funds_and_plazo_fijo_90d', '?amount=2000000&days=90&kind=FUND&kind=PLAZO_FIJO'],
  ['limit200_ungrouped', '?limit=200&groupFunds=false'],
];
const SIMULATION_SHAPES = [
  ['default', ''],
  ['amount500k_60d', '&amount=500000&days=60'],
  ['amount5M_180d', '&amount=5000000&days=180'],
  ['amount250k_7d', '&amount=250000&days=7'],
];

const VARIANTS = [...new Set(RANKING_MIX.map(([name]) => name))];
const SIMULATION_VARIANTS = SIMULATION_SHAPES.map(([name]) => `sim_${name}`);

// An always-true threshold on a tagged sub-metric makes k6 report that sub-metric in the end-of-test summary.
function thresholdsFor(endpoint, p95, variants) {
  const t = {
    [`http_req_failed{endpoint:${endpoint}}`]: ['rate<0.01'],
    [`http_req_duration{endpoint:${endpoint}}`]: [`p(95)<${p95}`, 'p(99)<60000'],
    [`http_reqs{endpoint:${endpoint}}`]: ['count>0'],
  };
  for (const variant of variants) {
    t[`http_req_duration{endpoint:${endpoint},variant:${variant}}`] = ['p(99)<60000'];
    t[`http_reqs{endpoint:${endpoint},variant:${variant}}`] = ['count>0'];
  }
  return t;
}

export const options = {
  scenarios: {
    ranking: {
      executor: 'constant-vus', exec: 'ranking', vus: num('RANKING_VUS', 8), duration: DURATION,
    },
    simulation: {
      executor: 'constant-vus', exec: 'simulation', vus: num('SIMULATION_VUS', 4), duration: DURATION,
    },
    revalidation: {
      executor: 'constant-vus', exec: 'revalidation', vus: num('REVALIDATE_VUS', 2), duration: DURATION,
    },
  },
  thresholds: {
    ...thresholdsFor('ranking', 10000, VARIANTS),
    ...thresholdsFor('simulation', 10000, SIMULATION_VARIANTS),
    ...thresholdsFor('ranking_revalidate', 10000, ['default']),
    rate_limited: ['count==0'],
    checks: ['rate>0.99'],
  },
  summaryTrendStats: ['avg', 'min', 'med', 'p(50)', 'p(95)', 'p(99)', 'max'],
  discardResponseBodies: false,
};

function headers(extra) {
  const h = { Accept: 'application/json', ...extra };
  if (BUILD_TOKEN !== '') h['X-Build-Token'] = BUILD_TOKEN;
  return h;
}

function get(path, endpoint, variant, extraHeaders, expected) {
  const response = http.get(`${BASE_URL}${path}`, {
    headers: headers(extraHeaders),
    tags: { endpoint, variant },
    // Only these statuses are not errors; a 429 (or a 304 where none is expected) counts in http_req_failed.
    responseCallback: http.expectedStatuses(...expected),
  });
  if (response.status === 429) rateLimited.add(1);
  return response;
}

const SIMULATED_KINDS = ['PLAZO_FIJO', 'FUND', 'WALLET', 'CRYPTO'];

export function setup() {
  // Two ranked instruments of every kind (plus one fund class from the middle of the list), from the application's own
  // ranking, so every simulated id is one the full-basket path also answers with an entry.
  const picked = [];
  for (const kind of SIMULATED_KINDS) {
    const response = get(`/api/v1/ranking?kind=${kind}&limit=200&groupFunds=false`, 'setup', 'setup', {}, [200]);
    if (response.status !== 200) throw new Error(`setup: ranking ${kind} answered ${response.status}: ${response.body}`);
    const ranked = response.json('ranked');
    if (ranked.length === 0) throw new Error(`setup: no ranked instrument of kind ${kind}`);
    ranked.slice(0, 2).forEach((entry) => picked.push({ id: entry.id, kind }));
    if (kind === 'FUND' && ranked.length > 4) {
      picked.push({ id: ranked[Math.floor(ranked.length / 2)].id, kind });
    }
  }
  console.log(`simulating ${JSON.stringify(picked)}`);
  return { instruments: picked };
}

export function ranking() {
  const [variant, query] = RANKING_MIX[exec.scenario.iterationInTest % RANKING_MIX.length];
  const response = get(`/api/v1/ranking${query}`, 'ranking', variant, {}, [200]);
  check(response, { 'ranking 200 with rows': (r) => r.status === 200 && r.json('ranked').length > 0 });
}

export function simulation(data) {
  const n = exec.scenario.iterationInTest;
  const instrument = data.instruments[n % data.instruments.length];
  const [variant, query] = SIMULATION_SHAPES[Math.floor(n / data.instruments.length) % SIMULATION_SHAPES.length];
  const response = get(`/api/v1/simulations?instrumentId=${instrument.id}${query}`, 'simulation', `sim_${variant}`, {}, [200]);
  check(response, { 'simulation 200 with an entry': (r) => r.status === 200 && r.body.length > 2 });
}

export function revalidation() {
  const first = get('/api/v1/ranking', 'ranking', 'default', {}, [200]);
  check(first, { 'first ranking 200': (r) => r.status === 200 });
  const etag = first.headers['Etag'];
  if (!etag) noEtag.add(1);
  const again = get('/api/v1/ranking', 'ranking_revalidate', 'default', etag ? { 'If-None-Match': etag } : {}, [200, 304]);
  if (again.status === 304) notModified.add(1);
  check(again, { 'revalidation 200 or 304': (r) => r.status === 200 || r.status === 304 });
}

const ENDPOINTS = ['ranking', 'simulation', 'ranking_revalidate'];

function fixed(value, digits = 1) {
  return value === undefined ? 'n/a' : value.toFixed(digits);
}

function table(data) {
  const lines = [];
  const seconds = data.state.testRunDurationMs / 1000;
  lines.push(`k6 ${DURATION} per scenario; measured over ${fixed(seconds)} s`);
  lines.push('endpoint             requests   req/s   p50 ms   p95 ms   p99 ms   error %   (304s)');
  for (const endpoint of ENDPOINTS) {
    const duration = data.metrics[`http_req_duration{endpoint:${endpoint}}`];
    const requests = data.metrics[`http_reqs{endpoint:${endpoint}}`];
    const failed = data.metrics[`http_req_failed{endpoint:${endpoint}}`];
    if (!duration || !requests) continue;
    const v = duration.values;
    lines.push(
      `${endpoint.padEnd(20)} ${String(requests.values.count).padStart(8)} ${fixed(requests.values.rate).padStart(7)} `
      + `${fixed(v['p(50)']).padStart(8)} ${fixed(v['p(95)']).padStart(8)} ${fixed(v['p(99)']).padStart(8)} `
      + `${fixed((failed ? failed.values.rate : 0) * 100, 2).padStart(9)}`
      + (endpoint === 'ranking_revalidate' ? `   ${data.metrics.revalidation_304 ? data.metrics.revalidation_304.values.count : 0}` : ''),
    );
  }
  const all = data.metrics.http_reqs.values;
  lines.push(`all requests: ${all.count} (${fixed(all.rate)} req/s), 429s: ${data.metrics.rate_limited.values.count}`);
  lines.push('');
  lines.push('variant                                         requests   p50 ms   p95 ms   p99 ms');
  for (const [name, metric] of Object.entries(data.metrics)) {
    const m = /^http_req_duration\{endpoint:(\w+),variant:(\w+)\}$/.exec(name);
    if (!m) continue;
    const count = (data.metrics[`http_reqs{endpoint:${m[1]},variant:${m[2]}}`] || { values: { count: '' } }).values.count;
    const v = metric.values;
    lines.push(`${(`${m[1]}/${m[2]}`).padEnd(46)} ${String(count).padStart(9)} ${fixed(v['p(50)']).padStart(8)} ${fixed(v['p(95)']).padStart(8)} ${fixed(v['p(99)']).padStart(8)}`);
  }
  return `${lines.join('\n')}\n`;
}

export function handleSummary(data) {
  const out = { stdout: table(data) };
  if (__ENV.SUMMARY_JSON) out[__ENV.SUMMARY_JSON] = JSON.stringify(data, null, 2);
  if (__ENV.SUMMARY_TXT) out[__ENV.SUMMARY_TXT] = table(data);
  return out;
}
