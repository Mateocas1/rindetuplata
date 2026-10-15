/**
 * `npm run build:api` (T7e): the production build of the site against a running API.
 *
 * It is the counterpart of the fixture build the tests use, and it is deliberately not the same
 * command: `API_URL` and `SITE_URL` are both mandatory (there is no silent fallback to
 * `web/fixtures/` or to `http://localhost:4321`, which would publish canonical, sitemap and
 * `robots.txt` URLs pointing at the build machine) and `WEB_FIXTURES` is removed from the
 * environment, so a fixture build can never be published as a real one.
 *
 * A build that never ran must never look like a build that succeeded: a spawn error and a non-zero
 * child exit both print a message and exit non-zero.
 */
import { spawnSync } from 'node:child_process';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const webRoot = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const apiUrl = process.env.API_URL;
const siteUrl = process.env.SITE_URL;

function refuse(message) {
  console.error(message);
  process.exit(2);
}

if (apiUrl === undefined || apiUrl === '') {
  refuse(
    'API_URL is required: build:api builds the site against a running API, never the recorded fixtures.',
  );
}

if (siteUrl === undefined || siteUrl === '') {
  refuse(
    'SITE_URL is required: build:api publishes the site URL in canonicals, the sitemap and robots.txt, never the localhost default.',
  );
}

const env = { ...process.env, API_URL: apiUrl, SITE_URL: siteUrl };
delete env.WEB_FIXTURES;

const build = spawnSync('astro', ['build'], {
  cwd: webRoot,
  env,
  stdio: 'inherit',
  shell: process.platform === 'win32',
});

if (build.error) {
  console.error(`failed to run astro: ${build.error.message}`);
  process.exit(1);
}

if (build.status !== 0) {
  if (build.status === null) {
    console.error(`astro build was killed by ${build.signal ?? 'a signal'}`);
  } else {
    console.error(`astro build failed with exit code ${build.status}`);
  }
  process.exit(build.status ?? 1);
}
