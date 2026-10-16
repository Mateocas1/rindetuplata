/**
 * Rebuild sidecar of the private preview (T14b, S20).
 *
 * The API POSTs its rebuild hook (`rindetuplata.ingest.rebuild-hook-url`, T4d) after a COMPLETED daily
 * ingestion, and the site is a prerendered Astro build, so something has to run `npm run build:api`
 * when the day's data lands. On the VPS that "something" is the CDN's deploy hook; on the owner's
 * machine it is this dependency-free HTTP server, which runs the real build into the same `web/dist`
 * Caddy serves. `deploy/compose.preview.yaml` starts it with the repository's `web/` mounted, so the
 * build uses the host's `web/node_modules` and the result is visible to Caddy immediately.
 *
 * Contract:
 *   - `POST /` answers 202 immediately (the hook's timeout is 10s; a build takes minutes) and runs
 *     the build in the background. One build at a time: a POST that arrives while a build is running
 *     queues exactly one more, and further POSTs during that build are absorbed by the same queued
 *     rerun.
 *   - `GET /` answers the current state: whether a build is running, whether one is queued, how many
 *     ran, and the last run's start, finish, exit code and duration. `preview.sh status` reads it.
 *   - Every run is logged with its exit code. The build's own output goes to this container's stdout,
 *     so `docker compose --project-name rindetuplata-preview logs -f rebuilder` shows it.
 *
 * It never publishes a port: only the API reaches it, over the Compose network.
 *
 * `SITE_URL` (the public Funnel URL) is mandatory: `build:api` refuses to publish a site whose
 * canonicals and sitemap point at the build machine, and a sidecar without it could only fail.
 */
import { spawn } from 'node:child_process';
import { createServer } from 'node:http';
import process from 'node:process';

const port = Number(process.env.REBUILDER_PORT ?? '8787');
const webDir = process.env.WEB_DIR ?? '/app/web';
const apiUrl = process.env.API_URL ?? 'http://web';
const siteUrl = process.env.SITE_URL ?? '';

/** Wall-clock timestamp prefix, so a build's start and end can be matched to `docker logs`. */
function log(message) {
  console.log(`[${new Date().toISOString()}] ${message}`);
}

if (siteUrl === '') {
  log('SITE_URL is required: the site build must publish the public URL, never a localhost default.');
  process.exit(1);
}

if (!Number.isInteger(port) || port < 0 || port > 65535) {
  log(`REBUILDER_PORT must be a port number, got ${process.env.REBUILDER_PORT}`);
  process.exit(1);
}

/** One build at a time; `queued` collapses every request that arrives during a build into one rerun. */
let building = false;
let queued = false;
let runs = 0;
let last = null;

function state() {
  return { building, queued, runs, last, webDir, apiUrl, siteUrl };
}

function finishRun(run, startedAt, exitCode, signal, failure) {
  const durationMs = Date.now() - startedAt;
  last = {
    run,
    startedAt: new Date(startedAt).toISOString(),
    finishedAt: new Date().toISOString(),
    exitCode,
    signal: signal ?? null,
    durationMs,
    ok: exitCode === 0,
    failure: failure ?? null,
  };
  building = false;
  if (failure !== null && failure !== undefined) {
    log(`build ${run} did not run: ${failure}`);
  } else {
    log(
      `build ${run} finished with exit code ${exitCode}` +
        `${signal ? ` (signal ${signal})` : ''} after ${(durationMs / 1000).toFixed(1)}s`,
    );
  }
  if (queued) {
    queued = false;
    log(`build ${run} left one rerun queued; starting it`);
    startBuild();
  }
}

/** Starts a build, or queues exactly one if a build is already running. */
function startBuild() {
  if (building) {
    queued = true;
    log('a build is already running: queued exactly one rerun');
    return 'queued';
  }

  building = true;
  runs += 1;
  const run = runs;
  const startedAt = Date.now();
  log(`build ${run} started: npm run build:api in ${webDir} (API_URL=${apiUrl}, SITE_URL=${siteUrl})`);

  const env = { ...process.env, API_URL: apiUrl, SITE_URL: siteUrl };
  // A fixture build must never be published; `build:api` strips it too, this is the second lock.
  delete env.WEB_FIXTURES;

  const child = spawn('npm', ['run', 'build:api'], { cwd: webDir, env, stdio: ['ignore', 'inherit', 'inherit'] });

  let settled = false;
  const finish = (exitCode, signal, failure) => {
    if (settled) {
      return;
    }
    settled = true;
    finishRun(run, startedAt, exitCode, signal, failure);
  };

  // `error` fires when npm cannot be spawned at all (no node_modules, wrong image, no npm in PATH).
  child.on('error', (error) => finish(null, null, error.message));
  child.on('exit', (code, signal) => finish(code, signal, null));

  return 'started';
}

function send(response, status, body) {
  const payload = `${JSON.stringify(body)}\n`;
  response.writeHead(status, {
    'content-type': 'application/json',
    'content-length': Buffer.byteLength(payload),
    // The sidecar is mirrored state, never something a crawler or a proxy should keep.
    'cache-control': 'no-store',
  });
  response.end(payload);
}

const server = createServer((request, response) => {
  const path = new URL(request.url ?? '/', 'http://rebuilder').pathname;

  if (request.method === 'POST' && path === '/') {
    // Answered before the build finishes on purpose: the API's hook must never wait for it.
    send(response, 202, { status: startBuild(), ...state() });
    return;
  }

  if (request.method === 'GET' && path === '/') {
    send(response, 200, state());
    return;
  }

  send(response, 404, { error: 'not found', paths: ['GET /', 'POST /'] });
});

server.listen(port, '0.0.0.0', () => {
  // `server.address().port` and not `port`: with `REBUILDER_PORT=0` the kernel picks a free port, which
  // is how the tests of this server talk to it without racing for a fixed one.
  log(`rebuilder listening on 0.0.0.0:${server.address().port}; builds run in ${webDir} as SITE_URL=${siteUrl}`);
});

server.on('error', (error) => {
  log(`rebuilder could not listen on port ${port}: ${error.message}`);
  process.exit(1);
});

for (const signal of ['SIGTERM', 'SIGINT']) {
  process.on(signal, () => {
    log(`received ${signal}: exiting${building ? ' while a build is running (it is a child of this process)' : ''}`);
    server.close(() => process.exit(0));
    // A build in flight must not keep the container alive after `docker compose stop`.
    setTimeout(() => process.exit(0), 2000).unref();
  });
}
