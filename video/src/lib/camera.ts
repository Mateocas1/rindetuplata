/**
 * One camera for fifteen seconds.
 *
 * Every channel is a monotone cubic spline through hand-placed keys: C1
 * continuous end to end, so the vehicle never stops dead at a beat boundary and
 * the film cannot read as a series of shots. Depth is real (a 3D stage), so a
 * move is a dolly; `zoom` is a separate lens channel, which lets the piece cut
 * between a dolly and a lens push without a single edit.
 */
import { monotoneCubic } from './math';

/**
 * Beats. The camera path below is authored against these, and the scenes read
 * the same map, so retiming a beat is one edit everywhere.
 */
export const BEAT = {
  hook: { start: 0.0, end: 2.35 },
  thesis: { start: 2.35, end: 5.45 },
  benefits: { start: 5.45, end: 8.35 },
  features: { start: 8.35, end: 11.35 },
  positioning: { start: 11.35, end: 13.3 },
  outro: { start: 13.3, end: 15.0 },
} as const;

export const DURATION = 15;
export const FPS = 60;

/** World spacing between beats. Close enough that the next beat is already
 * waiting behind this one — the single-take tell — far enough to read as depth. */
export const ZONE_GAP = 950;
export const PERSPECTIVE = 2400;

export const ZONE_INDEX: Record<keyof typeof BEAT, number> = {
  hook: 0,
  thesis: 1,
  benefits: 2,
  features: 3,
  positioning: 4,
  outro: 5,
};

type Key = {
  t: number;
  /** Camera position along the corridor; the active zone sits at its own depth. */
  z: number;
  x: number;
  y: number;
  /** Look rotation, degrees, about the eye. */
  rx: number;
  ry: number;
  /** Lens zoom, not a dolly: it magnifies without changing depth of field. */
  zoom: number;
};

/**
 * The score. Holds are wide, transits are short and fast: that ratio is what
 * makes travel feel like travel instead of a slideshow.
 */
const KEYS: Key[] = [
  { t: 0.0, z: -420, x: 0, y: 26, rx: 2.2, ry: 0, zoom: 1.0 },
  { t: 0.62, z: 6, x: 0, y: 0, rx: 0, ry: 0, zoom: 1.0 },
  { t: 1.5, z: 120, x: 0, y: -12, rx: -0.7, ry: 0, zoom: 1.005 },
  { t: 2.08, z: 190, x: 0, y: -20, rx: -1.1, ry: 0, zoom: 1.012 },
  { t: 2.42, z: 950, x: 0, y: 0, rx: 0, ry: 0, zoom: 1.0 },
  { t: 3.2, z: 1032, x: -74, y: 2, rx: 0.6, ry: 5, zoom: 1.008 },
  { t: 4.12, z: 1104, x: -18, y: -26, rx: -1.0, ry: -4, zoom: 1.02 },
  { t: 4.86, z: 982, x: 64, y: 0, rx: 0.4, ry: 6, zoom: 1.04 },
  { t: 5.2, z: 1012, x: 372, y: -163, rx: -0.7, ry: 2, zoom: 1.34 },
  { t: 5.5, z: 1900, x: 0, y: 0, rx: 0, ry: 0, zoom: 1.0 },
  // Benefits: a slow dolly with a slight yaw, never a snap.
  { t: 6.3, z: 1948, x: -26, y: 0, rx: 0, ry: -2.4, zoom: 1.008 },
  { t: 7.16, z: 2022, x: 14, y: -34, rx: 1.0, ry: 1.2, zoom: 1.014 },
  { t: 7.9, z: 2086, x: 0, y: -52, rx: 1.4, ry: 0, zoom: 1.02 },
  // Features: the camera trucks across the row; the panel at the lens is the sharp one.
  // Features: the truck never stops. The row passes the lens, it does not
  // arrive and wait, so the beat has no still frame to fall asleep on.
  { t: 8.38, z: 2850, x: -300, y: 0, rx: 0, ry: 3.5, zoom: 1.0 },
  { t: 9.02, z: 2904, x: -560, y: 0, rx: -1.6, ry: 6.5, zoom: 1.02 },
  { t: 9.68, z: 2962, x: 130, y: 0, rx: 0, ry: -1, zoom: 1.03 },
  { t: 10.32, z: 3018, x: 580, y: 0, rx: 2.2, ry: -9, zoom: 1.02 },
  { t: 10.9, z: 3074, x: 430, y: 0, rx: 0, ry: -3, zoom: 1.01 },
  // Positioning: the breath, but still drifting, and it keeps pushing to the
  // last frame before the outro takes over.
  { t: 11.4, z: 3800, x: 0, y: 0, rx: 0, ry: 0, zoom: 1.0 },
  { t: 12.2, z: 3822, x: -34, y: -6, rx: 0, ry: 0, zoom: 1.02 },
  { t: 13.0, z: 3850, x: -72, y: -10, rx: 0, ry: 0, zoom: 1.05 },
  // Outro: settles rather than stops.
  { t: 13.34, z: 4750, x: 0, y: 0, rx: 0, ry: 0, zoom: 1.0 },
  { t: 14.2, z: 4792, x: 0, y: -7, rx: 0, ry: 0, zoom: 1.022 },
  { t: 15.0, z: 4834, x: 0, y: -12, rx: 0, ry: 0, zoom: 1.075 },
];

