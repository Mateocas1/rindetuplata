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

const recipesPath = join(webRoot, 'src/styles/recipes.css');
const globalPath = join(webRoot, 'src/styles/global.css');

type Oklch = { l: number; c: number; h: number; alpha?: number };

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
  const match = /^oklch\(\s*([\d.]+%?)\s+([\d.]+)\s+([\d.]+)\s*(?:\/\s*([\d.]+))?\s*\)$/.exec(value);
  if (!match) {
    throw new Error(`Unsupported color notation in tokens.css: ${value}`);
  }
  const lightness = match[1].endsWith('%')
    ? Number.parseFloat(match[1]) / 100
    : Number.parseFloat(match[1]);
  return {
    l: lightness,
    c: Number.parseFloat(match[2]),
    h: Number.parseFloat(match[3]),
    alpha: match[4] === undefined ? 1 : Number.parseFloat(match[4]),
  };
}

const toGamma = (v: number) => (v <= 0.0031308 ? 12.92 * v : 1.055 * v ** (1 / 2.4) - 0.055);
const toLinear = (v: number) => (v <= 0.04045 ? v / 12.92 : ((v + 0.055) / 1.055) ** 2.4);

/** Source-over in gamma sRGB, as browsers blend: returns the opaque result's linear channels. */
function composite(layers: Oklch[]): [number, number, number] {
  let [r, g, b] = oklchToLinearSrgb({ ...layers[0], alpha: 1 }).map((v) => toGamma(Math.min(1, Math.max(0, v))));
  for (const layer of layers.slice(1)) {
    const a = layer.alpha ?? 1;
    const top = oklchToLinearSrgb(layer).map((v) => toGamma(Math.min(1, Math.max(0, v))));
    [r, g, b] = [r + (top[0] - r) * a, g + (top[1] - g) * a, b + (top[2] - b) * a];
  }
  return [toLinear(r), toLinear(g), toLinear(b)];
}

function ratioOfLuminances(a: number, b: number): number {
  return (Math.max(a, b) + 0.05) / (Math.min(a, b) + 0.05);
}

const luminanceOfLinear = ([r, g, b]: [number, number, number]) => 0.2126 * r + 0.7152 * g + 0.0722 * b;

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
  '--color-thumb',
  '--color-on-thumb',
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
  // T12c status surfaces: the destructive confirmation fills with the loss role
  // and every notice borders its own tint, so each combination is measured too.
  ['on-accent on loss (destructive confirmation)', '--color-on-accent', '--color-loss', 4.5],
  ['loss on loss-surface (notice border)', '--color-loss', '--color-loss-surface', 3],
  ['gain on gain-surface (notice border)', '--color-gain', '--color-gain-surface', 3],
  ['warning on warning-surface (notice border)', '--color-warning', '--color-warning-surface', 3],
  // T13a segmented control: the selected thumb is a shape on the track (subtle-surface)
  // and carries its own ink (L81: the dark prototype read grey-on-grey).
  ['on-thumb on thumb (selected segment)', '--color-on-thumb', '--color-thumb', 4.5],
  ['thumb on subtle-surface (segment track)', '--color-thumb', '--color-subtle-surface', 3],
  ['muted on subtle-surface (unselected segment)', '--color-muted', '--color-subtle-surface', 4.5],
];

/** T13a glass: tokens declared outside @theme, repointed in the dark block. */
const glassTokens = ['--glass-fill', '--glass-line', '--glass-lift', '--glow', '--grid-line', '--surface-lift'];

