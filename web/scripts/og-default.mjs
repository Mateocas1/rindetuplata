/**
 * Generates the static social card and the favicon from the design tokens, so
 * the brand mark can never drift from `tokens.css`.
 *
 *   npm run og:default
 *
 * Writes public/og-default.png (1200x630, no external tooling: a tiny PNG
 * encoder over zlib) and public/favicon.svg.
 */
import { deflateSync } from 'node:zlib';
import { readFileSync, writeFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { dirname, join, resolve } from 'node:path';

const root = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const tokens = readFileSync(join(root, 'src/styles/tokens.css'), 'utf8');

// --- tokens ---------------------------------------------------------------
const declared = new Map(
  [...tokens.matchAll(/(--[a-z0-9-]+)\s*:\s*([^;]+);/g)].map((match) => [match[1], match[2].trim()]),
);

function resolveToken(name) {
  let value = declared.get(name);
  let hops = 0;
  while (value !== undefined && value.startsWith('var(') && hops < 8) {
    value = declared.get(/^var\(\s*(--[a-z0-9-]+)\s*\)$/.exec(value)[1]);
    hops += 1;
  }
  if (value === undefined) throw new Error(`Unknown token ${name}`);
  return value;
}

/** OKLCH to 8-bit sRGB, per the Oklab definition. */
function toRgb(value) {
  const [l, c, hue] = /^oklch\(\s*([\d.]+)\s+([\d.]+)\s+([\d.]+)\s*\)$/
    .exec(value)
    .slice(1)
    .map(Number);
  const h = (hue * Math.PI) / 180;
  const a = c * Math.cos(h);
  const b = c * Math.sin(h);
  const lp = l + 0.3963377774 * a + 0.2158037573 * b;
  const mp = l - 0.1055613458 * a - 0.0638541728 * b;
  const sp = l - 0.0894841775 * a - 1.291485548 * b;
  const lm = lp ** 3;
  const mm = mp ** 3;
  const sm = sp ** 3;
  const linear = [
    4.0767416621 * lm - 3.3077115913 * mm + 0.2309699292 * sm,
    -1.2684380046 * lm + 2.6097574011 * mm - 0.3413193965 * sm,
    -0.0041960863 * lm - 0.7034186147 * mm + 1.707614701 * sm,
  ];
  return linear.map((channel) => {
    const clamped = Math.min(1, Math.max(0, channel));
    const encoded =
      clamped <= 0.0031308 ? 12.92 * clamped : 1.055 * clamped ** (1 / 2.4) - 0.055;
    return Math.round(encoded * 255);
  });
}

const color = (name) => toRgb(resolveToken(name));
const hex = (name) => `#${color(name).map((v) => v.toString(16).padStart(2, '0')).join('')}`;

const palette = {
  background: color('--color-slate-900'),
  text: color('--color-slate-50'),
  muted: color('--color-slate-500'),
  accent: color('--color-blue-600'),
  gain: color('--color-green-600'),
  border: color('--color-slate-200'),
};

// --- 5x7 bitmap font, only the letters the wordmark needs ------------------
const GLYPHS = {
  r: [0b00000, 0b00000, 0b10110, 0b11001, 0b10000, 0b10000, 0b10000],
  i: [0b00000, 0b00100, 0b00000, 0b01100, 0b00100, 0b00100, 0b01110],
  n: [0b00000, 0b00000, 0b10110, 0b11001, 0b10001, 0b10001, 0b10001],
  d: [0b00001, 0b00001, 0b01111, 0b10001, 0b10001, 0b10001, 0b01111],
  e: [0b00000, 0b00000, 0b01110, 0b10001, 0b11111, 0b10000, 0b01110],
  t: [0b00100, 0b00100, 0b01110, 0b00100, 0b00100, 0b00101, 0b00010],
  u: [0b00000, 0b00000, 0b10001, 0b10001, 0b10001, 0b10011, 0b01101],
  p: [0b00000, 0b00000, 0b11110, 0b10001, 0b11110, 0b10000, 0b10000],
  l: [0b01100, 0b00100, 0b00100, 0b00100, 0b00100, 0b00100, 0b01110],
  a: [0b00000, 0b00000, 0b01110, 0b00001, 0b01111, 0b10001, 0b01111],
};

// --- tiny PNG encoder -----------------------------------------------------
const CRC_TABLE = Array.from({ length: 256 }, (_, n) => {
  let c = n;
  for (let k = 0; k < 8; k += 1) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1;
  return c >>> 0;
});

function crc32(buffer) {
  let crc = 0xffffffff;
  for (const byte of buffer) crc = CRC_TABLE[(crc ^ byte) & 0xff] ^ (crc >>> 8);
  return (crc ^ 0xffffffff) >>> 0;
}

function chunk(type, data) {
  const length = Buffer.alloc(4);
  length.writeUInt32BE(data.length);
  const body = Buffer.concat([Buffer.from(type, 'ascii'), data]);
  const crc = Buffer.alloc(4);
  crc.writeUInt32BE(crc32(body));
  return Buffer.concat([length, body, crc]);
}

function encodePng(width, height, pixels) {
  const ihdr = Buffer.alloc(13);
  ihdr.writeUInt32BE(width, 0);
  ihdr.writeUInt32BE(height, 4);
  ihdr[8] = 8; // bit depth
  ihdr[9] = 2; // colour type: truecolour RGB
  const raw = Buffer.alloc(height * (width * 3 + 1));
  for (let y = 0; y < height; y += 1) {
    const rowStart = y * (width * 3 + 1);
    raw[rowStart] = 0; // filter: none
    pixels.copy(raw, rowStart + 1, y * width * 3, (y + 1) * width * 3);
  }
  return Buffer.concat([
    Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]),
    chunk('IHDR', ihdr),
    chunk('IDAT', deflateSync(raw, { level: 9 })),
    chunk('IEND', Buffer.alloc(0)),
  ]);
}

