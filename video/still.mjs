import { bundle } from '@remotion/bundler';
import { selectComposition, renderStill } from '@remotion/renderer';
import fs from 'node:fs';

const CHROME = '/home/picala/.local/bin/google-chrome';
const times = process.argv.slice(2).map(Number);
const outDir = './out/stills';
fs.mkdirSync(outDir, { recursive: true });

const serveUrl = await bundle({ entryPoint: './src/index.ts' });
const composition = await selectComposition({ serveUrl, id: 'Rindetuplata', inputProps: {}, browserExecutable: CHROME });

for (const seconds of times) {
  const frame = Math.round(seconds * composition.fps);
  const output = `${outDir}/t${seconds.toFixed(2).replace('.', '_')}.png`;
  await renderStill({ composition, serveUrl, frame, output, browserExecutable: CHROME, imageFormat: 'png' });
  console.log('wrote', output, 'frame', frame);
}