/** The seven text roles of Cobalto Cristal (L77: at most 7), each one `@utility type-*`. */
const textRoles = ['label', 'display', 'section', 'figure', 'body', 'data', 'index'];

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

  it('declares the glass and surface-ladder tokens for both appearances', () => {
    for (const name of glassTokens) {
      expect(light.has(name), `${name} light`).toBe(true);
      expect(darkOnly.has(name), `${name} dark`).toBe(true);
    }
  });

  // Worst case under the glass: the glow at full strength over the page surface.
  it.each(
    bySchema.flatMap(([scheme, map]) =>
      (['--color-text', '--color-muted'] as const).map((fg) => [scheme, fg, map] as const),
    ),
  )('%s: %s on glass over the glow holds 4.5:1', (scheme, foreground, map) => {
    const behind = composite([
      parseColor(resolveToken(map, '--color-surface')),
      parseColor(map.get('--glow')!),
      parseColor(map.get('--glass-fill')!),
    ]);
    const ratio = ratioOfLuminances(
      relativeLuminance(parseColor(resolveToken(map, foreground))),
      luminanceOfLinear(behind),
    );
    expect(Number(ratio.toFixed(2)), `${scheme} ${foreground} on glass ${ratio.toFixed(2)}:1`).toBeGreaterThanOrEqual(4.5);
  });

  it('ships the four Cobalto Cristal families and no Inter', () => {
    expect(tokens.get('--font-sans')).toMatch(/^'Archivo Variable',/);
    expect(tokens.get('--font-display')).toMatch(/^'Space Grotesk Variable',/);
    expect(tokens.get('--font-mono')).toMatch(/^'Geist Mono Variable',/);
    expect(tokens.get('--font-serif')).toMatch(/^'Instrument Serif',/);
    const pkg = JSON.parse(readFileSync(join(webRoot, 'package.json'), 'utf8'));
    for (const dep of [
      '@fontsource-variable/archivo',
      '@fontsource-variable/space-grotesk',
      '@fontsource-variable/geist-mono',
      '@fontsource/instrument-serif',
    ]) {
      expect(pkg.dependencies[dep], dep).toBeDefined();
    }
    expect(pkg.dependencies['@fontsource-variable/inter']).toBeUndefined();
    const global = readFileSync(globalPath, 'utf8');
    expect(global).not.toMatch(/inter/i);
    expect(global).toMatch(/instrument-serif\/400-italic\.css/);
  });

  it('defines exactly the seven text roles and the one serif accent as recipes', () => {
    const recipes = readFileSync(recipesPath, 'utf8');
    const declared = [...recipes.matchAll(/@utility type-([a-z-]+)/g)].map((m) => m[1]);
    expect(declared.sort()).toEqual([...textRoles].sort());
    expect(recipes).toMatch(/@utility serif-accent/);
    for (const step of ['label', 'data', 'body', 'lead', 'section', 'figure', 'figure-row', 'display']) {
      expect(tokens.has(`--text-${step}`), `--text-${step}`).toBe(true);
      expect(tokens.has(`--text-${step}--line-height`), `--text-${step}--line-height`).toBe(true);
    }
  });

  it('retires the T12 text steps, so only the seven roles carry a size (T13e item 2)', () => {
    for (const step of ['caption', 'body-sm', 'h1', 'h2', 'h3']) {
      expect(tokens.has(`--text-${step}`), `--text-${step} must be gone`).toBe(false);
    }
  });

  it('never uses a retired text step in a recipe or a component (T13e item 2)', () => {
    const retired = /\btext-(caption|body-sm|h1|h2|h3)\b/;
    const offenders = sourceFiles(join(webRoot, 'src'))
      .filter((file) => retired.test(readFileSync(file, 'utf8')))
      .map((file) => relative(webRoot, file));
    expect(offenders).toEqual([]);
  });

  it('makes the glass opaque without backdrop-filter or under reduced transparency', () => {
    const recipes = readFileSync(recipesPath, 'utf8');
    expect(recipes).toMatch(/@utility glass\b/);
    expect(recipes).toMatch(/@utility glass-field\b/);
    expect(recipes).toMatch(/@supports not \(\(backdrop-filter/);
    expect(recipes).toMatch(/prefers-reduced-transparency: reduce/);
  });

  it('carries links with ink and the underline, never with the one accent (T13e item 4)', () => {
    const recipes = readFileSync(recipesPath, 'utf8');
    const link = /@utility link \{([\s\S]*?)\n\}/.exec(recipes)?.[1] ?? '';
    expect(link).toContain('text-text');
    expect(link).toContain('underline');
    expect(link).not.toContain('text-accent');
  });

  it('names the wordmark as the one documented exception to the roles (T13e item 6)', () => {
    const recipes = readFileSync(recipesPath, 'utf8');
    const body = /@utility wordmark \{([\s\S]*?)\n\}/.exec(recipes)?.[1] ?? '';
    // It takes the display family at the body step from the tokens, so it is a
    // recipe and not a hand-rolled pair of utilities.
    expect(body).toContain('var(--font-display)');
    expect(body).toContain('var(--text-body)');
    expect(recipes).toMatch(/wordmark[\s\S]{0,600}?exception/i);
    // Nothing else re-derives display type: the exception lives in one place.
    const offenders = sourceFiles(join(webRoot, 'src'))
      .filter((file) => !file.endsWith('recipes.css'))
      .filter((file) => /font-display/.test(readFileSync(file, 'utf8')))
      .map((file) => relative(webRoot, file));
    expect(offenders).toEqual([]);
  });

  const primitiveName = /^--color-(graphite|cobalt|green|red|amber)-\d+$/;
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
      .filter(([, value]) => !/^var\(\s*--color-(graphite|cobalt|green|red|amber)-\d+\s*\)$/.test(value))
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