// --- the card -------------------------------------------------------------
const WIDTH = 1200;
const HEIGHT = 630;
const pixels = Buffer.alloc(WIDTH * HEIGHT * 3);

function fill(x, y, w, h, rgb) {
  for (let row = y; row < y + h; row += 1) {
    if (row < 0 || row >= HEIGHT) continue;
    for (let column = x; column < x + w; column += 1) {
      if (column < 0 || column >= WIDTH) continue;
      const offset = (row * WIDTH + column) * 3;
      pixels[offset] = rgb[0];
      pixels[offset + 1] = rgb[1];
      pixels[offset + 2] = rgb[2];
    }
  }
}

function drawText(text, x, y, scale, rgb) {
  let cursor = x;
  for (const letter of text) {
    const glyph = GLYPHS[letter];
    if (glyph === undefined) throw new Error(`No glyph for "${letter}"`);
    glyph.forEach((bits, row) =>
      Array.from({ length: 5 }, (_, column) => 4 - column)
        .filter((bit) => (bits >> bit) & 1)
        .forEach((bit) => fill(cursor + (4 - bit) * scale, y + row * scale, scale, scale, rgb)),
    );
    cursor += 6 * scale;
  }
}

const png = (() => {
  fill(0, 0, WIDTH, HEIGHT, palette.background);
  fill(0, 0, WIDTH, 12, palette.accent);
  // A rising bar chart: the product's whole idea in one shape.
  const bars = [0.28, 0.42, 0.55, 0.72, 1];
  const baseline = 430;
  bars.forEach((factor, index) => {
    const height = Math.round(factor * 240);
    const x = 96 + index * 128;
    fill(x, baseline - height, 88, height, index === bars.length - 1 ? palette.gain : palette.muted);
  });
  fill(96, baseline + 8, 88 * bars.length + 4 * 40 - 40, 3, palette.border);
  drawText('rindetuplata', 96, 500, 12, palette.text);
  return encodePng(WIDTH, HEIGHT, pixels);
})();

writeFileSync(join(root, 'public/og-default.png'), png);

const favicon = `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 64 64" role="img" aria-label="rindetuplata">
  <rect width="64" height="64" rx="12" fill="${hex('--color-slate-900')}"/>
  <rect x="12" y="34" width="9" height="18" rx="2" fill="${hex('--color-slate-500')}"/>
  <rect x="27" y="24" width="9" height="28" rx="2" fill="${hex('--color-slate-500')}"/>
  <rect x="42" y="12" width="9" height="40" rx="2" fill="${hex('--color-green-600')}"/>
</svg>
`;
writeFileSync(join(root, 'public/favicon.svg'), favicon);

console.log(`og-default.png ${png.length} bytes, favicon.svg ${favicon.length} bytes`);
