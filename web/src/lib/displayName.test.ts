import { describe, expect, it } from 'vitest';
import { displayName } from './displayName';

/**
 * Display names (D13: presentation only; the API keeps the source's legal name).
 * The curated map wins over the generic rules, which only tidy names the map does not know.
 */

const CURATED: [raw: string, shown: string][] = [
  ['BANCO BBVA ARGENTINA S.A.', 'BBVA'],
  ['BANCO BICA S.A.', 'Banco BICA'],
  ['BANCO CMF S.A.', 'Banco CMF'],
  ['BANCO COMAFI SOCIEDAD ANONIMA', 'Banco Comafi'],
  ['BANCO CREDICOOP COOPERATIVO LIMITADO', 'Banco Credicoop'],
  ['BANCO DE COMERCIO S.A.', 'Banco de Comercio'],
  ['BANCO DE FORMOSA S.A.', 'Banco de Formosa'],
  ['BANCO DE GALICIA Y BUENOS AIRES S.A.', 'Banco Galicia'],
  ['BANCO DE LA CIUDAD DE BUENOS AIRES', 'Banco Ciudad'],
  ['BANCO DE LA NACION ARGENTINA', 'Banco Nación'],
  ['BANCO DE LA PROVINCIA DE BUENOS AIRES', 'Banco Provincia'],
  ['BANCO DE LA PROVINCIA DE CORDOBA S.A.', 'Banco de Córdoba'],
  ['BANCO DEL CHUBUT S.A.', 'Banco del Chubut'],
  ['BANCO DEL SOL S.A.', 'Banco del Sol'],
  ['BANCO DINO S.A.', 'Banco Dino'],
  ['BANCO HIPOTECARIO S.A.', 'Banco Hipotecario'],
  ['BANCO JULIO SOCIEDAD ANONIMA', 'Banco Julio'],
  ['BANCO MACRO S.A.', 'Banco Macro'],
  ['BANCO MARIVA S.A.', 'Banco Mariva'],
  ['BANCO MASVENTAS S.A.', 'Banco Masventas'],
  ['BANCO MERIDIAN S.A.', 'Banco Meridian'],
  ['BANCO PATAGONIA S.A.', 'Banco Patagonia'],
  ['BANCO PROVINCIA DE TIERRA DEL FUEGO', 'Banco de Tierra del Fuego'],
  ['BANCO SANTANDER ARGENTINA S.A.', 'Santander'],
  ['BANCO VOII S.A.', 'Banco Voii'],
  ['BIBANK S.A.', 'Bibank'],
  ['CRÉDITO REGIONAL COMPAÑÍA FINANCIERA S.A.U.', 'Crédito Regional'],
  ['INDUSTRIAL AND COMMERCIAL BANK OF CHINA (ARGENTINA) S.A.U.', 'ICBC'],
  ['REBA COMPAÑIA FINANCIERA S.A.', 'Reba'],
  ['UALA', 'Ualá'],
];

describe('displayName: curated map', () => {
  it.each(CURATED)('shows %s as %s', (raw, shown) => {
    expect(displayName(raw)).toBe(shown);
  });

  it('matches the key regardless of case and surrounding spaces', () => {
    expect(displayName('  banco macro s.a.  ')).toBe('Banco Macro');
    expect(displayName('Uala')).toBe('Ualá');
  });
});

describe('displayName: generic rules', () => {
  it.each([
    ['BANCO EJEMPLO DE LA PLATA S.A.', 'Banco Ejemplo de la Plata'],
    ['FINANCIERA NACION SOCIEDAD ANONIMA', 'Financiera Nación'],
    ['BANCO MACRO SA', 'Banco Macro'],
    ['MERCADO PAGO SAU', 'Mercado Pago'],
    ['BANCO EJEMPLO DEL SUR S.A.U.', 'Banco Ejemplo del Sur'],
    ['FOO BAR, S.A.', 'Foo Bar'],
    ['FOO BAR SOCIEDAD ANÓNIMA', 'Foo Bar'],
    ['COOPERATIVA DEL SUR COOPERATIVO LIMITADO', 'Cooperativa del Sur'],
    ['BANCO CMF DEL SUR S.A.', 'Banco CMF del Sur'],
    ['HSBC BANK ARGENTINA S.A.', 'HSBC Bank Argentina'],
    ['CREDITO INVERSION FINANCIERA SA', 'Crédito Inversión Financiera'],
    ['CÓRDOBA GANADERIA', 'Córdoba Ganadería'],
    ['COMPANIA DINAMICO', 'Compañía Dinámico'],
    ['DE LA PLATA FONDO', 'De la Plata Fondo'],
    ['FONDO CEDEAR USD MEP', 'Fondo CEDEAR USD MEP'],
  ])('shows %s as %s', (raw, shown) => {
    expect(displayName(raw)).toBe(shown);
  });

  it('collapses repeated spaces before the rules run', () => {
    expect(displayName('BANCO   EJEMPLO  S.A.')).toBe('Banco Ejemplo');
  });

  it('falls back to the trimmed raw name when stripping the suffix leaves nothing', () => {
    expect(displayName('  , S.A.  ')).toBe(', S.A.');
  });

  it('returns an empty string for empty or blank input, because there is no name to show', () => {
    expect(displayName('')).toBe('');
    expect(displayName('   ')).toBe('');
  });
});

describe('displayName: mixed-case passthrough', () => {
  it.each([
    'Adcap Ahorro Dinámico Fondo de Dinero - Clase G',
    'Naranja X',
    'letsbit ARS',
  ])('keeps the mixed-case name %s unchanged', (raw) => {
    expect(displayName(raw)).toBe(raw);
  });

  it('strips only the legal suffix of a mixed-case name', () => {
    expect(displayName('Naranja X S.A.')).toBe('Naranja X');
  });
});
