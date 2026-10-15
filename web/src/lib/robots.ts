/**
 * `robots.txt` body (T7e, S14).
 *
 * Built from the site URL Astro was configured with (`SITE_URL` in astro.config.mjs), so the sitemap
 * host follows the deployment instead of being hardcoded in a static file. The account pages carry
 * `noindex` (T7d) and stay out of the sitemap (`sitemapPaths.js`), but `robots.txt` allows them: a
 * `Disallow` would stop the crawler before it could read that `noindex`, and the page would stay in
 * the index instead of dropping out of it.
 */
export function robotsTxt(site: URL): string {
  return [
    '# rindetuplata',
    '# Generated at build time from SITE_URL; @astrojs/sitemap writes the sitemap it points at.',
    'User-agent: *',
    'Allow: /',
    '',
    `Sitemap: ${new URL('sitemap-index.xml', site).href}`,
    '',
  ].join('\n');
}
