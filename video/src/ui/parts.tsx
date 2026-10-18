/**
 * The product's own components, rebuilt for the film: the site's nearly-square
 * panel, its frosted plate with the cobalt rail, its mono table row, its toggle.
 * A showreel for this product has to contain the product.
 */
import type { CSSProperties, ReactNode } from 'react';
import { C, FONT, alpha } from '../theme';
import { Label, Mono, useSceneT } from '../world/Type';
import { OUT } from '../world/Type';
import { clamp01 } from '../lib/math';

/** The raised surface, plus the dark-mode inner highlight from the token layer. */
export const Card: React.FC<{ children: ReactNode; style?: CSSProperties; w: number; h: number }> = ({
  children,
  style,
  w,
  h,
}) => (
  <div
    style={{
      width: w,
      height: h,
      position: 'relative',
      background: `linear-gradient(180deg, ${C.raised}, oklch(0.19 0.02 271))`,
      border: `1px solid ${C.line}`,
      borderRadius: 3,
      boxShadow: `inset 0 1px 0 ${alpha(C.text, 0.06)}`,
      overflow: 'hidden',
      ...style,
    }}
  >
    {children}
  </div>
);

/**
 * The one frosted plate of the film (the site allows one per page). No
 * backdrop-filter: the render must not depend on a compositor's mood, so the
 * glass is a fill plus a hairline plus the cobalt glow behind it.
 */
export const Plate: React.FC<{ children: ReactNode; w: number; h: number; style?: CSSProperties }> = ({
  children,
  w,
  h,
  style,
}) => (
  <div
    style={{
      width: w,
      height: h,
      position: 'relative',
      borderRadius: 3,
      border: `1px solid ${alpha(C.text, 0.13)}`,
      borderInlineStart: `2px solid ${C.accent}`,
      background: `linear-gradient(152deg, ${alpha(C.text, 0.09)}, ${alpha(C.text, 0.035)} 42%, ${alpha(C.accentDeep, 0.1)} 100%)`,
      boxShadow: `inset 0 1px 0 ${alpha(C.text, 0.12)}, 0 40px 120px -40px ${alpha(C.accentDeep, 0.35)}`,
      ...style,
    }}
  >
    {children}
  </div>
);

/** A mono table row, the ranking's unit of information. */
export const Row: React.FC<{
  index: string;
  name: string;
  rate: string;
  /** 0–1, the bar that compares this row with the leader. */
  fill: number;
  at: number;
  delay?: number;
  best?: boolean;
  width: number;
}> = ({ index, name, rate, fill, at, delay = 0, best, width }) => {
  const t = useSceneT();
  const p = OUT(clamp01((t - at - delay) / 0.5));
  return (
    <div
      style={{
        display: 'flex',
        alignItems: 'center',
        gap: 14,
        height: 46,
        paddingInline: 12,
        width,
        opacity: p,
        transform: `translateY(${(1 - p) * 12}px)`,
        background: best ? alpha(C.accent, 0.1) : 'transparent',
        borderInlineStart: best ? `2px solid ${C.accent}` : '2px solid transparent',
      }}
    >
      <span style={{ fontFamily: FONT.mono, fontSize: 13, color: C.dim, width: 22 }}>{index}</span>
      <span
        style={{
          fontFamily: FONT.sans,
          fontSize: 15,
          color: best ? C.text : C.muted,
          fontWeight: best ? 500 : 400,
          flex: 1,
          whiteSpace: 'nowrap',
          overflow: 'hidden',
          textOverflow: 'ellipsis',
        }}
      >
        {name}
      </span>
      <div style={{ width: 92, height: 3, background: alpha(C.text, 0.1), position: 'relative' }}>
        <div
          style={{
            position: 'absolute',
            inset: 0,
            width: `${fill * 100}%`,
            background: best ? C.accent : C.dim,
            transformOrigin: 'left',
            transform: `scaleX(${p})`,
          }}
        />
      </div>
      <span
        style={{
          fontFamily: FONT.mono,
          fontSize: 14,
          color: best ? C.text : C.muted,
          fontVariantNumeric: 'tabular-nums',
          width: 76,
          textAlign: 'right',
        }}
      >
        {rate}
      </span>
    </div>
  );
};

