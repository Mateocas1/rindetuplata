/**
 * The wip: what the lens sees while it is travelling.
 *
 * Between two beats the frame would otherwise be an empty room for a third of a
 * second, which is exactly what makes a piece feel cut together. So the transit
 * is filled with its own event: radial light streaks whose length, speed and
 * brightness are driven by the camera's real velocity, plus a cobalt flare at
 * the vanishing point. Because it is derived from the spline, it can never drift
 * out of sync with the move.
 */
import { rng } from '../lib/math';
import { cameraAt } from '../lib/camera';
import { glow } from '../theme';

const STREAKS = (() => {
  const r = rng(9137);
  return Array.from({ length: 84 }, () => ({
    angle: r() * Math.PI * 2,
    phase: r(),
    speed: 1.4 + r() * 2.2,
    width: 0.6 + r() * 1.7,
    reach: 0.55 + r() * 0.85,
  }));
})();

/** Units per second along the corridor, plus the lateral and lens components. */
function rushAmount(t: number) {
  const a = cameraAt(t - 0.02);
  const b = cameraAt(t + 0.02);
  const dz = Math.abs(b.z - a.z) / 0.04;
  const dx = Math.abs(b.x - a.x) / 0.04;
  const dy = Math.abs(b.y - a.y) / 0.04;
  const dzoom = Math.abs(b.zoom - a.zoom) / 0.04;
  const v = dz + dx * 0.8 + dy * 0.8 + dzoom * 900;
  return Math.min(1, Math.max(0, (v - 260) / 1900));
}

export const SpeedRush: React.FC<{ t: number }> = ({ t }) => {
  const k = rushAmount(t);
  if (k <= 0.004) return null;

  const cx = 960;
  const cy = 540;

  return (
    <>
      <svg
        width={1920}
        height={1080}
        style={{ position: 'absolute', inset: 0, pointerEvents: 'none' }}
        aria-hidden
      >
        {STREAKS.map((s, i) => {
          const travel = (t * s.speed + s.phase) % 1;
          const inner = 160 + travel * 1250;
          const length = (60 + 420 * k) * s.reach * (0.35 + travel);
          const outer = inner + length;
          const opacity = k * (1 - travel) * 0.55 * Math.min(1, travel * 3.2);
          const cos = Math.cos(s.angle);
          const sin = Math.sin(s.angle);
          return (
            <line
              key={i}
              x1={cx + cos * inner}
              y1={cy + sin * inner * 0.62}
              x2={cx + cos * outer}
              y2={cy + sin * outer * 0.62}
              stroke={glow(0.9)}
              strokeWidth={s.width * (0.6 + k)}
              strokeLinecap="round"
              opacity={opacity}
            />
          );
        })}
      </svg>

      {/* A flare at the point the lens is heading for. */}
      <div
        style={{
          position: 'absolute',
          left: '50%',
          top: '50%',
          width: 1500,
          height: 1100,
          marginLeft: -750,
          marginTop: -550,
          background: `radial-gradient(closest-side, ${glow(0.34 * k)} 0%, transparent 68%)`,
          pointerEvents: 'none',
        }}
      />
    </>
  );
};
