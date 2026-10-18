/**
 * The master.
 *
 * 1. Render the same composition at 180 fps. Every animation in this project is
 *    a function of seconds, so the extra frames land on the real motion rather
 *    than on an approximation of it.
 * 2. Average each aligned triple of subframes with ffmpeg's `tmix`. Three frames
 *    at 180 fps spanning 1/60 s is a 180-degree shutter, which is the motion blur
 *    a real camera would have produced at 60 fps. Text that is not moving gets
 *    three identical frames, so it stays perfectly sharp: nothing is smeared.
 * 3. Add fine grain and encode.
 */
import { bundle } from '@remotion/bundler';
import { selectComposition, renderFrames } from '@remotion/renderer';
import { execFileSync } from 'node:child_process';
import fs from 'node:fs';

const CHROME = '/home/picala/.local/bin/google-chrome';
const SUBFPS = 180;
const OUT_FPS = 60;
const DURATION = 15;
const SUBFRAMES = SUBFPS * DURATION;
const FRAME_DIR = './out/frames180';

const existing = fs.existsSync(FRAME_DIR)
  ? fs.readdirSync(FRAME_DIR).filter((f) => f.endsWith('.png')).length
  : 0;
const resume = existing === SUBFRAMES;
if (resume) console.log(`found ${existing} subframes on disk; skipping the render`);

if (!resume) {
  fs.rmSync(FRAME_DIR, { recursive: true, force: true });
  fs.mkdirSync(FRAME_DIR, { recursive: true });
}

const serveUrl = await bundle({ entryPoint: './src/index.ts' });
const base = await selectComposition({ serveUrl, id: 'Rindetuplata', inputProps: {}, browserExecutable: CHROME });

const composition = { ...base, fps: SUBFPS, durationInFrames: SUBFRAMES };

let last = -1;
if (!resume) await renderFrames({
  composition,
  serveUrl,
  outputDir: FRAME_DIR,
  imageFormat: 'png',
  browserExecutable: CHROME,
  concurrency: Number(process.env.CONCURRENCY ?? 4),
  inputProps: {},
  onStart: ({ frameCount }) => console.log(`rendering ${frameCount} subframes at ${SUBFPS} fps`),
  onFrameUpdate: (done) => {
    const pct = Math.floor((done / SUBFRAMES) * 100);
    if (pct >= last + 5) {
      last = pct;
      console.log(`${pct}%`);
    }
  },
});

const files = fs.readdirSync(FRAME_DIR).filter((f) => f.endsWith('.png')).sort();
console.log(`subframes on disk: ${files.length}`);
if (files.length !== SUBFRAMES) throw new Error(`expected ${SUBFRAMES} subframes, got ${files.length}`);

// Remotion zero-pads the index to the width of the frame count, so the pattern
// has to come from a real filename rather than from an assumption.
const digits = /element-(\d+)\.png/.exec(files[0])[1];
const pattern = `${FRAME_DIR}/element-%0${digits.length}d.png`;
console.log(`pattern: ${pattern}`);

// tmix(3) averages n-2..n; picking n % 3 == 2 selects the aligned triples.
const filter = [
  "tmix=frames=3:weights='1 1 1'",
  "select='eq(mod(n\\,3)\\,2)'",
  'setpts=N/(60*TB)',
  // Grain after the blend: it belongs to the print, not to the exposure.
  'noise=c0s=7:c0f=t+u',
  'format=yuv420p',
].join(',');

execFileSync(
  'ffmpeg',
  [
    '-y', '-hide_banner', '-loglevel', 'warning',
    '-framerate', String(SUBFPS), '-start_number', '0', '-i', pattern,
    '-vf', filter,
    '-r', String(OUT_FPS),
    '-c:v', 'libx264', '-preset', 'slow', '-crf', '16',
    '-x264-params', 'keyint=120:min-keyint=60:ref=4',
    '-colorspace', 'bt709', '-color_primaries', 'bt709', '-color_trc', 'bt709',
    '-movflags', '+faststart',
    './out/master-silent.mp4',
  ],
  { stdio: 'inherit' },
);

execFileSync(
  'ffmpeg',
  [
    '-y', '-hide_banner', '-loglevel', 'error',
    '-i', './out/master-silent.mp4',
    '-i', './out/mix.wav',
    '-c:v', 'copy', '-c:a', 'aac', '-b:a', '256k', '-ar', '48000',
    '-shortest', '-movflags', '+faststart',
    './out/rindetuplata.mp4',
  ],
  { stdio: 'inherit' },
);

console.log('wrote ./out/rindetuplata.mp4');
