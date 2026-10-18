/**
 * The world: everything that survives a beat change.
 *
 * A perspective floor, drifting dust and a field of finance tokens share one 3D
 * space with the beats, so the moment between two beats is never empty and never
 * a black card: the camera is simply somewhere else in the same room.
 */
import { AbsoluteFill, useCurrentFrame, useVideoConfig } from 'remotion';
import { C, glow, glowDeep, ink } from '../theme';
import { cameraAt, PERSPECTIVE, ZONE_GAP } from '../lib/camera';
import { rng, smoothstep } from '../lib/math';

const TOKENS = [
  '24,00%', '19,00%', '21,1%', '−0,04%', '+0,38%', '1,59%', 'TNA', 'TEA',
  'REM', 'BCRA', 'INDEC', '30 d', '$1.019.726', 'plazo fijo', 'billeteras',
  'money market', '23,58%', '18,80%', '22,50%', '+0,34%', 'TIR', 'CFT',
  '$1.000.000', '60 d', '360 d', 'rendimiento real', 'sin comisiones',
];

type Particle = { x: number; y: number; fz: number; size: number; o: number; speed: number };
type Token = { x: number; y: number; fz: number; size: number; o: number; text: string; rot: number };

function buildField() {
  const r = rng(20261014);
  const particles: Particle[] = [];
  for (let i = 0; i < 84; i++) {
    particles.push({
      x: (r() - 0.5) * 5200,
      y: (r() - 0.5) * 2600,
      fz: r() * 12800,
      size: 1.4 + r() * 3.4,
      o: 0.1 + r() * 0.45,
      speed: 6 + r() * 22,
    });
  }
  const tokens: Token[] = [];
  for (let i = 0; i < 46; i++) {
    tokens.push({
      x: (r() - 0.5) * 5400,
      y: (r() - 0.5) * 2200,
      fz: r() * 13000,
      size: 12 + r() * 20,
      o: 0.03 + r() * 0.1,
      text: TOKENS[Math.floor(r() * TOKENS.length)],
      rot: (r() - 0.5) * 8,
    });
  }
  return { particles, tokens };
}

const FIELD = buildField();

/** Render a thing only if it can actually be on screen. */
const near = (d: number) => d > -PERSPECTIVE * 0.82 && d < PERSPECTIVE * 0.55;

export const World: React.FC<{ t: number }> = ({ t }) => {
  const cam = cameraAt(t);
  // The room clears for the wordmark: the last frame must hold nothing but the
  // brand, so the drifting data field goes quiet going into the outro.
  const quiet = 1 - smoothstep(13.15, 14.3, t);

  return (
    <>
      {/* The floor: a hairline grid receding to a horizon, scrolling under the camera. */}
      <div
        style={{
          position: 'absolute',
          left: '50%',
          top: '50%',
          width: 6400,
          height: 6400,
          marginLeft: -3200,
          marginTop: -3200,
          transform: `translateY(${540 - cam.y * 0.35}px) rotateX(79deg) translateY(${(cam.z * 0.26) % 150}px)`,
          backgroundImage: `repeating-linear-gradient(90deg, ${ink(0.05)} 0 1px, transparent 1px 150px), repeating-linear-gradient(0deg, ${ink(0.045)} 0 1px, transparent 1px 150px)`,
          maskImage: 'radial-gradient(closest-side at 50% 50%, #000 6%, transparent 52%)',
          WebkitMaskImage: 'radial-gradient(closest-side at 50% 50%, #000 6%, transparent 52%)',
          opacity: 0.35 + 0.65 * quiet,
        }}
      />

      {/* Dust. Physical parallax is what makes the dolly read as a dolly. */}
      {FIELD.particles.map((p, i) => {
        const d = cam.z - p.fz;
        if (!near(d)) return null;
        const s = PERSPECTIVE / (PERSPECTIVE - d);
        const drift = (t * p.speed) % 2600;
        return (
          <div
            key={`p${i}`}
            style={{
              position: 'absolute',
              left: '50%',
              top: '50%',
              width: p.size,
              height: p.size,
              borderRadius: '50%',
              background: i % 7 === 0 ? glow(0.9) : ink(0.9),
              opacity: quiet * p.o * Math.min(1, s * 1.6) * (d < -2400 ? 0.2 : 1),
              transform: `translate3d(${p.x + drift * 0.18}px, ${p.y - drift * 0.55}px, ${d}px) scale(${s})`,
            }}
          />
        );
      })}

      {/* The data field: the product's own vocabulary, drifting through the whole film. */}
      {FIELD.tokens.map((k, i) => {
        const d = cam.z - k.fz;
        if (!near(d)) return null;
        const s = PERSPECTIVE / (PERSPECTIVE - d);
        const drift = (t * 9) % 1300;
        return (
          <div
            key={`t${i}`}
            style={{
              position: 'absolute',
              left: '50%',
              top: '50%',
              fontFamily: "'Geist Mono Variable', monospace",
              fontSize: k.size,
              letterSpacing: '0.02em',
              color: i % 5 === 0 ? glow(0.55) : ink(0.8),
              opacity: quiet * k.o * Math.min(1, s) * (d < -1800 ? 0.1 : 1),
              whiteSpace: 'nowrap',
              transform: `translate3d(${k.x}px, ${k.y - drift}px, ${d}px) rotate(${k.rot}deg) scale(${s})`,
              transformOrigin: 'center',
            }}
          >
            {k.text}
          </div>
        );
      })}
    </>
  );
};

