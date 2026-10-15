/**
 * `robots.txt` as a build-time endpoint (T7e) instead of a static file in `public/`: in static output
 * the route is prerendered, and the body takes its host from `SITE_URL` (astro.config.mjs) and its
 * account disallows from the sitemap filter's own path list.
 */
import type { APIRoute } from 'astro';
import { robotsTxt } from '../lib/robots';

export const GET: APIRoute = ({ site }) => {
  const body = robotsTxt(site ?? new URL('http://localhost:4321'));
  return new Response(body, {
    headers: { 'content-type': 'text/plain; charset=utf-8' },
  });
};
