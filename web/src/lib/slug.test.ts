import { readFileSync, readdirSync } from 'node:fs';
import { join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { describe, expect, it } from 'vitest';
import { instrumentPageHref, instrumentPath, instrumentSlug, slugify } from './slug';

/**
 * Instrument slugs (T7c): the URL of an instrument page is the slugified display
 * name plus the API id, so the URL is readable and still unique when two
 * instruments share a name.
 */
const webRoot = resolve(fileURLToPath(new URL('.', import.meta.url)), '../..');
const catalog = JSON.parse(readFileSync(join(webRoot, 'fixtures/ranking-all.json'), 'utf8')) as {
  ranked: Array<{ id: string; name: string }>;
  ineligible: Array<{ id: string; name: string }>;
};

describe('slugify', () => {
  it('lowercases, strips accents and turns every other run of symbols into one dash', () => {
    expect(slugify('REBA COMPAÑIA FINANCIERA S.A.')).toBe('reba-compania-financiera-s-a');
    expect(slugify('Champaquí Fondo Inmediato Plus - Clase D')).toBe(
      'champaqui-fondo-inmediato-plus-clase-d',
    );
    expect(slugify('Pellegrini Renta Pesos - Clase R Ley N° 27.743')).toBe(
      'pellegrini-renta-pesos-clase-r-ley-n-27-743',
    );
    expect(slugify('Super Ahorro $ - Clase L')).toBe('super-ahorro-clase-l');
  });

  it('is already stable: slugifying a slug changes nothing', () => {
    const slug = slugify('Adcap Ahorro Dinámico Fondo de Dinero - Clase G');
    expect(slugify(slug)).toBe(slug);
  });

  it('falls back when a name has no alphanumeric characters at all', () => {
    expect(slugify('$ % &')).toBe('instrumento');
  });
});

describe('instrumentSlug', () => {
  it('appends the id, so two instruments with the same name stay apart', () => {
    expect(instrumentSlug('REBA COMPAÑIA FINANCIERA S.A.', '29')).toBe(
      'reba-compania-financiera-s-a-29',
    );
    expect(instrumentSlug('Fondo Común', '1')).not.toBe(instrumentSlug('Fondo Común', '2'));
  });

  it('gives every catalogued instrument a unique, stable slug', () => {
    const entries = [...catalog.ranked, ...catalog.ineligible];
    expect(entries.length).toBeGreaterThan(0);
    const slugs = entries.map((entry) => instrumentSlug(entry.name, entry.id));
    expect(new Set(slugs).size).toBe(slugs.length);
    for (const [index, slug] of slugs.entries()) {
      expect(slug).toMatch(/^[a-z0-9]+(?:-[a-z0-9]+)*$/);
      expect(slug.endsWith(`-${entries[index].id}`)).toBe(true);
      expect(instrumentSlug(entries[index].name, entries[index].id)).toBe(slug);
    }
  });
});

describe('instrumentPath', () => {
  it('is the instrument page URL, with the trailing slash the canonical uses', () => {
    expect(instrumentPath('Naranja X', '1322')).toBe('/instrumentos/naranja-x-1322/');
  });
});

describe('instrumentPageHref', () => {
  const slugs = new Set(['reba-compania-financiera-s-a-29']);

  it('links a row that has a page (D1)', () => {
    expect(instrumentPageHref(slugs, { id: '29', name: 'REBA COMPAÑIA FINANCIERA S.A.' })).toBe(
      '/instrumentos/reba-compania-financiera-s-a-29/',
    );
  });

  it('returns null for a row outside the page set, so the table renders plain text', () => {
    expect(instrumentPageHref(slugs, { id: '999', name: 'Suma Fija' })).toBeNull();
    // Same id, different name: a different page, and this one was never built.
    expect(instrumentPageHref(slugs, { id: '29', name: 'REBA Cliente' })).toBeNull();
  });
});

describe('catalog fixture coverage', () => {
  it('has one recorded history file per catalogued instrument', () => {
    const historyFiles = new Set(
      readdirSync(join(webRoot, 'fixtures/history')).map((file) => file.replace('.json', '')),
    );
    for (const entry of [...catalog.ranked, ...catalog.ineligible]) {
      expect(historyFiles.has(entry.id), `no history for ${entry.id}`).toBe(true);
    }
  });
});
