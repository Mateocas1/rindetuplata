/**
 * Instrument page URLs (T7c).
 *
 * An instrument is identified by its API id; the URL adds the slugified display
 * name in front of it so a reader (and a search engine) sees what the page is
 * about. Two instruments that share a display name therefore keep distinct URLs
 * because the id is always appended.
 *
 * The slug only ever contains `[a-z0-9-]`: accents are folded, runs of anything
 * else collapse into one dash, and the result is trimmed.
 */

const COMBINING_MARKS = /[\u0300-\u036f]/g;
const NOT_ALNUM = /[^a-z0-9]+/g;
const EDGE_DASHES = /^-+|-+$/g;
const FALLBACK = 'instrumento';

export function slugify(name: string): string {
  const slug = name
    .normalize('NFD')
    .replace(COMBINING_MARKS, '')
    .toLowerCase()
    .replace(NOT_ALNUM, '-')
    .replace(EDGE_DASHES, '');
  return slug === '' ? FALLBACK : slug;
}

export function instrumentSlug(name: string, id: string): string {
  return `${slugify(name)}-${id}`;
}

/** Canonical instrument page path, trailing slash included (one URL shape everywhere). */
export function instrumentPath(name: string, id: string): string {
  return `/instrumentos/${instrumentSlug(name, id)}/`;
}

/**
 * The href for a ranking row when a page was built for it, or null so the table
 * renders the plain name instead of linking to a 404 (D1). `pageSlugs` is the
 * set produced by `lib/pages.ts` at build time and passed to the island.
 */
export function instrumentPageHref(
  pageSlugs: ReadonlySet<string>,
  entry: { id: string; name: string },
): string | null {
  const slug = instrumentSlug(entry.name, entry.id);
  return pageSlugs.has(slug) ? `/instrumentos/${slug}/` : null;
}
