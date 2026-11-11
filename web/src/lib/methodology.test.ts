/**
 * The methodology explains the dólar MEP break-even in a section of its own, with the same worked
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

describe('metodologia: liquidez y garantía section', () => {
  const liquidity = section('liquidez-garantia');
  const text = liquidity.replace(/\{' '\}/g, ' ').replace(/<[^>]+>/g, ' ').replace(/\s+/g, ' ');

  it('has its own titled section, listed in the table of contents', () => {
    expect(liquidity).toContain('<h2 id="liquidez-garantia"');
    expect(text).toContain('Liquidez y garantía de los depósitos');
    expect(page).toContain("id: 'liquidez-garantia'");
  });

  it('says when the money can be taken out, per kind', () => {
    expect(text).toMatch(/plazo fijo[^.]*al vencimiento/i);
    expect(text).toMatch(/billeteras[^.]*disponible al instante/i);
    expect(text).toMatch(/fondos money market[^.]*rescate en el día/i);
  });

  it('states the guarantee with its exclusions and without a peso cap', () => {
    expect(text).toMatch(/BCRA/);
    expect(text).toMatch(/tope por persona y por entidad/);
    expect(text).toMatch(/tasas muy por encima/);
    expect(text).toMatch(/cuotapartes[^.]*no son depósitos/);
    expect(text).toMatch(/proveedor de servicios de pago/);
    expect(text).not.toMatch(/\$\s*\d/);
  });

  it('links the BCRA text as the source of truth', () => {
    expect(liquidity).toContain('https://www.bcra.gob.ar/archivos/Pdfs/texord/t-seggar.pdf');
  });
});
