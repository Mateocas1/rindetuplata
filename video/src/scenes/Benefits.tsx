/**
 * Beat 3 — benefits (5.45–8.35s).
 *
 * The product's own frosted plate, large: the best option of the day, the money
 * it earns, and the three numbers that justify it. The figure counts up from the
 * amount you put in to the amount you get back, then pulses green on landing.
 *
 * Real figures, from the recorded run (`web/fixtures/ranking-default.json`):
 * REBA, plazo fijo, 24,00 % TNA, 30 days on $1.000.000, data of 14/10/2026.
 */
import { AbsoluteFill } from 'remotion';
import { Zone } from '../world/Stage';
import type { Camera } from '../lib/camera';
import { FadeUp, Label, Mono, OUT, useBeatT } from '../world/Type';
import { Plate } from '../ui/parts';
import { C, FONT, gainGlow, glow } from '../theme';
import { clamp01, inv, lerp, money } from '../lib/math';

const FROM = 1_000_000;
const TO = 1_019_726.03;

/** The site prints the figure with the non-breaking gap after "$". */
const figure = (v: number) => money(v);

const Column: React.FC<{ label: string; value: string; at: number; gain?: boolean }> = ({
  label,
  value,
  at,
  gain,
}) => (
  <FadeUp at={at} distance={14} style={{ flex: 1 }}>
    <Label style={{ fontSize: 12, letterSpacing: '0.2em', color: C.dim }}>{label}</Label>
    <div style={{ marginTop: 10 }}>
      <Mono size={22} style={{ color: gain ? C.gain : C.text }}>
        {value}
      </Mono>
    </div>
  </FadeUp>
);

export const Benefits: React.FC<{ cam: Camera }> = ({ cam }) => {
  const t = useBeatT();
  const count = OUT(inv(5.8, 6.7, t));
  const value = lerp(FROM, TO, count);

  const settle = OUT(clamp01((t - 5.55) / 0.95));
  const lift = OUT(inv(5.55, 6.3, t));
  const pulse = Math.sin(Math.PI * inv(6.7, 7.4, t));

  return (
    <Zone index={2} cam={cam} sharp>
      <AbsoluteFill style={{ alignItems: 'center', justifyContent: 'center' }}>
        {/* Cobalt breath behind the glass, so the plate has something to refract. */}
        <div
          style={{
            position: 'absolute',
            width: 1500,
            height: 900,
            background: `radial-gradient(closest-side, ${glow(0.2 * lift)} 0%, transparent 72%)`,
          }}
        />

        <Plate
          w={1090}
          h={680}
          style={{
            opacity: clamp01(settle * 1.6),
            transform: `translate3d(0, ${(1 - settle) * 18}px, 0) scale(${lerp(0.94, 1, settle)})`,
          }}
        >
          <div
            style={{
              position: 'absolute',
              inset: 0,
              padding: '60px 68px',
              display: 'flex',
              flexDirection: 'column',
              justifyContent: 'center',
            }}
          >
            <div>
              <FadeUp at={5.65} distance={10}>
                <Label style={{ fontSize: 17, letterSpacing: '0.22em' }}>La mejor de hoy · Plazo fijo</Label>
              </FadeUp>

              <div
                style={{
                  marginTop: 30,
                  fontFamily: FONT.display,
                  fontSize: 118,
                  fontWeight: 700,
                  lineHeight: 1,
                  letterSpacing: '-0.035em',
                  color: C.text,
                  fontVariantNumeric: 'tabular-nums',
                  whiteSpace: 'nowrap',
                  opacity: OUT(inv(5.62, 6.1, t)),
                  textShadow: `0 0 ${46 * pulse}px ${gainGlow(0.75 * pulse)}, 0 0 ${14 * pulse}px ${gainGlow(0.5 * pulse)}`,
                }}
              >
                {figure(value)}
              </div>

              <FadeUp at={6.1} distance={12} style={{ marginTop: 26 }}>
                <span style={{ fontFamily: FONT.sans, fontSize: 26, color: C.text }}>
                  REBA COMPAÑÍA FINANCIERA S.A.
                </span>
              </FadeUp>
              <FadeUp at={6.2} distance={12} style={{ marginTop: 10 }}>
                <Mono size={18} style={{ color: C.muted }}>
                  en 30 días con $1.000.000
                </Mono>
              </FadeUp>
            </div>

            <div style={{ marginTop: 64 }}>
              <div
                style={{
                  height: 1,
                  background: `linear-gradient(90deg, ${C.lineStrong}, ${C.line})`,
                  transformOrigin: 'left',
                  transform: `scaleX(${OUT(inv(6.3, 6.9, t))})`,
                }}
              />
              <div style={{ display: 'flex', gap: 40, marginTop: 30 }}>
                <Column label="TNA" value="24,00 %" at={6.5} />
                <Column label="RETORNO REAL" value="+0,38 %" at={6.62} gain />
                <Column label="INFLACIÓN REM" value="1,59 %" at={6.74} />
              </div>
              <FadeUp at={7.1} distance={8} style={{ marginTop: 34 }}>
                <Mono size={13} style={{ color: C.dim }}>
                  ArgentinaDatos · dato del 14/10/2026
                </Mono>
              </FadeUp>
            </div>
          </div>
        </Plate>
      </AbsoluteFill>
    </Zone>
  );
};
