/// <reference types="vitest/config" />
import { getViteConfig } from 'astro/config';
import { configDefaults } from 'vitest/config';

export default getViteConfig({
  test: {
    environment: 'node',
    include: ['src/**/*.test.ts', 'test/**/*.test.ts'],
    // e2e/ is the Playwright smoke (T7e): a different runner and a real stack, never collected here.
    exclude: [...configDefaults.exclude, 'e2e/**'],
    // Two smoke tests shell out to `astro build` and both write `dist`; run the
    // files one at a time so the two builds never share the output directory.
    fileParallelism: false,
    // The build smoke tests shell out to `astro build`; give them room.
    testTimeout: 180_000,
    hookTimeout: 180_000,
  },
});
