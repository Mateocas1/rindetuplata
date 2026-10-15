/**
 * Structured-data builders (T7c, S14).
 *
 * Plain data, so a page can pass the result to the layout and a test can check
 * it without a browser. The rate stays the API's own number: only the unit the
 * schema expects changes (a percentage), never the value itself.
 */

export interface BreadcrumbItem {
  name: string;
  url: string;
}

export interface BreadcrumbListJsonLd {
  '@context': 'https://schema.org';
  '@type': 'BreadcrumbList';
  itemListElement: Array<{
    '@type': 'ListItem';
    position: number;
    name: string;
    item: string;
  }>;
}

export interface FinancialProductInput {
  name: string;
  description: string;
  url: string;
  /** The API's rate as a fraction; the schema wants a percentage. */
  interestRate: number;
  /** The entity behind the product, when the API names one. */
  provider?: string | null;
}

export interface FinancialProductJsonLd {
  '@context': 'https://schema.org';
  '@type': 'FinancialProduct';
  name: string;
  description: string;
  url: string;
  interestRate: number;
  provider?: { '@type': 'Organization'; name: string };
}

export function breadcrumbJsonLd(items: BreadcrumbItem[]): BreadcrumbListJsonLd {
  if (items.length === 0) {
    throw new Error('A breadcrumb needs at least one item');
  }
  return {
    '@context': 'https://schema.org',
    '@type': 'BreadcrumbList',
    itemListElement: items.map((entry, index) => ({
      '@type': 'ListItem',
      position: index + 1,
      name: entry.name,
      item: entry.url,
    })),
  };
}

/** Fraction to the percentage the schema expects, with two decimals. */
function ratePercent(fraction: number): number {
  return Number((fraction * 100).toFixed(2));
}

export function financialProductJsonLd(
  input: FinancialProductInput,
): FinancialProductJsonLd {
  if (input.name.trim() === '') {
    throw new Error('A financial product needs a name');
  }
  if (input.url.trim() === '') {
    throw new Error('A financial product needs a url');
  }
  if (!Number.isFinite(input.interestRate)) {
    throw new Error('A financial product needs a finite rate');
  }

  const product: FinancialProductJsonLd = {
    '@context': 'https://schema.org',
    '@type': 'FinancialProduct',
    name: input.name,
    description: input.description,
    url: input.url,
    interestRate: ratePercent(input.interestRate),
  };
  if (input.provider !== undefined && input.provider !== null && input.provider !== '') {
    product.provider = { '@type': 'Organization', name: input.provider };
  }
  return product;
}
