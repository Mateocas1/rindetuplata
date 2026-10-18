/**
 * Beat 1 — hook (0.00–2.35s).
 *
 * The product's own question, set in two lines: the display face asks, the
 * serif says "plata". Everything before it is the room moving.
 */
import { AbsoluteFill } from 'remotion';
import { Zone } from '../world/Stage';
import type { Camera } from '../lib/camera';
import { Display, FadeUp, OUT, TypeMorph, Word, WordReveal, useT } from '../world/Type';
import { C, glow } from '../theme';
import { clamp01, inv } from '../lib/math';

export const Hook: React.FC<{ cam: Camera }> = ({ cam }) => {
  const t = useT();
  const rule = OUT(inv(1.0, 1.85, t));
  const bloom = clamp01(inv(1.55, 2.3, t));

  return (
    <Zone index={0} cam={cam} sharp lead={0}>
      <AbsoluteFill style={{ alignItems: 'center', justifyContent: 'center' }}>
        {/* The accent's one job: a cobalt breath behind the word it belongs to. */}
        <div
          style={{
            position: 'absolute',
            width: 1100,
            height: 420,
            background: `radial-gradient(closest-side, ${glow(0.3 * bloom)} 0%, transparent 70%)`,
            transform: 'translate(120px, 40px)',
          }}
        />

        <div style={{ position: 'relative', textAlign: 'center' }}>
          <div style={{ marginBottom: 34 }}>
            <WordReveal
              text="COMPARADOR DE RENDIMIENTOS"
              at={0.5}
              stagger={0.03}
              duration={0.6}
              style={{ display: 'inline-block', fontFamily: "'Geist Mono Variable', monospace", fontSize: 17, letterSpacing: '0.28em', color: C.muted }}
            />
          </div>

          <Display size={152} style={{ letterSpacing: '-0.038em' }}>
            <span style={{ display: 'block' }}>
              <Word at={0.2} delay={0}>
                ¿Dónde
              </Word>{' '}
              <Word at={0.2} delay={0.085}>
                rinde
              </Word>
            </span>
            <span style={{ display: 'block' }}>
              <Word at={0.44} delay={0}>tu</Word>{' '}
              <Word at={0.44} delay={0.075} duration={0.78}>
                <TypeMorph text="plata" at={1.12} size={1} />
              </Word>{' '}
              <Word at={0.44} delay={0.15}>hoy?</Word>
            </span>
          </Display>

          <div
            style={{
              height: 1,
              width: 720 * rule,
              margin: '42px auto 0',
              background: `linear-gradient(90deg, transparent, ${C.accent}, transparent)`,
              opacity: 0.85,
            }}
          />

          <FadeUp at={1.15} distance={10} style={{ marginTop: 34 }}>
            <span
              style={{
                fontFamily: "'Geist Mono Variable', monospace",
                fontSize: 20,
                letterSpacing: '0.22em',
                textTransform: 'uppercase',
                color: C.muted,
              }}
            >
              TNA · TEA · inflación · retorno real
            </span>
          </FadeUp>
        </div>
      </AbsoluteFill>
    </Zone>
  );
};
