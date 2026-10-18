/**
 * Typography primitives. Every reveal in the film is one of these, so the whole
 * piece shares one motion grammar instead of six improvised ones.
 */
import { createContext, useContext, type CSSProperties, type ReactNode } from 'react';
import { useCurrentFrame, useVideoConfig } from 'remotion';
import { bezier, clamp01, inv, lerp } from '../lib/math';
import { C, FONT } from '../theme';

/** Expo-out: the curve that makes a reveal land rather than drift. */
export const OUT = bezier(0.16, 1, 0.3, 1);
export const IN_OUT = bezier(0.76, 0, 0.24, 1);
export const LINEAR_SOFT = bezier(0.33, 1, 0.68, 1);

export const useT = () => {
  const frame = useCurrentFrame();
  const { fps } = useVideoConfig();
  return frame / fps;
};

/**
 * A beat's own clock, supplied by `Zone`.
 *
 * A composition has to start resolving before the camera lands, so every beat
 * runs ahead of the wall clock. Scenes read it through `useSceneT`, which is
 * also what the reveal primitives use: shifting only a scene's local math would
 * leave its words arriving late, which is precisely the gap this film cannot
 * afford.
 */
export const SceneLead = createContext(0);
export const useSceneT = () => useT() + useContext(SceneLead);

/**
 * One line, masked, rising into place from behind its own baseline. Returns as a
 * block so the caller can stack lines and stagger `delay` by a beat.
 */
export const MaskLine: React.FC<{
  children: ReactNode;
  /** Seconds from the start of the film. */
  at: number;
  delay?: number;
  duration?: number;
  style?: CSSProperties;
  /** 0 = rises from its own height, 0.6 = gentler lift. */
  travel?: number;
}> = ({ children, at, delay = 0, duration = 0.72, style, travel = 1 }) => {
  const t = useSceneT();
  const p = OUT(clamp01((t - at - delay) / duration));
  const y = (1 - p) * 108 * travel;
  const blur = (1 - p) * 7;

  return (
    <span
      style={{
        display: 'block',
        overflow: 'hidden',
        // Descenders and the italic serif must not be clipped by the mask.
        paddingBottom: '0.16em',
        marginBottom: '-0.16em',
        ...style,
      }}
    >
      <span
        style={{
          display: 'block',
          transform: `translate3d(0, ${y}%, 0)`,
          opacity: clamp01(p * 1.4),
          filter: blur > 0.1 ? `blur(${blur}px)` : undefined,
          willChange: 'transform',
        }}
      >
        {children}
      </span>
    </span>
  );
};

/** Plain fade + rise, for supporting copy that should not compete. */
export const FadeUp: React.FC<{
  children: ReactNode;
  at: number;
  delay?: number;
  duration?: number;
  distance?: number;
  style?: CSSProperties;
}> = ({ children, at, delay = 0, duration = 0.8, distance = 18, style }) => {
  const t = useSceneT();
  const p = OUT(clamp01((t - at - delay) / duration));
  return (
    <span
      style={{
        display: 'block',
        opacity: p,
        transform: `translate3d(0, ${(1 - p) * distance}px, 0)`,
        ...style,
      }}
    >
      {children}
    </span>
  );
};

/** The site's mono caps label. */
export const Label: React.FC<{ children: ReactNode; style?: CSSProperties }> = ({ children, style }) => (
  <span
    style={{
      fontFamily: FONT.mono,
      fontSize: 15,
      letterSpacing: '0.16em',
      textTransform: 'uppercase',
      color: C.muted,
      fontVariantNumeric: 'tabular-nums',
      ...style,
    }}
  >
    {children}
  </span>
);

/** Space Grotesk display. */
export const Display: React.FC<{
  children: ReactNode;
  size?: number;
  weight?: number;
  style?: CSSProperties;
}> = ({ children, size = 150, weight = 500, style }) => (
  <span
    style={{
      fontFamily: FONT.display,
      fontSize: size,
      fontWeight: weight,
      lineHeight: 0.95,
      letterSpacing: '-0.032em',
      color: C.text,
      display: 'block',
      ...style,
    }}
  >
    {children}
  </span>
);

/** The one serif accent per composition, in cobalt: the brand's signature word. */
export const Serif: React.FC<{ children: ReactNode; style?: CSSProperties }> = ({ children, style }) => (
  <span
    style={{
      fontFamily: FONT.serif,
      fontStyle: 'italic',
      fontWeight: 400,
      fontSize: '1.02em',
      letterSpacing: '0.005em',
      color: C.accent,
      ...style,
    }}
  >
    {children}
  </span>
);

/** Mono, tabular: every figure in this film is data and must not jitter. */
export const Mono: React.FC<{ children: ReactNode; size?: number; style?: CSSProperties }> = ({
  children,
  size = 24,
  style,
}) => (
  <span
    style={{
      fontFamily: FONT.mono,
      fontSize: size,
      fontVariantNumeric: 'tabular-nums',
      letterSpacing: '-0.01em',
      color: C.muted,
      ...style,
    }}
  >
    {children}
  </span>
);

/** A number rolling between two values, driven by seconds. */
export const useCount = (from: number, to: number, at: number, duration: number) => {
  const t = useSceneT();
  const p = OUT(inv(at, at + duration, t));
  return { value: lerp(from, to, p), p };
};

