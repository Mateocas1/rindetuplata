import { describe, expect, it } from 'vitest';
import { breadcrumbJsonLd, financialProductJsonLd } from './seo';

/**
 * JSON-LD builders (T7c, S14): breadcrumbs on instrument and category pages, and
 * a FinancialProduct with the API's own interest rate on instrument pages. Both
 * are plain data so the pages can be checked without a browser.
 */
describe('breadcrumbJsonLd', () => {
  it('numbers the trail in order, with absolute urls', () => {
    const block = breadcrumbJsonLd([
      { name: 'Inicio', url: 'https://rindetuplata.ar/' },
      { name: 'Plazos fijos', url: 'https://rindetuplata.ar/plazo-fijo' },
      { name: 'REBA', url: 'https://rindetuplata.ar/instrumentos/reba-29' },
    ]);

    expect(block['@context']).toBe('https://schema.org');
    expect(block['@type']).toBe('BreadcrumbList');
    expect(block.itemListElement).toEqual([
      { '@type': 'ListItem', position: 1, name: 'Inicio', item: 'https://rindetuplata.ar/' },
      {
        '@type': 'ListItem',
        position: 2,
        name: 'Plazos fijos',
        item: 'https://rindetuplata.ar/plazo-fijo',
      },
      {
        '@type': 'ListItem',
        position: 3,
        name: 'REBA',
        item: 'https://rindetuplata.ar/instrumentos/reba-29',
      },
    ]);
  });

  it('rejects an empty trail instead of emitting an invalid list', () => {
    expect(() => breadcrumbJsonLd([])).toThrow(/at least one/i);
  });
});

describe('financialProductJsonLd', () => {
  const product = financialProductJsonLd({
    name: 'REBA COMPAÑIA FINANCIERA S.A.',
    description: 'Plazo fijo a 30 días. Tasa para no clientes.',
    url: 'https://rindetuplata.ar/instrumentos/reba-compania-financiera-s-a-29/',
    interestRate: 0.24,
    provider: 'REBA COMPAÑIA FINANCIERA S.A.',
  });

  it('describes the product with the API rate as a percentage', () => {
    expect(product['@context']).toBe('https://schema.org');
    expect(product['@type']).toBe('FinancialProduct');
    expect(product.name).toBe('REBA COMPAÑIA FINANCIERA S.A.');
    expect(product.url).toBe(
      'https://rindetuplata.ar/instrumentos/reba-compania-financiera-s-a-29/',
    );
    expect(product.interestRate).toBe(24);
  });

  it('declares only properties schema.org defines on a FinancialProduct', () => {
    const serialized = JSON.stringify(product);
    expect(serialized).not.toContain('rateBasis');
    expect(serialized).not.toContain('inLanguage');
  });

  it('keeps two decimals when the API rate has more', () => {
    const fund = financialProductJsonLd({
      name: 'Alpha Pesos Plus - Clase A',
      description: 'Fondo money market.',
      url: 'https://rindetuplata.ar/instrumentos/alpha-pesos-plus-clase-a-102/',
      interestRate: 0.175192,
    });
    expect(fund.interestRate).toBe(17.52);
  });

  it('names the provider only when the API has one', () => {
    expect(product.provider).toEqual({
      '@type': 'Organization',
      name: 'REBA COMPAÑIA FINANCIERA S.A.',
    });
    const wallet = financialProductJsonLd({
      name: 'Naranja X',
      description: 'Billetera remunerada.',
      url: 'https://rindetuplata.ar/instrumentos/naranja-x-1322/',
      interestRate: 0.2,
    });
    expect(wallet.provider).toBeUndefined();
  });

  it('rejects a missing name, url or rate', () => {
    expect(() =>
      financialProductJsonLd({
        name: '',
        description: 'x',
        url: 'https://rindetuplata.ar/x',
        interestRate: 0.2,
      }),
    ).toThrow(/name/i);
    expect(() =>
      financialProductJsonLd({
        name: 'X',
        description: 'x',
        url: 'https://rindetuplata.ar/x',
        interestRate: Number.NaN,
      }),
    ).toThrow(/rate/i);
  });
});
