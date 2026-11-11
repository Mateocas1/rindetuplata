// Astro configuration for the rindetuplata static site.
// Config APIs verified against node_modules/astro@7.3.8 types:
//   - `server` is Astro's own ServerConfig (host, port, allowedHosts, headers, open),
//     so the dev-time /api proxy lives under `vite.server.proxy`.
//   - `output: 'static'` is still supported.
import { defineConfig } from 'astro/config';
import react from '@astrojs/react';
import sitemap from '@astrojs/sitemap';
import tailwindcss from '@tailwindcss/vite';
import { runBuildGate } from './src/lib/buildGate.ts';
import { inSitemap } from './src/lib/sitemapPaths.js';

const site = process.env.SITE_URL ?? 'http://localhost:4321';
const apiProxyTarget = process.env.API_PROXY_TARGET ?? 'http://localhost:8080';

// Freshness and minimum-data gate. `astro:build:start` runs before any page renders, so a stale,
// thin or category-less API answer fails the build (and every entrypoint: `build:api`, the rebuilder sidecar,
// a CDN's own `astro build`) while the previous site keeps being served.
const buildGate = {
  name: 'rindetuplata-build-gate',
  hooks: {
    'astro:build:start': async () => {
      await runBuildGate();
    },
  },
};

export default defineConfig({
  site,
  output: 'static',
  integrations: [buildGate, react(), sitemap({ filter: inSitemap })],
  vite: {
    plugins: [tailwindcss()],
    server: {
      proxy: {
        '/api': {
          target: apiProxyTarget,
          changeOrigin: true,
        },
      },
    },
  },
});
