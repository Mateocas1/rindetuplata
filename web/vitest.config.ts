/// <reference types="vitest/config" />
import { getViteConfig } from 'astro/config';

export default getViteConfig({
  test: {
    environment: 'node',
    include: ['src/**/*.test.ts', 'test/**/*.test.ts'],
    // The build smoke test shells out to `astro build`; give it room.
    testTimeout: 180_000,
    hookTimeout: 180_000,
  },
});