const at = (get: (k: Key) => number) => monotoneCubic(KEYS.map((k) => k.t), KEYS.map(get));

const cz = at((k) => k.z);
const cx = at((k) => k.x);
const cy = at((k) => k.y);
const crx = at((k) => k.rx);
const cry = at((k) => k.ry);
const czoom = at((k) => k.zoom);

export type Camera = { z: number; x: number; y: number; rx: number; ry: number; zoom: number };

/** A breath on top of the spline: the rig is hand-held, not a tripod. */
function handheld(t: number) {
  return {
    x: Math.sin(t * 1.31) * 2.6 + Math.sin(t * 2.73 + 1.2) * 1.1,
    y: Math.cos(t * 1.07 + 0.4) * 2.2 + Math.sin(t * 3.1 + 2.1) * 0.8,
    rx: Math.sin(t * 0.91 + 0.7) * 0.08,
    ry: Math.cos(t * 1.13 + 1.9) * 0.09,
  };
}

export function cameraAt(t: number): Camera {
  const h = handheld(t);
  return {
    z: cz(t),
    x: cx(t) + h.x,
    y: cy(t) + h.y,
    rx: crx(t) + h.rx,
    ry: cry(t) + h.ry,
    zoom: czoom(t),
  };
}

/** Relative depth of a zone: 0 means it sits exactly on the lens plane. */
export const depthOf = (zone: number, cameraZ: number) => cameraZ - zone * ZONE_GAP;

/** Projected scale of a plane at that depth. */
export const scaleAt = (depth: number) => PERSPECTIVE / (PERSPECTIVE - depth);

/**
 * A beat is only alive while the camera is near it: it resolves out of the far
 * distance and is gone before it can ghost through the next one.
 */
export function zoneVisibility(depth: number) {
  // A beat resolves out of the distance only for the last stretch of the move
  // and holds until well after the lens passes it, so a transit is always a
  // cross-fade through motion and never a gap.
  const appear = depth < -980 ? 0 : depth < -560 ? (depth + 980) / 420 : 1;
  const leave = depth > 380 ? Math.max(0, 1 - (depth - 380) / 520) : 1;
  return Math.min(appear, leave);
}

/** Depth of field: nothing at the focus plane, plenty in the far distance. */
export function zoneBlur(depth: number) {
  const d = Math.abs(depth);
  if (d < 140) return 0;
  return Math.min(9, ((d - 140) / 900) ** 1.25 * 9);
}

/** How far past the lens a zone travels on its way out, in px at scale 1. */
export const zoneExitDrift = (depth: number) => (depth > 0 ? depth * 0.34 : 0);
