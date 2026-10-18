/**
 * Beat 6 — outro (13.30–15.00s).
 *
 * The wordmark as one word: "rinde" in the display face, "plata" in the serif
 * accent, no gap between them. A cobalt bloom behind it keeps growing to the
 * last frame, so the film settles instead of stopping dead.
 */
import { AbsoluteFill } from 'remotion';
import { Zone } from '../world/Stage';
import type { Camera } from '../lib/camera';
import { Display, FadeUp, Mono, OUT, Serif, Word, useBeatT } from '../world/Type';
import { C, glow } from '../theme';
import { clamp01, inv, lerp } from '../lib/math';

export const Outro: React.FC<{ cam: Camera }> = ({ cam }) => {
  const t = useBeatT();
  const bloomIn = OUT(inv(13.3, 14.0, t));
  const bloomGrow = inv(13.3, 15.0, t);
  const line = OUT(inv(14.0, 14.7, t));

  return (
    <Zone index={5} cam={cam} sharp>
      <AbsoluteFill style={{ alignItems: 'center', justifyContent: 'center' }}>
        <div
          style={{
            position: 'absolute',
            width: 1300,
            height: 520,
            background: `radial-gradient(closest-side, ${glow(0.34 * clamp01(bloomIn))} 0%, transparent 72%)`,
            transform: `translate(60px, 10px) scale(${lerp(0.9, 1.14, bloomGrow)})`,
          }}
        />

        {/* The last frame must still be moving, even if only just: a frozen end
            card is the one thing that makes a film feel like it stopped. */}
        <div
          style={{
            position: 'relative',
            textAlign: 'center',
            transform: `translateY(${-5 * inv(13.4, 15.0, t)}px) scale(${1 + 0.018 * inv(13.4, 15.0, t)})`,
          }}
        >
          <Display size={150} weight={500} style={{ letterSpacing: '-0.04em', lineHeight: 1.05 }}>
            <Word at={13.45} duration={0.9} style={{ paddingRight: '0.08em' }}>
              rindetu
              <Serif style={{ letterSpacing: '-0.02em' }}>plata</Serif>
            </Word>
          </Display>

          <FadeUp at={13.75} style={{ marginTop: 30 }}>
            <Mono size={22} style={{ letterSpacing: '0.18em' }}>
              ¿DÓNDE RINDE TU PLATA HOY?
            </Mono>
          </FadeUp>

          <div
            style={{
              margin: '40px auto 0',
              width: 440,
              height: 1,
              background: `linear-gradient(90deg, transparent, ${C.accent}, transparent)`,
              transform: `scaleX(${line})`,
              opacity: line,
            }}
          />
        </div>

        <div style={{ position: 'absolute', bottom: 96, left: 0, right: 0, textAlign: 'center' }}>
          <FadeUp at={14.2}>
            <Mono size={14}>Datos oficiales · Actualizado todos los días</Mono>
          </FadeUp>
        </div>
      </AbsoluteFill>
    </Zone>
  );
};
