import { AbsoluteFill, useCurrentFrame, useVideoConfig } from 'remotion';
import { cameraAt } from './lib/camera';
import { Sky, World, Lens } from './world/World';
import { SpeedRush } from './world/SpeedRush';
import { Stage } from './world/Stage';
import { Hook } from './scenes/Hook';
import { Thesis } from './scenes/Thesis';
import { Benefits } from './scenes/Benefits';
import { Features } from './scenes/Features';
import { Positioning } from './scenes/Positioning';
import { Outro } from './scenes/Outro';
import { C } from './theme';
import { clamp01, smoothstep } from './lib/math';

export const Rindetuplata: React.FC = () => {
  const frame = useCurrentFrame();
  const { fps, durationInFrames } = useVideoConfig();
  const t = frame / fps;
  const cam = cameraAt(t);

  const fadeIn = 1 - smoothstep(0, 0.3, t);
  const fadeOut = smoothstep((durationInFrames - 1) / fps - 0.5, (durationInFrames - 1) / fps, t);

  return (
    <AbsoluteFill style={{ background: C.surfaceDeep }}>
      <Sky t={t} />
      <Stage cam={cam}>
        <World t={t} />
        <SpeedRush t={t} />
        <Hook cam={cam} />
        <Thesis cam={cam} />
        <Benefits cam={cam} />
        <Features cam={cam} />
        <Positioning cam={cam} />
        <Outro cam={cam} />
      </Stage>
      <Lens t={t} />
      <AbsoluteFill style={{ background: '#000', opacity: clamp01(fadeIn + fadeOut) }} />
    </AbsoluteFill>
  );
};
