/**
 * Sitemap filter (T7c, D2).
 *
 * Plain JavaScript on purpose: `astro.config.mjs` imports this module and that
 * file is loaded as JavaScript, so a TypeScript annotation here would break the
 * config load. The filter receives absolute page URLs from @astrojs/sitemap.
 *
 * Account pages are client-only and carry `noindex` (T7d): they never belong in
 * the sitemap, and only the exact paths are dropped.
 */

const EXCLUDED = new Set(['/ingresar', '/alertas', '/admin']);

/**
 * @param {string} page Absolute page URL.
 * @returns {boolean} True when the page belongs in the sitemap.
 */
export function inSitemap(page) {
  const pathname = new URL(page).pathname.replace(/\/+$/, '');
  return !EXCLUDED.has(pathname) && !pathname.startsWith('/admin/');
}
