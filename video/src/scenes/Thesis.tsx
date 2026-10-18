/**
 * Beat 2 — thesis (2.35–5.45s).
 *
 * The whole product in one shape: a 30-day nominal return split into the part
 * inflation eats and the part that actually reaches you. The real-return sliver
 * is 19% of the bar, so the graphic proves the claim instead of illustrating it,
 * and the camera ends the beat pushed into that sliver.
 *
 * Real figures, from the recorded run (`web/fixtures/ranking-default.json`):
 * 24.00% TNA -> 1.9726% in 30 days, expected inflation 1.5860%, real 0.3806%.
 */
import { AbsoluteFill } from 'remotion';
import { Zone } from '../world/Stage';
import type { Camera } from '../lib/camera';
import { Display, Eyebrow, Label, Mono, Word, useBeatT } from '../world/Type';
import { C, FONT, gainGlow, ink } from '../theme';
import { OUT } from '../world/Type';
import { clamp01 } from '../lib/math';

const NOMINAL = 0.019726;
const INFLATION = 0.01586;
const REAL = NOMINAL - INFLATION;

const BAR_H = 470;
const BAR_W = 200;
const REAL_PX = (REAL / NOMINAL) * BAR_H;
const INFL_PX = (INFLATION / NOMINAL) * BAR_H;

export const Thesis: React.FC<{ cam: Camera }> = ({ cam }) => {
  const t = useBeatT();

  const grow = OUT(clamp01((t - 2.72) / 0.9));
  const lineIn = OUT(clamp01((t - 3.6) / 0.5));
  const sliverIn = OUT(clamp01((t - 3.98) / 0.55));
  const sliverLabel = OUT(clamp01((t - 4.18) / 0.55));
  const foot = OUT(clamp01((t - 4.42) / 0.6));

  return (
    <Zone index={1} cam={cam} sharp>
      <AbsoluteFill style={{ alignItems: 'center', justifyContent: 'center' }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 150 }}>
          {/* ---- Left: the claim ---- */}
          <div style={{ width: 880 }}>
            <Eyebrow at={2.5}>La tesis</Eyebrow>

            <div style={{ marginTop: 28 }}>
              <Display size={104} style={{ letterSpacing: '-0.035em' }}>
                <Word at={2.55}>La tasa</Word>
                <span style={{ display: 'block' }}>
                  <Word at={2.63} delay={0.02}>
                    no es
                  </Word>{' '}
                  <Word at={2.72} delay={0.04}>
                    la ganancia.
                  </Word>
                </span>
              </Display>
            </div>

            <div
              style={{
                marginTop: 44,
                display: 'flex',
                alignItems: 'baseline',
                gap: 16,
                opacity: foot,
                transform: `translateY(${(1 - foot) * 14}px)`,
              }}
            >
              <span style={{ fontFamily: FONT.mono, fontSize: 21, color: C.muted }}>24,00&#8239;% TNA</span>
              <span style={{ color: C.dim, fontSize: 19 }}>→</span>
              <span style={{ fontFamily: FONT.mono, fontSize: 21, color: C.muted }}>1,97&#8239;% en 30 días</span>
            </div>
          </div>

          {/* ---- Right: the split ---- */}
          <div style={{ position: 'relative', width: 560, height: 640 }}>
            <div
              style={{
                position: 'absolute',
                left: 0,
                bottom: 60,
                width: BAR_W,
                height: BAR_H,
                overflow: 'hidden',
                borderRadius: 3,
              }}
            >
              <div
                style={{
                  position: 'absolute',
                  left: 0,
                  bottom: 0,
                  width: BAR_W,
                  height: BAR_H * grow,
                  border: `1px solid ${ink(0.16)}`,
                  borderBottom: 'none',
                  borderRadius: '3px 3px 0 0',
                  background: `linear-gradient(180deg, ${ink(0.13)}, ${ink(0.07)})`,
                  overflow: 'hidden',
                }}
              >
                {/* Inflation: the part you never see. */}
                <div
                  style={{
                    position: 'absolute',
                    left: 0,
                    right: 0,
                    bottom: 0,
                    height: INFL_PX * grow,
                    background: `linear-gradient(180deg, ${ink(0.11)}, ${ink(0.03)})`,
                  }}
                />
                {/* The sliver that reaches you. */}
                <div
                  style={{
                    position: 'absolute',
                    left: 0,
                    right: 0,
                    top: 0,
                    height: REAL_PX * sliverIn,
                    background: `linear-gradient(180deg, ${gainGlow(0.98)}, ${gainGlow(0.55)})`,
                    boxShadow: `0 0 ${40 * sliverIn}px ${gainGlow(0.6 * sliverIn)}`,
                  }}
                />
              </div>
            </div>

            {/* Inflation tick line, running out into its label. */}
            <div
              style={{
                position: 'absolute',
                left: -30,
                bottom: 60 + INFL_PX * grow,
                width: 470,
                height: 1,
                background: `linear-gradient(90deg, ${ink(0.05)}, ${ink(0.55)} 22%, ${ink(0.16)} 100%)`,
                opacity: lineIn,
                transform: `scaleX(${lineIn})`,
                transformOrigin: 'left',
              }}
            />
            <div
              style={{
                position: 'absolute',
                left: BAR_W + 40,
                bottom: 60 + INFL_PX * grow - 44,
                opacity: lineIn,
                transform: `translateX(${(1 - lineIn) * -16}px)`,
              }}
            >
              <Mono size={23} style={{ color: C.muted }}>
                1,59&#8239;%
              </Mono>
              <div style={{ marginTop: 5 }}>
                <Label style={{ fontSize: 13, letterSpacing: '0.17em' }}>Inflación, 30 días</Label>
              </div>
              <div style={{ marginTop: 2 }}>
                <Label style={{ fontSize: 13, letterSpacing: '0.17em', color: C.dim }}>REM · BCRA</Label>
              </div>
            </div>

            {/* Real return label, above the sliver. */}
            <div
              style={{
                position: 'absolute',
                left: BAR_W + 40,
                bottom: 60 + BAR_H - 8,
                opacity: sliverLabel,
                transform: `translateY(${(1 - sliverLabel) * -12}px)`,
              }}
            >
              <div
                style={{
                  fontFamily: FONT.display,
                  fontSize: 52,
                  fontWeight: 700,
                  letterSpacing: '-0.025em',
                  color: C.gain,
                  fontVariantNumeric: 'tabular-nums',
                  lineHeight: 1,
                  textShadow: `0 0 ${28 * sliverLabel}px ${gainGlow(0.45 * sliverLabel)}`,
                }}
              >
                +0,38&#8239;%
              </div>
              <div style={{ marginTop: 8 }}>
                <Label style={{ fontSize: 13, letterSpacing: '0.17em', color: C.gain }}>
                  Lo que te queda
                </Label>
              </div>
            </div>

            {/* Baseline */}
            <div
              style={{
                position: 'absolute',
                left: -30,
                bottom: 60,
                width: 470,
                height: 1,
                background: `linear-gradient(90deg, ${ink(0.5)}, ${ink(0.1)})`,
                transform: `scaleX(${grow})`,
                transformOrigin: 'left',
              }}
            />
          </div>
        </div>
      </AbsoluteFill>
    </Zone>
  );
};
