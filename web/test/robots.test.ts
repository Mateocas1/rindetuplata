import { describe, expect, it } from 'vitest';
import { robotsTxt } from '../src/lib/robots';
import { ACCOUNT_PATHS } from '../src/lib/sitemapPaths.js';
import { GET } from '../src/pages/robots.txt';

/**
 * `robots.txt` (T7e, finding R3).
 *
 * The account pages are `noindex` (T7d) and stay out of the sitemap, but they
 * must stay crawlable: a `Disallow` would stop the crawler before it could read
 * the `noindex`, and the page would stay in the index. The host comes from
 * `SITE_URL`; there is no localhost fallback that could publish a sitemap URL
 * pointing at the build machine.
 */
const SITE = new URL('https://rindetuplata.ar');

describe('robotsTxt', () => {
  it('points at the sitemap of the site it was given', () => {
    expect(robotsTxt(SITE)).toContain('Sitemap: https://rindetuplata.ar/sitemap-index.xml');
  });

  it('allows every path, because the account pages carry noindex', () => {
    const body = robotsTxt(SITE);
    expect(body).toContain('Allow: /');
    expect(body).not.toContain('Disallow');
  });

  it('does not disallow the account paths', () => {
    const body = robotsTxt(SITE);
    for (const path of ACCOUNT_PATHS) {
      expect(body, `${path} must stay crawlable`).not.toContain(`Disallow: ${path}`);
    }
  });
});

describe('robots.txt endpoint', () => {
  it('refuses to answer without a site URL instead of falling back to localhost', () => {
    expect(() => GET({ site: undefined } as never)).toThrow(/SITE_URL/);
  });

  it('serves the body with the configured host', async () => {
    const response = await GET({ site: SITE } as never);
    expect(response.headers.get('content-type')).toBe('text/plain; charset=utf-8');
    expect(await response.text()).toContain('Sitemap: https://rindetuplata.ar/sitemap-index.xml');
  });
});
