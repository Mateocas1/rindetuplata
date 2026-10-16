/**
 * Rebuild sidecar of the private preview (T14b/T14c, S20).
 *
 * The API POSTs its rebuild hook (`rindetuplata.ingest.rebuild-hook-url`, T4d) after a COMPLETED daily
 * ingestion, and the site is a prerendered Astro build, so something has to run the site build when the
 * day's data lands. On the VPS that "something" is the CDN's deploy hook; on the owner's machine it is
 * this dependency-free HTTP server.
 *
 * Build layout (fix B of the T14c review): every build writes a directory of its own,
 * `web/.site/build-<n>`, and only a build that exited 0 is published by swapping the
 * `web/.site/current` symlink. Caddy serves `web/.site` on `/srv` with `root /srv/current`, so a rebuild
 * is invisible to visitors: they keep getting the previous build while the new one runs, a failed build
 * leaves the previous one serving, and `astro build` emptying its output directory can never delete the
 * site that is being served. Only the last `REBUILDER_KEEP_BUILDS` (default 2) builds are kept.
 *
 * Contract:
 *   - `POST /` answers 202 immediately (the hook's timeout is 10s; a build takes minutes) and runs the
 *     build in the background. One build at a time: a POST that arrives while a build is running queues
 *     exactly one more, and further POSTs during that build are absorbed by the same queued rerun.
 *   - `GET /` answers the current state: whether a build is running, whether one is queued, how many
 *     ran, which build `current` serves, which build directories are kept, and the last run's start,
 *     finish, exit code and duration. `preview.sh status` reads it.
 *   - Every run is logged with its exit code. The build's own output goes to this container's stdout,
 *     so `docker compose --project-name rindetuplata-preview logs -f rebuilder` shows it.
 *
 * It never publishes a port: only the API reaches it, over the Compose network.
 *
 * The build drives Astro directly (`node_modules/.bin/astro build --outDir .site/<build>`) instead of
 * the project's `npm run build:api` script, because that script builds into `dist` and accepts no output
 * directory. The two guards of that script are replicated here: `SITE_URL` and `API_URL` are mandatory
 * (a build must never publish a localhost canonical, and a build must never fall back to the recorded
 * fixtures), and `WEB_FIXTURES` is removed from the child's environment.
 */
import { spawn } from 'node:child_process';
import {
  existsSync,
  mkdirSync,
  readdirSync,
  readlinkSync,
  renameSync,
  rmSync,
  symlinkSync,
} from 'node:fs';
import { createServer } from 'node:http';
import { join } from 'node:path';
import process from 'node:process';

const port = Number(process.env.REBUILDER_PORT ?? '8787');
const webDir = process.env.WEB_DIR ?? '/app/web';
const apiUrl = process.env.API_URL ?? '';
const siteUrl = process.env.SITE_URL ?? '';
const keepBuilds = Number(process.env.REBUILDER_KEEP_BUILDS ?? '2');
// T14c: the API's startup ingestion can finish before this sidecar listens, and its hook is then lost
// (the API does not retry). One build at startup covers that window; a hook that arrives later queues.
const buildOnStart = process.env.REBUILDER_BUILD_ON_START === 'true';

/** The build root Caddy mounts on /srv: one directory per build plus the `current` symlink. */
const siteDir = join(webDir, '.site');
const currentLink = join(siteDir, 'current');
/** Astro's CLI, resolved locally so a build never depends on what is on the container's PATH. */
const astroBin = join(webDir, 'node_modules', '.bin', 'astro');

/** Wall-clock timestamp prefix, so a build's start and end can be matched to `docker logs`. */
function log(message) {
  console.log(`[${new Date().toISOString()}] ${message}`);
}

if (siteUrl === '') {
  log('SITE_URL is required: the site build must publish the public URL, never a localhost default.');
  process.exit(1);
}

if (apiUrl === '') {
  log('API_URL is required: the site build must read the running API, never the recorded fixtures.');
  process.exit(1);
}

if (!Number.isInteger(port) || port < 0 || port > 65535) {
  log(`REBUILDER_PORT must be a port number, got ${process.env.REBUILDER_PORT}`);
  process.exit(1);
}

if (!Number.isInteger(keepBuilds) || keepBuilds < 1) {
  log(`REBUILDER_KEEP_BUILDS must be a positive number, got ${process.env.REBUILDER_KEEP_BUILDS}`);
  process.exit(1);
}

/** One build at a time; `queued` collapses every request that arrives during a build into one rerun. */
let building = false;
let queued = false;
let runs = 0;
let last = null;

/** The build directories on disk, oldest first. A failed build never gets this far (it is removed). */
function listBuilds() {
  if (!existsSync(siteDir)) {
    return [];
  }
  return readdirSync(siteDir)
    .filter((name) => /^build-\d+$/.test(name))
    .sort((left, right) => Number(left.slice('build-'.length)) - Number(right.slice('build-'.length)));
}