/** A form field, filled in: the simulator's input is part of the story. */
export const Field: React.FC<{ label: string; value: string; w: number; accent?: boolean }> = ({
  label,
  value,
  w,
  accent,
}) => (
  <div style={{ width: w }}>
    <Label style={{ fontSize: 11, letterSpacing: '0.2em' }}>{label}</Label>
    <div
      style={{
        marginTop: 8,
        height: 46,
        border: `1px solid ${accent ? C.accent : C.line}`,
        borderRadius: 3,
        background: alpha(C.text, 0.04),
        display: 'flex',
        alignItems: 'center',
        paddingInline: 12,
        fontFamily: FONT.mono,
        fontSize: 18,
        color: C.text,
        fontVariantNumeric: 'tabular-nums',
      }}
    >
      {value}
    </div>
  </div>
);

/** The alert rule: a sentence, a threshold, a switch. */
export const Toggle: React.FC<{ on: number }> = ({ on }) => (
  <div
    style={{
      width: 52,
      height: 28,
      borderRadius: 9999,
      background: on > 0.5 ? C.accent : alpha(C.text, 0.14),
      position: 'relative',
      transition: 'none',
    }}
  >
    <div
      style={{
        position: 'absolute',
        top: 3,
        left: 3 + on * 24,
        width: 22,
        height: 22,
        borderRadius: '50%',
        background: on > 0.5 ? C.surface : C.muted,
      }}
    />
  </div>
);

/** The history line: yield against inflation, drawn on. */
export const Sparkline: React.FC<{
  w: number;
  h: number;
  progress: number;
  accent?: string;
}> = ({ w, h, progress, accent = C.accent }) => {
  const pts = [0.18, 0.3, 0.26, 0.42, 0.38, 0.55, 0.5, 0.66, 0.62, 0.78, 0.74, 0.9];
  const step = w / (pts.length - 1);
  const d = pts
    .map((v, i) => `${i === 0 ? 'M' : 'L'} ${(i * step).toFixed(1)} ${(h - v * h).toFixed(1)}`)
    .join(' ');
  const flat = `M 0 ${h - 0.34 * h} L ${w} ${h - 0.34 * h}`;

  return (
    <svg width={w} height={h} style={{ overflow: 'visible' }}>
      <defs>
        <linearGradient id="spark" x1="0" y1="0" x2="0" y2="1">
          <stop offset="0%" stopColor={accent} stopOpacity={0.32} />
          <stop offset="100%" stopColor={accent} stopOpacity={0} />
        </linearGradient>
      </defs>
      <path d={`${d} L ${w} ${h} L 0 ${h} Z`} fill="url(#spark)" opacity={progress} />
      <path
        d={flat}
        stroke={alpha(C.muted, 0.42)}
        strokeWidth={1}
        strokeDasharray="4 4"
        fill="none"
        opacity={progress}
      />
      <path
        d={d}
        stroke={accent}
        strokeWidth={2.5}
        fill="none"
        strokeLinecap="round"
        strokeDasharray={600}
        strokeDashoffset={600 * (1 - progress)}
      />
    </svg>
  );
};

/** Mono source chip: the product's credibility, on screen. */
export const Chip: React.FC<{ children: ReactNode; at: number; delay?: number }> = ({
  children,
  at,
  delay = 0,
}) => {
  const t = useSceneT();
  const p = OUT(clamp01((t - at - delay) / 0.5));
  return (
    <div
      style={{
        border: `1px solid ${C.line}`,
        borderRadius: 3,
        paddingInline: 14,
        paddingBlock: 8,
        fontFamily: FONT.mono,
        fontSize: 13,
        letterSpacing: '0.14em',
        textTransform: 'uppercase',
        color: C.muted,
        opacity: p,
        transform: `translateY(${(1 - p) * 10}px)`,
      }}
    >
      {children}
    </div>
  );
};
