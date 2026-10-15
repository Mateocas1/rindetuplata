/**
 * `robots.txt` as a build-time endpoint (T7e) instead of a static file in `public/`: in static output
 * the route is prerendered, and the body takes its host from `SITE_URL` (astro.config.mjs). There is
 * no localhost fallback: a build without `SITE_URL` fails here instead of publishing a sitemap URL
 * that points at the build machine. `npm run build:api` requires `SITE_URL` before it even gets this
 * far; the fixture build keeps the config's default and is local-only by design.
 */
import type { APIRoute } from 'astro';
import { robotsTxt } from '../lib/robots';

export const GET: APIRoute = ({ site }) => {
  if (site === undefined) {
    throw new Error(
      'robots.txt needs the site URL: set SITE_URL (npm run build:api requires it) so the sitemap host is not localhost.',
    );
  }
  const body = robotsTxt(site);
  return new Response(body, {
    headers: { 'content-type': 'text/plain; charset=utf-8' },
  });
};