/**
 * Screen-space atmosphere. Independent of the camera on purpose: a vignette and
 * a bloom are lens artefacts, and a lens does not travel with the subject.
 */
export const Sky: React.FC<{ t: number }> = ({ t }) => {
  const cam = cameraAt(t);
  const breathe = 1 + Math.sin(t * 0.6) * 0.06;

  return (
    <>
      <AbsoluteFill
        style={{
          background: `radial-gradient(115% 85% at 50% 46%, oklch(0.205 0.032 272) 0%, oklch(0.148 0.018 270) 46%, ${C.surfaceDeep} 100%)`,
        }}
      />
      {/* Cobalt bloom — the brand's one accent, doing its one job. */}
      <div
        style={{
          position: 'absolute',
          left: '50%',
          top: '50%',
          width: 2100 * breathe,
          height: 1500 * breathe,
          marginLeft: -1050 * breathe,
          marginTop: -750 * breathe,
          background: `radial-gradient(closest-side, ${glow(0.3)} 0%, ${glowDeep(0.14)} 42%, transparent 72%)`,
          transform: `translate(${cam.x * -0.22}px, ${cam.y * -0.22}px)`,
        }}
      />
      <div
        style={{
          position: 'absolute',
          left: '50%',
          top: '50%',
          width: 1200,
          height: 1200,
          marginLeft: -600,
          marginTop: -600,
          background: `radial-gradient(closest-side, ${glow(0.16)} 0%, transparent 68%)`,
          transform: `translate(${cam.x * -0.55 + 380}px, ${cam.y * -0.55 - 240}px)`,
        }}
      />
    </>
  );
};

/** In front of the world: artefacts of the lens, never of the room. */
export const Lens: React.FC<{ t: number }> = () => (
  <>
    <AbsoluteFill
      style={{
        background:
          'radial-gradient(120% 96% at 50% 50%, transparent 40%, oklch(0.08 0.01 268 / 0.5) 80%, oklch(0.06 0.01 268 / 0.92) 100%)',
      }}
    />
  </>
);

/** Convenience: the depth-derived scale of a zone, for scenes that need it. */
export const useCameraFrame = () => {
  const frame = useCurrentFrame();
  const { fps } = useVideoConfig();
  const t = frame / fps;
  return { t, cam: cameraAt(t) };
};

export const ZONE_DEPTH = ZONE_GAP;
