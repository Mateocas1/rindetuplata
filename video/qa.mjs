/**
 * Objective QA for the film.
 *
 * Watching a render is the only real check, but these catch the two failure
 * modes a 15-second piece cannot hide: an accidental edit, and a stretch where
 * nothing moves. Both are measured, not guessed.
 *
 *   node qa.mjs out/rindetuplata.mp4
 */
import { execFileSync } from 'node:child_process';

const file = process.argv[2] ?? './out/rindetuplata.mp4';

const probe = (args) => execFileSync('ffprobe', ['-v', 'error', ...args, file]).toString().trim();

const streams = probe(['-show_entries', 'stream=index,codec_type,codec_name,width,height,r_frame_rate,nb_frames,duration,sample_rate,channels', '-of', 'compact=p=0:nk=0']);

console.log(`\nQ A  ${file}\n${'─'.repeat(64)}`);
console.log(streams);

const duration = Number(probe(['-show_entries', 'format=duration', '-of', 'csv=p=0']));
console.log(`container duration: ${duration.toFixed(3)} s`);

// ---- cuts: a scene-change score well above a whip means an edit slipped in.
const sceneOut = execFileSync(
  'ffmpeg',
  ['-hide_banner', '-i', file, '-vf', "select='gt(scene,0.12)',metadata=print:file=-", '-an', '-f', 'null', '-'],
  { stdio: ['ignore', 'pipe', 'ignore'] },
).toString();
const cuts = [...sceneOut.matchAll(/pts_time:([\d.]+)/g)].map((m) => Number(m[1]));
console.log(`\ncuts (scene score > 0.12): ${cuts.length === 0 ? 'none' : cuts.map((c) => c.toFixed(2) + 's').join(', ')}`);

// ---- motion: per-frame mean absolute difference, averaged over half seconds.
const diffOut = execFileSync(
  'ffmpeg',
  [
    '-hide_banner', '-i', file,
    '-vf', 'tblend=all_mode=difference,signalstats,metadata=print:key=lavfi.signalstats.YAVG:file=-',
    '-an', '-f', 'null', '-',
  ],
  { stdio: ['ignore', 'pipe', 'ignore'] },
).toString();
const values = [...diffOut.matchAll(/YAVG=([\d.]+)/g)].map((m) => Number(m[1]));

const WINDOW = 30; // 0.5 s at 60 fps
const rows = [];
for (let w = 0; w < Math.floor(values.length / WINDOW); w++) {
  let sum = 0;
  for (let i = w * WINDOW; i < (w + 1) * WINDOW && i < values.length; i++) sum += values[i];
  rows.push({ t: w * 0.5, m: sum / WINDOW });
}

console.log('\nmotion energy per 0.5 s (mean |ΔY| per pixel)');
const max = Math.max(...rows.map((r) => r.m));
for (const r of rows) {
  const bar = '█'.repeat(Math.max(0, Math.round((r.m / max) * 42)));
  console.log(`${r.t.toFixed(1).padStart(4)}s ${r.m.toFixed(2).padStart(5)} ${bar}`);
}

// The last stretch is allowed to settle; everything before it is not.
const body = rows.filter((r) => r.t < 13.5);
const quiet = body.filter((r) => r.m < 0.5);
console.log(
  `\nquiet windows before 13.5 s (<0.5): ${quiet.length === 0 ? 'none' : quiet.map((r) => r.t.toFixed(1) + 's').join(', ')}`,
);
console.log(`busiest: ${rows.reduce((a, b) => (b.m > a.m ? b : a)).t.toFixed(1)}s\n`);
