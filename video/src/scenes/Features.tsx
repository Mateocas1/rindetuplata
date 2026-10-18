/**
 * Beat 4 — features (8.35–11.35s).
 *
 * Four product panels in one row; the camera trucks across them and the panel
 * nearest the lens is the only sharp one. The focus rack is computed per panel
 * from its on-screen offset (`panelX - cam.x`), so the lens decides what is in
 * focus instead of a hand-timed cue.
 */
import { AbsoluteFill } from 'remotion';
import { Zone } from '../world/Stage';
import type { Camera } from '../lib/camera';
import { Display, Label, Mono, OUT, useBeatT } from '../world/Type';
import { Card, Field, Row, Sparkline, Toggle } from '../ui/parts';
import { C, FONT, alpha } from '../theme';
import { clamp, clamp01, inv } from '../lib/math';

const W = 392;
const H = 430;
const GAP = 42;
const PAD = 22;
const INNER = W - PAD * 2; // 348, the Row width

const ENTER = [8.7, 8.82, 8.94, 9.06];

const Panel: React.FC<{
  index: number;
  cam: Camera;
  label: string;
  title: string;
  children: React.ReactNode;
}> = ({ index, cam, label, title, children }) => {
  const t = useBeatT();
  const enter = OUT(clamp01((t - ENTER[index]) / 0.8));

  // Screen offset of this panel's centre from the lens axis.
  const panelX = (index - 1.5) * (W + GAP);
  const dx = Math.abs(panelX - cam.x);
  const blur = clamp(dx / 520, 0, 1) * 5;
  const scale = 1 - clamp(dx / 1400, 0, 0.06);

  return (
    <div
      style={{
        opacity: enter * (1 - clamp(dx / 1400, 0, 1) * 0.14),
        transform: `translate3d(0, ${(1 - enter) * 46}px, 0) scale(${scale})`,
        filter: blur > 0.15 ? `blur(${blur}px)` : undefined,
      }}
    >
      <Card w={W} h={H}>
        <div
          style={{
            position: 'absolute',
            inset: 0,
            padding: PAD,
            display: 'flex',
            flexDirection: 'column',
          }}
        >
          <Label style={{ fontSize: 12, letterSpacing: '0.2em' }}>{label}</Label>
          <div style={{ flex: 1, display: 'flex', flexDirection: 'column', justifyContent: 'center' }}>
            {children}
          </div>
          <Display size={30} weight={500} style={{ lineHeight: 1.05, minHeight: 64, display: 'flex', alignItems: 'flex-end' }}>
            {title}
          </Display>
        </div>
      </Card>
    </div>
  );
};

export const Features: React.FC<{ cam: Camera }> = ({ cam }) => {
  const t = useBeatT();
  const spark = OUT(inv(9.4, 10.2, t));
  const toggle = OUT(inv(9.9, 10.15, t));
  const result = OUT(inv(9.3, 9.8, t));

  return (
    <Zone index={3} cam={cam} sharp>
      <AbsoluteFill style={{ alignItems: 'center', justifyContent: 'center' }}>
        <div style={{ display: 'flex', gap: GAP }}>
          {/* 1 — ranking */}
          <Panel index={0} cam={cam} label="RANKING DIARIO" title="Ordenado por pesos">
            <Row index="01" name="REBA" rate="24,00 %" fill={1} at={8.95} best width={INNER} />
            <Row index="02" name="Adcap Ahorro Dinámico" rate="23,58 %" fill={0.982} at={8.95} delay={0.08} width={INNER} />
            <Row index="03" name="Banco CMF" rate="23,50 %" fill={0.979} at={8.95} delay={0.16} width={INNER} />
            <Row index="04" name="Naranja X" rate="20,00 %" fill={0.833} at={8.95} delay={0.24} width={INNER} />
          </Panel>

          {/* 2 — simulator */}
          <Panel index={1} cam={cam} label="SIMULADOR" title="Tu monto, tu plazo">
            <div style={{ display: 'flex', gap: 12 }}>
              <Field label="MONTO" value="$1.000.000" w={168} accent />
              <Field label="PLAZO" value="30 días" w={168} />
            </div>
            <div
              style={{
                marginTop: 30,
                opacity: result,
                transform: `translateY(${(1 - result) * 12}px)`,
              }}
            >
              <Mono size={15} style={{ letterSpacing: '0.16em' }}>
                GANÁS
              </Mono>
              <div
                style={{
                  marginTop: 6,
                  fontFamily: FONT.display,
                  fontSize: 40,
                  fontWeight: 500,
                  letterSpacing: '-0.03em',
                  color: C.gain,
                  fontVariantNumeric: 'tabular-nums',
                  whiteSpace: 'nowrap',
                }}
              >
                + $19.726,03
              </div>
              <div style={{ marginTop: 6 }}>
                <Mono size={13}>en 30 días</Mono>
              </div>
            </div>
          </Panel>

          {/* 3 — history */}
          <Panel index={2} cam={cam} label="HISTÓRICO" title="Rinde contra inflación">
            <div style={{ paddingInline: 24 }}>
              <Sparkline w={300} h={120} progress={spark} />
            </div>
            <div style={{ marginTop: 26, paddingInline: 24, whiteSpace: 'pre' }}>
              <Mono size={13}>— rendimiento   --- inflación</Mono>
            </div>
          </Panel>

          {/* 4 — alerts */}
          <Panel index={3} cam={cam} label="ALERTAS" title="Avisame a tiempo">
            <div
              style={{
                border: `1px solid ${C.line}`,
                borderRadius: 3,
                background: alpha(C.text, 0.04),
                padding: '18px 16px',
              }}
            >
              <Mono size={16} style={{ color: C.text, lineHeight: 1.5, letterSpacing: '-0.01em' }}>
                Avisame si una billetera pasa 22&nbsp;%&nbsp;TNA
              </Mono>
            </div>
            <div style={{ marginTop: 22, display: 'flex', alignItems: 'center', gap: 16 }}>
              <Toggle on={toggle} />
              <Mono size={13}>Te llega por mail</Mono>
            </div>
          </Panel>
        </div>
      </AbsoluteFill>
    </Zone>
  );
};