/**
 * Word-by-word version of MaskLine. A line that arrives as one block reads as a
 * slide; a line that arrives word by word reads as a title being set.
 */
export const WordReveal: React.FC<{
  text: string;
  at: number;
  stagger?: number;
  duration?: number;
  style?: CSSProperties;
  accent?: number;
}> = ({ text, at, stagger = 0.055, duration = 0.68, style, accent }) => {
  const t = useSceneT();
  const words = text.split(' ');
  return (
    <span
      style={{
        display: 'block',
        overflow: 'hidden',
        paddingBottom: '0.18em',
        marginBottom: '-0.18em',
        ...style,
      }}
    >
      {words.map((w, i) => {
        const p = OUT(clamp01((t - at - i * stagger) / duration));
        return (
          <span
            key={`${w}-${i}`}
            style={{
              display: 'inline-block',
              transform: `translate3d(0, ${(1 - p) * 104}%, 0)`,
              opacity: clamp01(p * 1.5),
              filter: p < 0.98 ? `blur(${(1 - p) * 8}px)` : undefined,
              color: accent === i ? C.accentSoft : undefined,
            }}
          >
            {w}
            {i < words.length - 1 ? '\u00A0' : ''}
          </span>
        );
      })}
    </span>
  );
};

/**
 * A single word with its own timing, so a line can be composed from words with
 * different faces — the brand sets "plata" in the serif inside a display line
 * and the reveal has to follow that, not flatten it.
 */
export const Word: React.FC<{
  children: ReactNode;
  at: number;
  delay?: number;
  duration?: number;
  style?: CSSProperties;
  inline?: boolean;
}> = ({ children, at, delay = 0, duration = 0.7, style, inline = true }) => {
  const t = useSceneT();
  const p = OUT(clamp01((t - at - delay) / duration));
  return (
    <span
      style={{
        display: inline ? 'inline-block' : 'block',
        overflow: 'hidden',
        paddingBottom: '0.18em',
        marginBottom: '-0.18em',
        verticalAlign: 'top',
      }}
    >
      <span
        style={{
          display: 'inline-block',
          transform: `translate3d(0, ${(1 - p) * 106}%, 0)`,
          opacity: clamp01(p * 1.5),
          filter: p < 0.985 ? `blur(${(1 - p) * 9}px)` : undefined,
          ...style,
        }}
      >
        {children}
      </span>
    </span>
  );
};

/**
 * The type morph: the same word changes face in place. Used once, on "plata",
 * where the display face hands over to the serif — a word becoming itself.
 */
export const TypeMorph: React.FC<{
  text: string;
  at: number;
  duration?: number;
  size?: number;
  displayStyle?: CSSProperties;
  serifStyle?: CSSProperties;
}> = ({ text, at, duration = 0.55, size = 1, displayStyle, serifStyle }) => {
  const t = useSceneT();
  const p = IN_OUT(clamp01((t - at) / duration));
  return (
    <span style={{ position: 'relative', display: 'inline-block' }}>
      <span
        style={{
          display: 'inline-block',
          opacity: 1 - p,
          filter: p > 0.02 ? `blur(${p * 6}px)` : undefined,
          transform: `scale(${1 + p * 0.045})`,
          ...displayStyle,
        }}
      >
        {text}
      </span>
      <span
        style={{
          position: 'absolute',
          inset: 0,
          display: 'inline-block',
          opacity: p,
          filter: p < 0.98 ? `blur(${(1 - p) * 6}px)` : undefined,
          transform: `scale(${1.045 - p * 0.045})`,
          fontFamily: FONT.serif,
          fontStyle: 'italic',
          fontWeight: 400,
          fontSize: `${size * 1.03}em`,
          letterSpacing: '0.004em',
          color: C.accent,
          ...serifStyle,
        }}
      >
        {text}
      </span>
    </span>
  );
};

/** Eyebrow: a thin cobalt tick, then mono caps. The film's way of naming a beat. */
export const Eyebrow: React.FC<{ children: ReactNode; at: number; style?: CSSProperties }> = ({
  children,
  at,
  style,
}) => {
  const t = useSceneT();
  const p = OUT(clamp01((t - at) / 0.6));
  return (
    <div style={{ display: 'flex', alignItems: 'center', gap: 14, ...style }}>
      <div
        style={{
          width: 34 * p,
          height: 1,
          background: C.accent,
          opacity: 0.9,
          transformOrigin: 'left center',
        }}
      />
      <span
        style={{
          fontFamily: FONT.mono,
          fontSize: 15,
          letterSpacing: '0.24em',
          textTransform: 'uppercase',
          color: C.muted,
          opacity: p,
          transform: `translateX(${(1 - p) * 10}px)`,
        }}
      >
        {children}
      </span>
    </div>
  );
};

/**
 * A beat's own clock. Content starts LEAD seconds before the camera lands, so
 * the next composition is already resolving in the distance while the previous
 * one leaves: the arrival is a settle, never a reveal from nothing.
 */
export const BEAT_LEAD = 0.45;
export const useBeatT = (lead = BEAT_LEAD) => useT() + lead;
