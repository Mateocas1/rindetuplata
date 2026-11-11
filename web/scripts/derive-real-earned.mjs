/**
 * `node scripts/derive-real-earned.mjs`: adds `realEarned` to the recorded fixtures.
 *
 * The recordings predate the API field, so it is derived here with the API's own formula
 * (`finalAmount / (1 + inflation.overHorizon) - request.amount`, exact decimal arithmetic, cents half-even; see
 * `realEarned.mjs`), and is null exactly when `realReturn` is null. Every `ranking-*.json` uses its own `request`
 * and `inflation`. `simulation-sample.json` carries neither, so it reads them from `ranking-500k-60d.json`, the
 * recording of the same request (amount 500000, 60 days), after checking the entry matches that file's row.
 * The files are edited as text, so everything else stays byte for byte; running it again changes nothing.
 */
import { readdirSync, readFileSync, writeFileSync } from 'node:fs';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { addRealEarned } from './realEarned.mjs';

const fixtures = resolve(dirname(fileURLToPath(import.meta.url)), '..', 'fixtures');

function read(name) {
  const text = readFileSync(join(fixtures, name), 'utf8');
  return { name, text, json: JSON.parse(text) };
}

function write(file, text) {
  if (text !== file.text) {
    writeFileSync(join(fixtures, file.name), text);
    console.log(`updated ${file.name}`);
  } else {
    console.log(`unchanged ${file.name}`);
  }
}

const rankingFiles = readdirSync(fixtures)
  .filter((name) => /^ranking-.*\.json$/.test(name))
  .sort();

for (const name of rankingFiles) {
  const file = read(name);
  const overHorizon = file.json.inflation?.overHorizon ?? null;
  write(file, addRealEarned(file.text, file.json.ranked, file.json.request.amount, overHorizon));
}

const reference = read('ranking-500k-60d.json');
const simulation = read('simulation-sample.json');
const row = reference.json.ranked.find((entry) => entry.id === simulation.json.entry.id);
if (row === undefined || row.finalAmount !== simulation.json.entry.finalAmount) {
  throw new Error('simulation-sample.json no longer matches its row of ranking-500k-60d.json');
}
write(
  simulation,
  addRealEarned(
    simulation.text,
    [simulation.json.entry],
    reference.json.request.amount,
    reference.json.inflation?.overHorizon ?? null,
  ),
);
