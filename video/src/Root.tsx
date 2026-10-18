import { Composition } from 'remotion';
import { Rindetuplata } from './Video';
import { DURATION, FPS } from './lib/camera';

export const RemotionRoot = () => (
  <Composition
    id="Rindetuplata"
    component={Rindetuplata}
    durationInFrames={DURATION * FPS}
    fps={FPS}
    width={1920}
    height={1080}
  />
);
