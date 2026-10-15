import { describe, expect, it } from 'vitest';
import { CATEGORIES, categoryForKind } from './categories';
import { RANKING_KINDS } from './types';

/**
 * Category landing pages (T7c): one per kind, each with its own path, nav label,
 * title and intro copy. The instrument page and the header nav read the same map,
 * so a new kind cannot get an instrument page without a category page.
 */
describe('categories', () => {
  it('covers every kind the ranking serves, exactly once', () => {
    const kinds = CATEGORIES.map((category) => category.kind);
    expect([...kinds].sort()).toEqual([...RANKING_KINDS].sort());
    expect(new Set(kinds).size).toBe(kinds.length);
  });

  it('has unique, url-safe paths that match the slug', () => {
    const paths = CATEGORIES.map((category) => category.path);
    expect(new Set(paths).size).toBe(paths.length);
    for (const category of CATEGORIES) {
      expect(category.path).toBe(`/${category.slug}/`);
      expect(category.slug).toMatch(/^[a-z-]+$/);
    }
  });

  it('gives every category its own H1, title, description and intro', () => {
    for (const category of CATEGORIES) {
      expect(category.h1.length).toBeGreaterThan(0);
      expect(category.title).toContain('rindetuplata');
      expect(category.description.length).toBeGreaterThan(40);
      expect(category.intro.length).toBeGreaterThan(40);
      expect(category.navLabel.length).toBeGreaterThan(0);
    }
    expect(new Set(CATEGORIES.map((category) => category.h1)).size).toBe(CATEGORIES.length);
    expect(new Set(CATEGORIES.map((category) => category.title)).size).toBe(CATEGORIES.length);
  });

  it('finds the category of a kind and returns null for anything else', () => {
    expect(categoryForKind('FUND')?.slug).toBe('fondos');
    expect(categoryForKind('PLAZO_FIJO')?.slug).toBe('plazo-fijo');
    expect(categoryForKind('PLAZO_FIJO_OFFICIAL')).toBeNull();
    expect(categoryForKind('')).toBeNull();
  });
});