/** The build Caddy is serving, or null when nothing has been published yet. */
function currentBuild() {
  try {
    return readlinkSync(currentLink);
  } catch {
    return null;
  }
}

/** `build-<n>` with `n` past every build already on disk, so a restart never reuses a number. */
function nextBuildName() {
  const numbers = listBuilds().map((name) => Number(name.slice('build-'.length)));
  return `build-${numbers.length === 0 ? 1 : Math.max(...numbers) + 1}`;
}

/**
 * Publishes one finished build: `current` is replaced with a single `rename`, so a visitor sees either
 * the previous build or the new one, never a half-written directory. Older builds beyond the last
 * `keepBuilds` are removed afterwards; the swap never touches them, and a directory still open in an
 * in-flight request survives until that request finishes (POSIX unlink semantics).
 */
function publishBuild(buildName) {
  const temporaryLink = join(siteDir, '.current.tmp');
  rmSync(temporaryLink, { force: true });
  symlinkSync(buildName, temporaryLink);
  renameSync(temporaryLink, currentLink);
  log(`published ${buildName}: ${currentLink} -> ${buildName}`);

  pruneBuilds();
}

/**
 * Removes builds beyond the last `keepBuilds`. Runs after the swap and never throws: a build that is
 * already published must not be reported as failed (and then deleted) because an old one resisted
 * removal; the leftover is logged and retried after the next build.
 */
function pruneBuilds() {
  const builds = listBuilds();
  for (const obsolete of builds.slice(0, Math.max(0, builds.length - keepBuilds))) {
    try {
      rmSync(join(siteDir, obsolete), { recursive: true, force: true });
      log(`removed the obsolete build ${obsolete} (keeping the last ${keepBuilds})`);
    } catch (error) {
      log(`could not remove the obsolete build ${obsolete}: ${error.message}`);
    }
  }
}

function state() {
  return {
    building,
    queued,
    runs,
    current: currentBuild(),
    builds: listBuilds(),
    keepBuilds,
    last,
    webDir,
    siteDir,
    apiUrl,
    siteUrl,
  };
}

function finishRun({ run, buildName, outDir, startedAt, exitCode, signal, failure, published }) {
  const durationMs = Date.now() - startedAt;
  last = {
    run,
    build: buildName,
    outDir,
    startedAt: new Date(startedAt).toISOString(),
    finishedAt: new Date().toISOString(),
    exitCode,
    signal: signal ?? null,
    durationMs,
    ok: failure === null && exitCode === 0 && published,
    published,
    failure: failure ?? null,
  };
  building = false;
  if (failure !== null) {
    log(`build ${run} did not produce a site: ${failure}`);
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
  const buildName = nextBuildName();
  const relativeOutDir = join('.site', buildName);
  mkdirSync(siteDir, { recursive: true });
  log(
    `build ${run} started: astro build --outDir ${relativeOutDir} in ${webDir} ` +
      `(API_URL=${apiUrl}, SITE_URL=${siteUrl})`,
  );

  let settled = false;
  const finish = (exitCode, signal, failure) => {
    if (settled) {
      return;
    }
    settled = true;
    let published = false;
    let reportedFailure = failure;
    if (reportedFailure === null && exitCode === 0) {
      try {
        publishBuild(buildName);
        published = true;
      } catch (error) {
        reportedFailure = `the build succeeded but could not be published: ${error.message}`;
      }
    }
    if (!published) {
      // A failed or unpublishable build is never kept: it must not occupy one of the kept slots.
      rmSync(join(siteDir, buildName), { recursive: true, force: true });
    }
    finishRun({ run, buildName, outDir: relativeOutDir, startedAt, exitCode, signal, failure: reportedFailure, published });
  };

  if (!existsSync(astroBin)) {
    // No spawn at all: a missing toolchain is a failure of this run, never a reason to try something else.
    finish(null, null, `${astroBin} is missing: the preview needs the host's web/node_modules (run 'cd web && npm ci')`);
    return 'started';
  }

  const env = { ...process.env, API_URL: apiUrl, SITE_URL: siteUrl };
  // A fixture build must never be published; the project's build:api script strips it too, this is the
  // second lock.
  delete env.WEB_FIXTURES;

  const child = spawn(astroBin, ['build', '--outDir', relativeOutDir], {
    cwd: webDir,
    env,
    stdio: ['ignore', 'inherit', 'inherit'],
  });

  // `error` fires when Astro cannot be spawned at all (no node_modules, no exec bit, wrong platform).
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
  log(
    `rebuilder listening on 0.0.0.0:${server.address().port}; builds run in ${webDir} ` +
      `and are published as ${currentLink} (keeping the last ${keepBuilds})`,
  );
  if (buildOnStart) {
    log('startup build requested (REBUILDER_BUILD_ON_START=true)');
    startBuild();
  }
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
