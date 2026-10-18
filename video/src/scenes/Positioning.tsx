/**
 * Beat 5 — positioning (11.35–13.30s).
 *
 * Calm after speed: no cards, one claim, the sources that back it. Two lines,
 * set at a size that holds them as two lines; the camera slows and pushes in a
 * few percent, the breath before the outro.
 */
import { AbsoluteFill } from 'remotion';
import { Zone } from '../world/Stage';
import type { Camera } from '../lib/camera';
import { Display, Eyebrow, FadeUp, Mono, OUT, WordReveal, useBeatT } from '../world/Type';
import { Chip } from '../ui/parts';
import { C } from '../theme';
import { inv } from '../lib/math';

const SOURCES = ['BCRA', 'INDEC', 'CÁMARA DE FCI', 'ENTIDADES'];

/** This beat is the calm one: it needs less head start than the rest. */
const POSITION_LEAD = 0.15;

export const Positioning: React.FC<{ cam: Camera }> = ({ cam }) => {
  const t = useBeatT(POSITION_LEAD);
  const rule = OUT(inv(12.0, 12.7, t));

  return (
    <Zone index={4} cam={cam} sharp lead={POSITION_LEAD}>
      <AbsoluteFill style={{ alignItems: 'center', justifyContent: 'center' }}>
        <div style={{ width: 1440, display: 'flex', flexDirection: 'column', alignItems: 'center' }}>
          <Eyebrow at={11.5}>Posición</Eyebrow>

          <Display
            size={72}
            style={{
              marginTop: 30,
              textAlign: 'center',
              letterSpacing: '-0.035em',
              lineHeight: 1.1,
              whiteSpace: 'nowrap',
            }}
          >
            <WordReveal text="Sin comisiones de nadie." at={11.56} stagger={0.045} duration={0.6} />
            <WordReveal
              text="Cada dato, con su fuente y su fecha."
              at={11.78}
              stagger={0.04}
              duration={0.6}
              style={{ marginTop: 8 }}
            />
          </Display>

          <div
            style={{
              marginTop: 52,
              width: 560,
              height: 1,
              background: `linear-gradient(90deg, transparent, ${C.lineStrong}, transparent)`,
              transform: `scaleX(${rule})`,
            }}
          />

          <FadeUp at={12.12} style={{ marginTop: 28 }}>
            <Mono size={18}>No vendemos productos. Mostramos números.</Mono>
          </FadeUp>

          <div style={{ marginTop: 42, display: 'flex', gap: 14 }}>
            {SOURCES.map((s, i) => (
              <Chip key={s} at={12.3} delay={i * 0.07}>
                {s}
              </Chip>
            ))}
          </div>
        </div>
      </AbsoluteFill>
    </Zone>
  );
};
