/**
 * Brand values, copied from `web/src/styles/tokens.css` (Cobalto Cristal).
 * The film must look like the product, not like a video about it, so nothing
 * here is invented: every colour is the site's own role token, resolved for the
 * dark appearance (the one a film wants).
 */
export const C = {
  surface: 'oklch(0.159 0.011 268)',
  surfaceDeep: 'oklch(0.11 0.012 268)',
  raised: 'oklch(0.206 0.021 272)',
  subtle: 'oklch(0.241 0.024 270)',
  line: 'oklch(0.3 0.022 268)',
  lineStrong: 'oklch(0.56 0.031 264)',
  text: 'oklch(0.967 0.006 265)',
  muted: 'oklch(0.756 0.022 264)',
  dim: 'oklch(0.56 0.031 264)',
  accent: 'oklch(0.692 0.161 273)',
  accentDeep: 'oklch(0.488 0.232 267)',
  accentSoft: 'oklch(0.8 0.1 271)',
  gain: 'oklch(0.76 0.14 152)',
  loss: 'oklch(0.72 0.15 25)',
  warn: 'oklch(0.8 0.13 80)',
} as const;

const oklch = (l: number, c: number, h: number, a: number) => `oklch(${l} ${c} ${h} / ${a})`;

/** The cobalt the brand spends on exactly one job: this is it, with alpha. */
export const glow = (a: number) => oklch(0.692, 0.161, 273, a);
export const glowDeep = (a: number) => oklch(0.488, 0.232, 267, a);
export const ink = (a: number) => oklch(0.967, 0.006, 265, a);
export const gainGlow = (a: number) => oklch(0.76, 0.14, 152, a);

export const FONT = {
  display: "'Space Grotesk Variable', ui-sans-serif, system-ui, sans-serif",
  sans: "'Archivo Variable', system-ui, -apple-system, sans-serif",
  mono: "'Geist Mono Variable', ui-monospace, monospace",
  serif: "'Instrument Serif', ui-serif, Georgia, serif",
} as const;

/** The site's nearly-square panel: 3px, hairline border, no shadow. */
export const panel = {
  border: `1px solid ${C.line}`,
  borderRadius: 3,
  background: C.raised,
} as const;

/**
 * Alpha compositing for token colours.
 *
 * The tokens are resolved `oklch(L C H)` strings, so appending a hex suffix
 * (`${C.text}14`) yields invalid CSS and the paint silently disappears. Every
 * translucent surface in the film goes through this instead.
 */
export function alpha(color: string, a: number): string {
  const match = /^oklch\(([^/)]+?)\s*(?:\/\s*[\d.]+)?\)$/.exec(color.trim());
  if (!match) return color;
  return `oklch(${match[1].trim()} / ${a})`;
}
