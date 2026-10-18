/**
 * Fast preview: 60 fps at half resolution, no subframe blur. For judging motion
 * and timing, not quality. The master goes through master.mjs.
 */
import { bundle } from '@remotion/bundler';
import { selectComposition, renderMedia } from '@remotion/renderer';
import { execFileSync } from 'node:child_process';

const CHROME = '/home/picala/.local/bin/google-chrome';
const serveUrl = await bundle({ entryPoint: './src/index.ts' });
const composition = await selectComposition({ serveUrl, id: 'Rindetuplata', inputProps: {}, browserExecutable: CHROME });

await renderMedia({
  composition,
  serveUrl,
  codec: 'h264',
  outputLocation: './out/preview-silent.mp4',
  browserExecutable: CHROME,
  scale: 0.5,
  crf: 20,
  muted: true,
  concurrency: 6,
  onProgress: ({ progress }) => {
    const pct = Math.round(progress * 100);
    if (pct % 20 === 0) console.log(`${pct}%`);
  },
});

execFileSync('ffmpeg', [
  '-y', '-hide_banner', '-loglevel', 'error',
  '-i', './out/preview-silent.mp4',
  '-i', './out/mix.wav',
  '-c:v', 'copy', '-c:a', 'aac', '-b:a', '192k',
  '-shortest', './out/preview.mp4',
]);
console.log('wrote ./out/preview.mp4');
