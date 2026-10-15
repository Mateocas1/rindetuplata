/**
 * `robots.txt` body (T7e, S14).
 *
 * Built from the site URL Astro was configured with (`SITE_URL` in astro.config.mjs), so the sitemap
 * host follows the deployment instead of being hardcoded in a static file. The account pages carry
 * `noindex` (T7d): the same path list keeps them out of the sitemap and disallows them for the
 * crawler, so the two can never disagree.
 */
import { ACCOUNT_PATHS } from './sitemapPaths.js';

export function robotsTxt(site: URL): string {
  return [
    '# rindetuplata',
    '# Generated at build time from SITE_URL; @astrojs/sitemap writes the sitemap it points at.',
    'User-agent: *',
    'Allow: /',
    ...ACCOUNT_PATHS.map((path) => `Disallow: ${path}`),
    '',
    `Sitemap: ${new URL('sitemap-index.xml', site).href}`,
    '',
  ].join('\n');
}
