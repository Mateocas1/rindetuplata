/**
 * Guards the design token layer: every color role used by a component must exist,
 * every declared color must fit sRGB, every declared text/UI pair must meet its WCAG
 * 2.2 threshold (better-colors: "never report a contrast value you did not measure"),
 * and no component may hard-code a color outside the token file.
 *
 * Two appearances (T12a): the `@theme` block holds the light values and one
 * `@media (prefers-color-scheme: dark)` block repoints the roles, so every pair is
 * measured once per scheme against the tokens that scheme actually resolves.
 */
import { readFileSync, readdirSync, statSync } from 'node:fs';
import { extname, join, relative, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { describe, expect, it } from 'vitest';

const webRoot = resolve(fileURLToPath(new URL('.', import.meta.url)), '../..');
const tokensPath = join(webRoot, 'src/styles/tokens.css');

type Oklch = { l: number; c: number; h: number };

function parseTokens(css: string): Map<string, string> {
  const tokens = new Map<string, string>();
  const declaration = /(--[a-z0-9-]+)\s*:\s*([^;]+);/g;
  for (const match of css.matchAll(declaration)) {
    tokens.set(match[1], match[2].trim());
  }
  return tokens;
}

/** OKLCH (D65) to linear sRGB, per the Oklab definition. */
function oklchToLinearSrgb(color: Oklch): [number, number, number] {
  const hRad = (color.h * Math.PI) / 180;
  const a = color.c * Math.cos(hRad);
  const b = color.c * Math.sin(hRad);

  const lPrime = color.l + 0.3963377774 * a + 0.2158037573 * b;
  const mPrime = color.l - 0.1055613458 * a - 0.0638541728 * b;
  const sPrime = color.l - 0.0894841775 * a - 1.291485548 * b;

  const l = lPrime ** 3;
  const m = mPrime ** 3;
  const s = sPrime ** 3;

  return [
    4.0767416621 * l - 3.3077115913 * m + 0.2309699292 * s,
    -1.2684380046 * l + 2.6097574011 * m - 0.3413193965 * s,
    -0.0041960863 * l - 0.7034186147 * m + 1.707614701 * s,
  ];
}

function parseColor(value: string): Oklch {
  const match = /^oklch\(\s*([\d.]+%?)\s+([\d.]+)\s+([\d.]+)\s*\)$/.exec(value);
  if (!match) {
    throw new Error(`Unsupported color notation in tokens.css: ${value}`);
  }
  const lightness = match[1].endsWith('%')
    ? Number.parseFloat(match[1]) / 100
    : Number.parseFloat(match[1]);
  return { l: lightness, c: Number.parseFloat(match[2]), h: Number.parseFloat(match[3]) };
}

function relativeLuminance(color: Oklch): number {
  const [r, g, b] = oklchToLinearSrgb(color);
  return 0.2126 * r + 0.7152 * g + 0.0722 * b;
}

function resolveToken(tokens: Map<string, string>, name: string): string {
  let value = tokens.get(name);
  let hops = 0;
  while (value !== undefined && value.startsWith('var(') && hops < 8) {
    const referenced = /^var\(\s*(--[a-z0-9-]+)\s*\)$/.exec(value);
    if (!referenced) break;
    value = tokens.get(referenced[1]);
    hops += 1;
  }
  if (value === undefined) throw new Error(`Token ${name} does not resolve to a color`);
  return value;
}

function contrastRatio(foreground: string, background: string): number {
  const a = relativeLuminance(parseColor(foreground));
  const b = relativeLuminance(parseColor(background));
  const lighter = Math.max(a, b);
  const darker = Math.min(a, b);
  return (lighter + 0.05) / (darker + 0.05);
}

function inSrgbGamut(color: Oklch, tolerance = 0.0005): boolean {
  return oklchToLinearSrgb(color).every(
    (channel) => channel >= -tolerance && channel <= 1 + tolerance,
  );
}

const darkQuery = '@media (prefers-color-scheme: dark)';

/** Light tokens come from everything outside the dark block; dark tokens override them. */
function schemes(css: string): { light: Map<string, string>; dark: Map<string, string>; darkOnly: Map<string, string> } {
  const start = css.indexOf(darkQuery);
  if (start === -1) {
    return { light: parseTokens(css), dark: parseTokens(css), darkOnly: new Map() };
  }
  let depth = 0;
  let end = css.indexOf('{', start);
  for (let i = end; i < css.length; i += 1) {
    if (css[i] === '{') depth += 1;
    if (css[i] === '}') depth -= 1;
    if (depth === 0) {
      end = i + 1;
      break;
    }
  }
  const light = parseTokens(css.slice(0, start) + css.slice(end));
  const darkOnly = parseTokens(css.slice(start, end));
  return { light, dark: new Map([...light, ...darkOnly]), darkOnly };
}

const requiredRoles = [
  '--color-surface',
  '--color-surface-raised',
  '--color-subtle-surface',
  '--color-text',
  '--color-muted',
  '--color-border',
  '--color-border-strong',
  '--color-accent',
  '--color-accent-solid',
  '--color-accent-surface',
  '--color-on-accent',
  '--color-gain',
  '--color-gain-surface',
  '--color-on-gain',
  '--color-loss',
  '--color-loss-surface',
  '--color-on-loss',
  '--color-warning',
  '--color-warning-surface',
  '--color-on-warning',
  '--color-focus',
];

/** [label, foreground token, background token, minimum ratio] */
const contrastPairs: Array<[string, string, string, number]> = [
  ['text on surface', '--color-text', '--color-surface', 4.5],
  ['text on surface-raised', '--color-text', '--color-surface-raised', 4.5],
  ['muted on surface', '--color-muted', '--color-surface', 4.5],
  ['muted on surface-raised', '--color-muted', '--color-surface-raised', 4.5],
  ['muted on subtle-surface', '--color-muted', '--color-subtle-surface', 4.5],
  ['text on subtle-surface', '--color-text', '--color-subtle-surface', 4.5],
  ['text on accent-surface (selected)', '--color-text', '--color-accent-surface', 4.5],
  ['accent on accent-surface', '--color-accent', '--color-accent-surface', 4.5],
  ['accent on surface', '--color-accent', '--color-surface', 4.5],
  ['accent on surface-raised', '--color-accent', '--color-surface-raised', 4.5],
  ['on-accent on accent-solid', '--color-on-accent', '--color-accent-solid', 4.5],
  ['on-accent on accent (button hover)', '--color-on-accent', '--color-accent', 4.5],
  ['gain on surface', '--color-gain', '--color-surface', 4.5],
  ['gain on surface-raised', '--color-gain', '--color-surface-raised', 4.5],
  ['on-gain on gain-surface', '--color-on-gain', '--color-gain-surface', 4.5],
  ['loss on surface', '--color-loss', '--color-surface', 4.5],
  ['loss on surface-raised', '--color-loss', '--color-surface-raised', 4.5],
  ['on-loss on loss-surface', '--color-on-loss', '--color-loss-surface', 4.5],
  ['warning on surface', '--color-warning', '--color-surface', 4.5],
  ['warning on surface-raised', '--color-warning', '--color-surface-raised', 4.5],
  ['on-warning on warning-surface', '--color-on-warning', '--color-warning-surface', 4.5],
  ['focus ring on surface', '--color-focus', '--color-surface', 3],
  ['focus ring on surface-raised', '--color-focus', '--color-surface-raised', 3],
  ['border-strong on surface-raised', '--color-border-strong', '--color-surface-raised', 3],
  ['border-strong on surface', '--color-border-strong', '--color-surface', 3],
  ['border-strong on subtle-surface (input on a panel)', '--color-border-strong', '--color-subtle-surface', 3],
];

function sourceFiles(dir: string): string[] {
  return readdirSync(dir).flatMap((entry) => {
    const full = join(dir, entry);
    if (statSync(full).isDirectory()) {
      return entry === 'api' ? [] : sourceFiles(full);
    }
    return ['.astro', '.tsx', '.ts', '.css'].includes(extname(full)) &&
      !full.endsWith('.test.ts') &&
      !full.endsWith('tokens.css')
      ? [full]
      : [];
  });
}

describe('design tokens', () => {
  const css = readFileSync(tokensPath, 'utf8');
  const { light, dark, darkOnly } = schemes(css);
  const tokens = light;
  const bySchema: Array<[string, Map<string, string>]> = [
    ['light', light],
    ['dark', dark],
  ];

  it('declares every color role a component uses', () => {
    const missing = requiredRoles.filter((role) => !tokens.has(role));
    expect(missing).toEqual([]);
  });

  it('repoints every color role in one prefers-color-scheme dark block', () => {
    expect(css.split(darkQuery).length - 1).toBe(1);
    expect(css).not.toMatch(/\.dark\b/);
    const missing = requiredRoles.filter((role) => !darkOnly.has(role));
    expect(missing).toEqual([]);
  });

  it('declares both appearances to the browser', () => {
    expect(css).toMatch(/color-scheme:\s*light dark/);
  });

  it.each(
    bySchema.flatMap(([scheme, map]) =>
      contrastPairs.map(([label, fg, bg, min]) => [scheme, label, fg, bg, min, map] as const),
    ),
  )('%s: %s', (scheme, label, foreground, background, minimum, map) => {
    const ratio = contrastRatio(resolveToken(map, foreground), resolveToken(map, background));
    expect(
      Number(ratio.toFixed(2)),
      `${scheme} ${label}: ${foreground} on ${background} measured ${ratio.toFixed(2)}:1 (min ${minimum})`,
    ).toBeGreaterThanOrEqual(minimum);
  });

  it('declares elevation shadows for both appearances', () => {
    for (const name of ['--shadow-card', '--shadow-card-hover']) {
      expect(light.has(name), `${name} light`).toBe(true);
      expect(darkOnly.has(name), `${name} dark`).toBe(true);
    }
  });

  it('sets Inter Variable first in the sans stack and ships it as a dependency', () => {
    expect(tokens.get('--font-sans')).toMatch(/^'Inter Variable',/);
    const pkg = JSON.parse(readFileSync(join(webRoot, 'package.json'), 'utf8'));
    expect(pkg.dependencies['@fontsource-variable/inter']).toBeDefined();
  });

  const primitiveName = /^--color-(slate|blue|green|red|amber)-\d+$/;
  const primitives = [...tokens.entries()].filter(([name]) => primitiveName.test(name));

  it('keeps every declared color primitive inside the sRGB gamut', () => {
    const outOfGamut = primitives
      .filter(([, value]) => !inSrgbGamut(parseColor(value)))
      .map(([name]) => name);
    expect(outOfGamut).toEqual([]);
  });

  it('declares every color primitive in oklch', () => {
    const nonOklch = primitives
      .filter(([, value]) => !value.startsWith('oklch('))
      .map(([name]) => name);
    expect(nonOklch).toEqual([]);
  });

  it.each(bySchema)('points every %s role at a declared primitive', (_scheme, map) => {
    const roles = [...map.entries()].filter(
      ([name]) => name.startsWith('--color-') && !primitiveName.test(name),
    );
    const dangling = roles
      .filter(([, value]) => !/^var\(\s*--color-(slate|blue|green|red|amber)-\d+\s*\)$/.test(value))
      .map(([name, value]) => `${name}: ${value}`);
    expect(dangling).toEqual([]);
  });

  it('keeps components free of raw color literals', () => {
    const offenders: string[] = [];
    for (const file of sourceFiles(join(webRoot, 'src'))) {
      const contents = readFileSync(file, 'utf8');
      if (/#[0-9a-fA-F]{3,8}\b|oklch\(|rgba?\(|hsla?\(/.test(contents)) {
        offenders.push(relative(webRoot, file));
      }
    }
    expect(offenders).toEqual([]);
  });
});
