import { existsSync, readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { llmsTxt } from '../src/lib/llms';
import { GET } from '../src/pages/llms.txt';

/**
 * `llms.txt`, in the llmstxt.org format: an H1 with the site name, a blockquote summary,
 * H2 sections of `- [name](url): notes` links, and the `Optional` section last. The site and API hosts come from
 * the build (`SITE_URL`, `PUBLIC_API_URL`), so no localhost can leak into a production build, and every link
 * must point at something that exists: a site page in `src/pages`, an API path in the recorded OpenAPI document.
 */
const SITE = new URL('https://rindetuplata.ar');
const API = new URL('https://api.rindetuplata.ar');
const PAGES = fileURLToPath(new URL('../src/pages/', import.meta.url));
const OPENAPI = JSON.parse(
  readFileSync(fileURLToPath(new URL('../fixtures/openapi.json', import.meta.url)), 'utf8'),
) as { paths: Record<string, unknown> };

const LINK = /^- \[([^\]]+)\]\((https?:\/\/[^)\s]+)\)(?:: (.+))?$/;

function sections(body: string): Map<string, string[]> {
  const result = new Map<string, string[]>();
  let current = '';
  for (const line of body.split('\n')) {
    if (line.startsWith('## ')) {
      current = line.slice(3);
      result.set(current, []);
    } else if (current !== '' && line.trim() !== '') {
      result.get(current)!.push(line);
    }
  }
  return result;
}

function links(body: string): Array<{ name: string; url: URL; section: string }> {
  const found: Array<{ name: string; url: URL; section: string }> = [];
  for (const [section, lines] of sections(body)) {
    for (const line of lines) {
      const match = LINK.exec(line);
      if (match) found.push({ name: match[1], url: new URL(match[2]), section });
    }
  }
  return found;
}

describe('llmsTxt', () => {
  const body = llmsTxt(SITE, API);

  it('opens with the site name as H1 and a blockquote summary, as llmstxt.org asks', () => {
    const lines = body.split('\n');
    expect(lines[0]).toBe('# rindetuplata');
    expect(lines[1]).toBe('');
    expect(lines[2]).toMatch(/^> .{40,}/);
    expect(lines[2]).toContain('inflación');
  });

  it('names the site, the public API, the MCP server and the optional sitemap as H2 sections, in that order', () => {
    expect([...sections(body).keys()]).toEqual(['Sitio', 'API pública', 'MCP', 'Optional']);
  });

  it('writes every list line as a markdown link with absolute URL and an optional note', () => {
    for (const [section, lines] of sections(body)) {
      for (const line of lines) {
        expect(line, `${section}: ${line}`).toMatch(LINK);
      }
    }
    expect(body.endsWith('\n')).toBe(true);
  });

  it('links the site sections on the site host and every one of them resolves to a page', () => {
    const site = links(body).filter((link) => link.section === 'Sitio');
    expect(site.map((link) => link.url.pathname)).toEqual(
      expect.arrayContaining(['/', '/plazo-fijo/', '/billeteras/', '/fondos/', '/cripto/', '/bancos/', '/informes/', '/metodologia/']),
    );
    for (const link of site) {
      expect(link.url.origin).toBe(SITE.origin);
      const path = link.url.pathname.replace(/^\/|\/$/g, '');
      const candidates = path === '' ? ['index.astro'] : [`${path}.astro`, `${path}/index.astro`];
      expect(
        candidates.some((candidate) => existsSync(PAGES + candidate)),
        `${link.url.pathname} has no page in src/pages`,
      ).toBe(true);
    }
  });

  it('links the public API and its OpenAPI document on the API host, every path known to the OpenAPI recording', () => {
    const api = links(body).filter((link) => link.section === 'API pública');
    const paths = api.map((link) => link.url.pathname);
    expect(paths).toEqual(
      expect.arrayContaining([
        '/api/v1/ranking',
        '/api/v1/simulations',
        '/api/v1/instruments',
        '/api/v1/entities/banks',
        '/api/v1/reports/monthly',
        '/v3/api-docs',
      ]),
    );
    for (const link of api) {
      expect(link.url.origin).toBe(API.origin);
      if (link.url.pathname.startsWith('/api/')) {
        expect(Object.keys(OPENAPI.paths), link.url.pathname).toContain(link.url.pathname);
      }
    }
  });

  it('names the MCP endpoint, its transport and its tools', () => {
    const mcp = links(body).filter((link) => link.section === 'MCP');
    expect(mcp.map((link) => link.url.href)).toContain('https://api.rindetuplata.ar/mcp');
    const section = sections(body).get('MCP')!.join('\n');
    expect(section).toContain('POST');
    for (const tool of [
      'get_ranking',
      'simulate_instrument',
      'get_instrument_history',
      'list_monthly_reports',
      'get_monthly_report',
    ]) {
      expect(section).toContain(tool);
    }
  });

  it('points the optional section at the sitemap of the site', () => {
    const optional = links(body).filter((link) => link.section === 'Optional');
    expect(optional.map((link) => link.url.href)).toEqual(['https://rindetuplata.ar/sitemap-index.xml']);
  });

  it('puts the API on the site host when both are one origin, and keeps localhost out of a production body', () => {
    const local = new URL('http://localhost:4321');
    expect(llmsTxt(local, local)).toContain('(http://localhost:4321/mcp)');
    expect(body).not.toContain('localhost');
  });
});

describe('llms.txt endpoint', () => {
  afterEach(() => {
    vi.unstubAllEnvs();
  });

  it('refuses to answer without a site URL instead of falling back to localhost', () => {
    expect(() => GET({ site: undefined } as never)).toThrow(/SITE_URL/);
  });

  it('serves plain text with the configured hosts', async () => {
    vi.stubEnv('PUBLIC_API_URL', 'https://api.rindetuplata.ar');

    const response = await GET({ site: SITE } as never);

    expect(response.headers.get('content-type')).toBe('text/plain; charset=utf-8');
    const text = await response.text();
    expect(text).toBe(llmsTxt(SITE, API));
    expect(text).toContain('(https://api.rindetuplata.ar/mcp)');
    expect(text).toContain('(https://rindetuplata.ar/informes/)');
  });

  it('serves the API from the site origin when PUBLIC_API_URL is empty, like the browser client does', async () => {
    vi.stubEnv('PUBLIC_API_URL', '');

    const response = await GET({ site: SITE } as never);

    expect(await response.text()).toContain('(https://rindetuplata.ar/mcp)');
  });
});
