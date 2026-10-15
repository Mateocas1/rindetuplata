import { spawnSync } from 'node:child_process';
import { chmodSync, mkdirSync, mkdtempSync, rmSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { afterAll, describe, expect, it } from 'vitest';

/**
 * `scripts/build-api.mjs` (T7e, finding R3).
 *
 * The production build entrypoint is what keeps a fixture build out of
 * production, and it talks to `astro` through a child process. Both the
 * environment guard and the failure paths of that spawn need to hold: a build
 * that never ran must never look like a build that succeeded.
 *
 * `astro` is never really invoked here: a fake executable on `PATH` records the
 * environment the script passed it and can fail on purpose.
 */
const webRoot = resolve(fileURLToPath(new URL('.', import.meta.url)), '..');
const script = join(webRoot, 'scripts/build-api.mjs');

const scratch = mkdtempSync(join(tmpdir(), 'build-api-test-'));

/** A directory holding an executable named `astro` that runs `body`. */
function fakeAstroDir(name: string, body: string): string {
  const dir = join(scratch, name);
  mkdirSync(dir, { recursive: true });
  const file = join(dir, 'astro');
  writeFileSync(file, `#!/bin/sh\n${body}\n`);
  chmodSync(file, 0o755);
  return dir;
}

const okBin = fakeAstroDir(
  'ok-bin',
  'echo "API_URL=$API_URL SITE_URL=$SITE_URL WEB_FIXTURES=${WEB_FIXTURES-<unset>}"',
);
const failingBin = fakeAstroDir('failing-bin', 'echo "fake astro: the build broke" >&2; exit 3');
const emptyBin = join(scratch, 'empty-bin');
mkdirSync(emptyBin, { recursive: true });

afterAll(() => {
  rmSync(scratch, { recursive: true, force: true });
});

interface Run {
  status: number | null;
  stdout: string;
  stderr: string;
}

/** Run the real script with exactly the given `astro` and environment. */
function runBuildApi(options: { path: string; apiUrl?: string; siteUrl?: string }): Run {
  // Spelled out: the environment is mutated below (`delete`/assign), and the spread of `process.env`
  // does not keep an index signature in this project's type environment.
  const env: Record<string, string | undefined> = { ...process.env, PATH: options.path };
  delete env.WEB_FIXTURES;
  if (options.apiUrl === undefined) delete env.API_URL;
  else env.API_URL = options.apiUrl;
  if (options.siteUrl === undefined) delete env.SITE_URL;
  else env.SITE_URL = options.siteUrl;

  const result = spawnSync(process.execPath, [script], {
    cwd: webRoot,
    env,
    encoding: 'utf8',
  });
  return { status: result.status, stdout: result.stdout ?? '', stderr: result.stderr ?? '' };
}

describe('build:api environment guard', () => {
  it('refuses to build without API_URL and says so', () => {
    const run = runBuildApi({ path: okBin, siteUrl: 'https://example.test' });
    expect(run.status).toBe(2);
    expect(run.stderr).toContain('API_URL is required');
    expect(run.stdout).not.toContain('API_URL=');
  });

  it('treats an empty API_URL as missing', () => {
    const run = runBuildApi({ path: okBin, apiUrl: '', siteUrl: 'https://example.test' });
    expect(run.status).toBe(2);
    expect(run.stderr).toContain('API_URL is required');
  });

  it('refuses to build without SITE_URL and says so', () => {
    const run = runBuildApi({ path: okBin, apiUrl: 'https://api.example.test' });
    expect(run.status).toBe(2);
    expect(run.stderr).toContain('SITE_URL is required');
    expect(run.stdout).not.toContain('SITE_URL=');
  });

  it('treats an empty SITE_URL as missing', () => {
    const run = runBuildApi({ path: okBin, apiUrl: 'https://api.example.test', siteUrl: '' });
    expect(run.status).toBe(2);
    expect(run.stderr).toContain('SITE_URL is required');
  });
});

describe('build:api astro invocation', () => {
  it('passes API_URL and SITE_URL to astro and strips WEB_FIXTURES', () => {
    const run = runBuildApi({
      path: okBin,
      apiUrl: 'https://api.example.test',
      siteUrl: 'https://example.test',
    });
    expect(run.status).toBe(0);
    expect(run.stdout).toContain('API_URL=https://api.example.test');
    expect(run.stdout).toContain('SITE_URL=https://example.test');
    expect(run.stdout).not.toContain('WEB_FIXTURES=1');
  });

  it('reports a spawn error instead of a silent non-zero exit', () => {
    const run = runBuildApi({
      path: emptyBin,
      apiUrl: 'https://api.example.test',
      siteUrl: 'https://example.test',
    });
    expect(run.status).not.toBe(0);
    expect(run.stderr).toContain('astro');
  });

  it('reports the child exit code when astro fails', () => {
    const run = runBuildApi({
      path: failingBin,
      apiUrl: 'https://api.example.test',
      siteUrl: 'https://example.test',
    });
    expect(run.status).toBe(3);
    expect(run.stderr).toContain('astro build failed with exit code 3');
  });
});
