/**
 * `npm run build:api` (T7e): the production build of the site against a running API.
 *
 * It is the counterpart of the fixture build the tests use, and it is deliberately not the same
 * command: `API_URL` is mandatory (there is no silent fallback to `web/fixtures/`) and
 * `WEB_FIXTURES` is removed from the environment, so a fixture build can never be published as a
 * real one.
 */
import { spawnSync } from 'node:child_process';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const webRoot = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const apiUrl = process.env.API_URL;

if (apiUrl === undefined || apiUrl === '') {
  console.error(
    'API_URL is required: build:api builds the site against a running API, never the recorded fixtures.',
  );
  process.exit(2);
}

const env = { ...process.env, API_URL: apiUrl };
delete env.WEB_FIXTURES;

const build = spawnSync('astro', ['build'], {
  cwd: webRoot,
  env,
  stdio: 'inherit',
  shell: process.platform === 'win32',
});

process.exit(build.status ?? 1);
