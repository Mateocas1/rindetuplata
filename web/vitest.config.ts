/// <reference types="vitest/config" />
import { getViteConfig } from 'astro/config';

export default getViteConfig({
  test: {
    environment: 'node',
    include: ['src/**/*.test.ts', 'test/**/*.test.ts'],
    // Two smoke tests shell out to `astro build` and both write `dist`; run the
    // files one at a time so the two builds never share the output directory.
    fileParallelism: false,
    // The build smoke tests shell out to `astro build`; give them room.
    testTimeout: 180_000,
    hookTimeout: 180_000,
  },
});
