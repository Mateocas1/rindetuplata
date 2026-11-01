/**
 * `llms.txt` as a build-time endpoint (T6e, S6, L32 c), like `robots.txt`: prerendered in static output, with the
 * site host from `SITE_URL` (astro.config.mjs) and the API host from `PUBLIC_API_URL`, which is the site origin when
 * the stack is one origin. There is no localhost fallback: a build without `SITE_URL` fails here instead of
 * publishing links that point at the build machine.
 */
import type { APIRoute } from 'astro';
import { llmsTxt } from '../lib/llms';

export const GET: APIRoute = ({ site }) => {
  if (site === undefined) {
    throw new Error(
      'llms.txt needs the site URL: set SITE_URL (npm run build:api requires it) so the links are not localhost.',
    );
  }
  const apiUrl = import.meta.env.PUBLIC_API_URL;
  const body = llmsTxt(site, apiUrl ? new URL(apiUrl) : site);
  return new Response(body, {
    headers: { 'content-type': 'text/plain; charset=utf-8' },
  });
};
