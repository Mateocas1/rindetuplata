/**
 * Timing primitives. Everything is a pure function of seconds, never of a
 * frame count, so the identical animation renders at 60 fps for the master and
 * at 180 fps for the motion-blur pass.
 */

export const clamp = (v: number, min: number, max: number) => (v < min ? min : v > max ? max : v);
export const clamp01 = (v: number) => clamp(v, 0, 1);
export const lerp = (a: number, b: number, t: number) => a + (b - a) * t;

/** Normalised progress of `v` through [a, b], clamped. */
export const inv = (a: number, b: number, v: number) => clamp01((v - a) / (b - a));

export const smoothstep = (a: number, b: number, v: number) => {
  const t = inv(a, b, v);
  return t * t * (3 - 2 * t);
};

export const easeOutCubic = (t: number) => 1 - Math.pow(1 - t, 3);
export const easeInCubic = (t: number) => t * t * t;
export const easeInOutCubic = (t: number) => (t < 0.5 ? 4 * t * t * t : 1 - Math.pow(-2 * t + 2, 3) / 2);
export const easeOutQuint = (t: number) => 1 - Math.pow(1 - t, 5);
export const easeInOutQuint = (t: number) =>
  t < 0.5 ? 16 * t * t * t * t * t : 1 - Math.pow(-2 * t + 2, 5) / 2;
export const easeOutExpo = (t: number) => (t >= 1 ? 1 : 1 - Math.pow(2, -10 * t));
export const easeInOutExpo = (t: number) =>
  t <= 0 ? 0 : t >= 1 ? 1 : t < 0.5 ? Math.pow(2, 20 * t - 10) / 2 : (2 - Math.pow(2, -20 * t + 10)) / 2;

/** A cubic-bezier timing function, the CSS curve the brand already uses. */
export function bezier(x1: number, y1: number, x2: number, y2: number) {
  const cx = 3 * x1;
  const bx = 3 * (x2 - x1) - cx;
  const ax = 1 - cx - bx;
  const cy = 3 * y1;
  const by = 3 * (y2 - y1) - cy;
  const ay = 1 - cy - by;
  const sampleX = (t: number) => ((ax * t + bx) * t + cx) * t;
  const sampleY = (t: number) => ((ay * t + by) * t + cy) * t;
  const slopeX = (t: number) => (3 * ax * t + 2 * bx) * t + cx;
  return (x: number) => {
    let t = x;
    for (let i = 0; i < 6; i++) {
      const dx = sampleX(t) - x;
      if (Math.abs(dx) < 1e-6) break;
      const d = slopeX(t);
      if (Math.abs(d) < 1e-6) break;
      t -= dx / d;
    }
    return sampleY(clamp01(t));
  };
}

/**
 * Fritsch–Carlson monotone cubic interpolation over non-uniform keys.
 *
 * The camera is one continuous path for the whole film, so it needs C1
 * continuity across every key or the vehicle feels like it is being cut
 * together. This interpolation never overshoots, never rings, and keeps a
 * monotone channel monotone: a dolly-in stays a dolly-in.
 */
export function monotoneCubic(xs: number[], ys: number[]): (x: number) => number {
  const n = xs.length;
  if (n === 1) return () => ys[0];

  const h: number[] = [];
  const d: number[] = [];
  for (let i = 0; i < n - 1; i++) {
    h[i] = xs[i + 1] - xs[i];
    d[i] = (ys[i + 1] - ys[i]) / h[i];
  }

  const m: number[] = new Array(n).fill(0);
  m[0] = d[0];
  m[n - 1] = d[n - 2];
  for (let i = 1; i < n - 1; i++) {
    if (d[i - 1] * d[i] <= 0) m[i] = 0;
    else m[i] = (d[i - 1] + d[i]) / 2;
  }

  // Fritsch–Carlson: keep every interval monotone.
  for (let i = 0; i < n - 1; i++) {
    if (d[i] === 0) {
      m[i] = 0;
      m[i + 1] = 0;
      continue;
    }
    const a = m[i] / d[i];
    const b = m[i + 1] / d[i];
    const s = a * a + b * b;
    if (s > 9) {
      const tau = 3 / Math.sqrt(s);
      m[i] = tau * a * d[i];
      m[i + 1] = tau * b * d[i];
    }
  }

  return (x: number) => {
    if (x <= xs[0]) return ys[0];
    if (x >= xs[n - 1]) return ys[n - 1];
    let i = 0;
    while (i < n - 2 && x > xs[i + 1]) i++;
    const t = (x - xs[i]) / h[i];
    const t2 = t * t;
    const t3 = t2 * t;
    const h00 = 2 * t3 - 3 * t2 + 1;
    const h10 = t3 - 2 * t2 + t;
    const h01 = -2 * t3 + 3 * t2;
    const h11 = t3 - t2;
    return h00 * ys[i] + h10 * h[i] * m[i] + h01 * ys[i + 1] + h11 * h[i] * m[i + 1];
  };
}

/**
 * A decaying impulse: the snappy micro-move laid on top of the continuous
 * camera path (a push, a settle, a kick). Zero at its edges, so it can be
 * summed anywhere without breaking continuity.
 */
export function impulse(t: number, start: number, decay: number, freq = 1.6) {
  const dt = t - start;
  if (dt < 0) return 0;
  return Math.exp(-dt * decay) * Math.sin(dt * freq * Math.PI * 2);
}

/** Rising edge only: 0 before `start`, a soft step that settles at 1. */
export function settle(t: number, start: number, duration: number) {
  const dt = inv(start, start + duration, t);
  return 1 - Math.pow(1 - dt, 3);
}

/** Seeded pseudo-random, so a "random" field is identical on every render. */
export function rng(seed: number) {
  let s = seed >>> 0;
  return () => {
    s = (s * 1664525 + 1013904223) >>> 0;
    return s / 4294967296;
  };
}

/** Argentine number formatting — the product's own conventions. */
const arMoney = new Intl.NumberFormat('es-AR', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
const arInt = new Intl.NumberFormat('es-AR', { maximumFractionDigits: 0 });

/** "$ 1.019.726,03" with a non-breaking space, like the site. */
export const money = (v: number) => `$${arMoney.format(v).replace(/^/, '\u00A0')}`;
export const integer = (v: number) => arInt.format(v);
export const pct = (v: number, digits = 2) =>
  `${new Intl.NumberFormat('es-AR', { minimumFractionDigits: digits, maximumFractionDigits: digits }).format(v)}\u00A0%`;
