/**
 * Self-hosted brand fonts (the same OFL files the site ships), loaded before the
 * first frame. A render that starts with a fallback font is a wasted render.
 */
import { continueRender, delayRender, staticFile } from 'remotion';
import { loadFont } from '@remotion/fonts';

const handle = delayRender('Loading brand fonts');

const fonts = [
  { family: 'Space Grotesk Variable', file: 'space-grotesk-latin-wght-normal.woff2', weight: '300 700' },
  { family: 'Archivo Variable', file: 'archivo-latin-wght-normal.woff2', weight: '100 900' },
  { family: 'Geist Mono Variable', file: 'geist-mono-latin-wght-normal.woff2', weight: '100 900' },
  { family: 'Instrument Serif', file: 'instrument-serif-latin-400-italic.woff2', weight: '400', style: 'italic' },
];

Promise.all(
  fonts.map((f) =>
    loadFont({
      family: f.family,
      url: staticFile(`fonts/${f.file}`),
      weight: f.weight,
      style: f.style ?? 'normal',
      format: 'woff2',
    }),
  ),
)
  .then(() => continueRender(handle))
  .catch((err) => {
    console.error('font load failed', err);
    continueRender(handle);
  });
