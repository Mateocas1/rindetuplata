/**
 * The methodology explains the dólar MEP break-even in a section of its own (S5, L24 b), with the same worked
 * example the API tests pin (30% TNA over 30 days against buy 1534,20 and sell 1539,40). Read from the page source:
 * the build smoke test checks the built page, this one checks the copy that has to stay in step with the API.
 */
import { readFileSync } from 'node:fs';
import { describe, expect, it } from 'vitest';

const page = readFileSync(new URL('../pages/metodologia.astro', import.meta.url), 'utf8');

function section(id: string): string {
  const match = new RegExp(`<section[^>]*aria-labelledby="${id}"[\\s\\S]*?</section>`).exec(page);
  if (match === null) throw new Error(`section ${id} not found`);
  return match[0];
}

describe('metodologia: dólar MEP section', () => {
  const mep = section('dolar-mep');
  const text = mep.replace(/\{' '\}/g, ' ').replace(/<[^>]+>/g, ' ').replace(/\s+/g, ' ');

  it('has its own titled section', () => {
    expect(mep).toContain('<h2 id="dolar-mep" class="type-section">');
    expect(text).toContain('Contra el dólar MEP');
  });

  it('explains the rule: buy at the sell price, leave at the buy price, the spread included', () => {
    expect(text).toMatch(/comprás dólares al precio de venta/);
    expect(text).toMatch(/precio de compra/);
    expect(text).toContain('(1 + rendimiento) × venta ÷ compra − 1');
    expect(text).toMatch(/spread/);
  });

  it('works the example through with the API numbers', () => {
    expect(mep).toContain('1.534,20');
    expect(mep).toContain('1.539,40');
    expect(text).toContain('2,47%');
    expect(text).toContain('2,81%');
    expect(mep).toContain('1.577,36');
  });

  it('says what it is not: no forecast, no taxes or costs', () => {
    expect(text).toMatch(/No es un pronóstico/);
    expect(text).toMatch(/impuestos/);
  });

  it('does not touch the wallets passage of the sources list', () => {
    expect(page).toContain("data: 'Billeteras remuneradas'");
    expect(mep).not.toContain('Billeteras remuneradas');
  });
});
