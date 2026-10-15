import { describe, expect, it } from 'vitest';
import { inSitemap } from './sitemapPaths.js';

/**
 * Sitemap filter (T7c, D2).
 *
 * Lives in a plain JavaScript module because `astro.config.mjs` imports it and
 * that file is loaded as JavaScript. Account pages are client-only and carry
 * `noindex` (T7d), so they must stay out of the sitemap.
 */
const SITE = 'https://rindetuplata.ar';

describe('inSitemap', () => {
  it('keeps the public pages, with and without the trailing slash', () => {
    for (const path of [
      '/',
      '/metodologia',
      '/metodologia/',
      '/plazo-fijo',
      '/plazo-fijo/',
      '/billeteras/',
      '/fondos/',
      '/cripto/',
      '/instrumentos/reba-compania-financiera-s-a-29',
      '/instrumentos/reba-compania-financiera-s-a-29/',
    ]) {
      expect(inSitemap(`${SITE}${path}`), path).toBe(true);
    }
  });

  it('drops the account pages that carry noindex', () => {
    for (const path of [
      '/ingresar',
      '/ingresar/',
      '/alertas',
      '/alertas/',
      '/admin',
      '/admin/',
      '/admin/tasas',
      '/admin/tasas/',
    ]) {
      expect(inSitemap(`${SITE}${path}`), path).toBe(false);
    }
  });

  it('only excludes the admin path itself, not every path that starts with it', () => {
    expect(inSitemap(`${SITE}/administracion`)).toBe(true);
    expect(inSitemap(`${SITE}/alertas-historicas`)).toBe(true);
  });
});
