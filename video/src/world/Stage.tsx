/**
 * The stage: one 3D room, one camera, and beats that are places in it.
 *
 * A beat is never "shown"; it is positioned at a depth and the camera happens to
 * be there. Its neighbours are already faintly present behind and ahead of it,
 * which is what removes the cut: you can always see where you came from.
 */
import type { ReactNode } from 'react';
import { AbsoluteFill } from 'remotion';
import { BEAT_LEAD, SceneLead } from './Type';
import {
  PERSPECTIVE,
  depthOf,
  zoneBlur,
  zoneExitDrift,
  zoneVisibility,
  type Camera,
} from '../lib/camera';

export const Stage: React.FC<{ cam: Camera; children: ReactNode }> = ({ cam, children }) => (
  <AbsoluteFill style={{ perspective: PERSPECTIVE, perspectiveOrigin: '50% 50%', overflow: 'hidden' }}>
    <AbsoluteFill
      style={{
        transformStyle: 'preserve-3d',
        // Lens zoom, then the camera's own look rotation about the eye, then the
        // eye's screen offset. Read right to left: rotate the room, then place it.
        transform: `scale(${cam.zoom}) translate3d(${-cam.x}px, ${-cam.y}px, 0) rotateX(${cam.rx}deg) rotateY(${cam.ry}deg)`,
      }}
    >
      {children}
    </AbsoluteFill>
  </AbsoluteFill>
);

/** Smooth ramp, used to keep the exit from looking linear. */
const ramp = (v: number) => Math.min(1, Math.max(0, v));

export const Zone: React.FC<{
  index: number;
  cam: Camera;
  children: ReactNode;
  /** Extra world offset of the whole beat, for beats that sit off-axis. */
  x?: number;
  y?: number;
  /** Depth-of-field override; 0 keeps the beat perfectly sharp. */
  sharp?: boolean;
  /** How far ahead of the wall clock this beat's copy runs. */
  lead?: number;
}> = ({ index, cam, children, x = 0, y = 0, sharp = false, lead = BEAT_LEAD }) => {
  const depth = depthOf(index, cam.z);

  /*
   * A beat leaves by moving, not by fading.
   *
   * Two full-screen type compositions dissolved at the same scale read as a
   * mistake; the eye needs one of them to be going somewhere. So the outgoing
   * beat accelerates towards the lens and falls out of frame while the incoming
   * one arrives undersized and grows into place. Overlap is then depth, not a
   * double exposure.
   */
  const exitP = ramp((depth - 80) / 380);
  const exitE = Math.pow(exitP, 1.6);
  const entryP = ramp((depth + 760) / 420);
  const visible = Math.min(entryP, 1 - ramp((exitP - 0.55) / 0.45));
  if (visible <= 0.003) return null;

  const scale = (1 + 1.5 * exitE) * (0.8 + 0.2 * entryP);
  const drop = y + zoneExitDrift(depth) + 760 * exitE;
  // Leaving blur is speed; arriving blur is a focus pull.
  const blur = (sharp ? 0 : zoneBlur(depth)) + 5.5 * exitP + 3.6 * (1 - entryP);

  return (
    <AbsoluteFill
      style={{
        transform: `translate3d(${x}px, ${drop}px, ${depth}px) scale(${scale})`,
        transformStyle: 'preserve-3d',
        opacity: visible,
        filter: blur > 0.15 ? `blur(${blur}px)` : undefined,
      }}
    >
      <AbsoluteFill style={{ display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
        <SceneLead.Provider value={lead}>{children}</SceneLead.Provider>
      </AbsoluteFill>
    </AbsoluteFill>
  );
};
