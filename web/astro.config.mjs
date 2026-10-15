// Astro configuration for the rindetuplata static site (S14/S16).
// Config APIs verified against node_modules/astro@7.3.8 types:
//   - `server` is Astro's own ServerConfig (host, port, allowedHosts, headers, open),
//     so the dev-time /api proxy lives under `vite.server.proxy`.
//   - `output: 'static'` is still supported.
import { defineConfig } from 'astro/config';
import react from '@astrojs/react';
import sitemap from '@astrojs/sitemap';
import tailwindcss from '@tailwindcss/vite';
import { inSitemap } from './src/lib/sitemapPaths.js';

const site = process.env.SITE_URL ?? 'http://localhost:4321';
const apiProxyTarget = process.env.API_PROXY_TARGET ?? 'http://localhost:8080';

export default defineConfig({
  site,
  output: 'static',
  integrations: [react(), sitemap({ filter: inSitemap })],
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
