/**
 * Sound design, authored as a cue sheet.
 *
 * Every sound is an ElevenLabs-generated effect: no music and no voice, by
 * request. The sheet is the single source of truth for the mix, so a change in
 * timing is one edit here rather than a hand-built filter graph.
 *
 * Levels are per-cue dB offsets over the normalised stems; the master runs
 * through a limiter and a short tail fade.
 */
import { execFileSync } from 'node:child_process';
import fs from 'node:fs';

const SR = 48000;

/** [seconds, file, gainDb, fadeIn, fadeOut] */
const CUES = [
  // ---- hook (no lead: this beat starts the film)
  [0.0, 'hook-riser', -27, 0.05, 0.6],
  [0.5, 'hook-impact', -14, 0.0, 0.3],
  [1.14, 'data-blip', -22, 0.0, 0.12],

  // ---- hook -> thesis (camera move)
  [2.2, 'whoosh-a', -13, 0.0, 0.12],
  [2.42, 'hook-impact', -19, 0.0, 0.35],

  // ---- thesis, on the beat's own clock (content runs 0.45 s ahead)
  [2.28, 'sweep-up', -19, 0.06, 0.25],
  [3.14, 'tick', -19, 0.0, 0.06],
  [3.53, 'tick', -17, 0.0, 0.06],
  [3.74, 'data-blip', -16, 0.0, 0.06],
  [3.86, 'ding', -15, 0.01, 0.55],
  [3.98, 'shimmer', -24, 0.1, 0.55],

  // ---- the push into the sliver, then the arrival at the plate
  [4.9, 'whip', -10, 0.0, 0.08],
  [5.3, 'whoosh-b', -13, 0.0, 0.12],

  // ---- benefits
  [5.4, 'sweep-up', -22, 0.06, 0.35],
  [5.5, 'tick', -23, 0.0, 0.06],
  [5.95, 'shimmer', -26, 0.12, 0.6],
  [6.05, 'tick', -19, 0.0, 0.06],
  [6.17, 'tick', -19, 0.0, 0.06],
  [6.29, 'tick', -19, 0.0, 0.06],
  [6.26, 'ding', -16, 0.01, 0.6],
  [6.66, 'tick', -21, 0.0, 0.06],
  [7.2, 'air-long', -23, 0.15, 0.4],

  // ---- benefits -> features (camera move)
  [8.1, 'whoosh-a', -12, 0.0, 0.12],
  [8.3, 'hook-impact', -22, 0.0, 0.25],

  // ---- features
  [8.25, 'tick', -18, 0.0, 0.06],
  [8.4, 'tick', -18, 0.0, 0.06],
  [8.55, 'tick', -18, 0.0, 0.06],
  [8.7, 'tick', -18, 0.0, 0.06],
  [8.96, 'sweep-up', -19, 0.06, 0.45],
  [9.46, 'switch', -13, 0.0, 0.1],
  [10.2, 'air-long', -24, 0.15, 0.45],

  // ---- features -> positioning (camera move)
  [11.2, 'whoosh-b', -12, 0.0, 0.12],
  [11.98, 'data-blip', -24, 0.0, 0.12],
  [12.16, 'tick', -19, 0.0, 0.06],
  [12.23, 'tick', -19, 0.0, 0.06],
  [12.3, 'tick', -19, 0.0, 0.06],
  [12.37, 'tick', -19, 0.0, 0.06],
  [12.8, 'rumble-soft', -17, 0.2, 0.5],

  // ---- positioning -> outro (camera move), then the wordmark
  [13.15, 'whoosh-a', -11, 0.0, 0.12],
  [13.02, 'hook-impact', -16, 0.0, 0.45],
  [13.56, 'shimmer', -20, 0.06, 0.7],
  [13.92, 'settle', -16, 0.02, 0.9],
];

const size = (file) =>
  Number(execFileSync('ffprobe', ['-v', 'error', '-show_entries', 'format=duration', '-of', 'csv=p=0', file]).toString().trim());

const parts = [];
const filters = [];
CUES.forEach(([at, name, gain, fin, fout], i) => {
  const file = `public/sfx/${name}.mp3`;
  if (!fs.existsSync(file)) throw new Error(`missing ${file}`);
  const dur = size(file);
  const fadeOutStart = Math.max(0, dur - fout);
  parts.push(`[${i}:a]`);
  filters.push(
    `[${i}:a]aformat=sample_fmts=fltp:channel_layouts=stereo,` +
      `volume=${gain}dB,` +
      `afade=t=in:st=0:d=${fin},` +
      `afade=t=out:st=${fadeOutStart.toFixed(3)}:d=${Math.min(fout, dur).toFixed(3)},` +
      `adelay=${Math.round(at * 1000)}:all=1[a${i}]`,
  );
});

const mixIn = CUES.map((_, i) => `[a${i}]`).join('');
const graph = [
  ...filters,
  `${mixIn}amix=inputs=${CUES.length}:normalize=0:dropout_transition=0[mix]`,
  // A showreel master: keep peaks under control and leave on a clean tail.
  `[mix]volume=11.5dB,alimiter=limit=0.85:attack=3:release=60,afade=t=out:st=14.82:d=0.18,aresample=${SR}[out]`,
].join(';');

const inputs = CUES.flatMap(([, name]) => ['-i', `public/sfx/${name}.mp3`]);

fs.mkdirSync('out', { recursive: true });
execFileSync(
  'ffmpeg',
  ['-y', '-hide_banner', '-loglevel', 'error', ...inputs, '-filter_complex', graph, '-map', '[out]', '-ac', '2', '-ar', String(SR), '-t', '15', 'out/mix.wav'],
  { stdio: 'inherit' },
);

const stats = execFileSync('ffmpeg', ['-hide_banner', '-i', 'out/mix.wav', '-af', 'ebur128=peak=true', '-f', 'null', '-'], {
  stdio: ['ignore', 'pipe', 'pipe'],
}).toString();
console.log('cues:', CUES.length, '-> out/mix.wav');
console.log(stats.split('\n').filter((l) => /I:|Peak:|LRA:/.test(l)).slice(-4).join('\n'));
