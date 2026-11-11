/**
 * Display names for banks, wallets and funds (presentation only). The API keeps
 * the source's legal name; this module only chooses how a name is shown.
 */

/** Curated display names, keyed by the trimmed, uppercased, space-collapsed legal name. */
const CURATED = new Map<string, string>([
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
]);

/** Trailing legal suffix, with a preceding comma or space, matched case-insensitively. */
const LEGAL_SUFFIX =
  /(?:,\s*|\s+)(?:S\.A\.U\.|S\.A\.|SAU|SA|SOCIEDAD AN[ÓO]NIMA|COOPERATIVO LIMITADO)$/i;

/** Words that stay in ALL CAPS when a legal name is title-cased. */
const ACRONYMS = new Set(['BBVA', 'BICA', 'CMF', 'ICBC', 'HSBC', 'BNA', 'FCI', 'ARS', 'USD', 'MEP', 'CEDEAR']);

/** Spanish particles that stay lowercase inside a name (but not as its first word). */
const PARTICLES = new Set(['DE', 'DEL', 'LA', 'LAS', 'LOS', 'Y', 'E']);

/** Accented spellings, keyed by the accent-free uppercase word (COMPAÑIA is reached as COMPANIA). */
const ACCENTED = new Map<string, string>([
  ['NACION', 'Nación'],
  ['CORDOBA', 'Córdoba'],
  ['COMPANIA', 'Compañía'],
  ['CREDITO', 'Crédito'],
  ['FINANCIERA', 'Financiera'],
  ['INVERSION', 'Inversión'],
  ['GANADERIA', 'Ganadería'],
  ['DINAMICO', 'Dinámico'],
  ['UALA', 'Ualá'],
]);

/** Uppercase, accent-free form of a word, used for every lookup. */
function foldAccents(word: string): string {
  return word.normalize('NFD').replace(/\p{M}/gu, '').toUpperCase();
}

/** One word of an all-caps name: acronym, particle, accented spelling, or capitalized. */
function titleCaseWord(word: string, first: boolean): string {
  const key = foldAccents(word);
  if (ACRONYMS.has(key)) {
    return key;
  }
  if (!first && PARTICLES.has(key)) {
    return word.toLowerCase();
  }
  const accented = ACCENTED.get(key);
  if (accented !== undefined) {
    return accented;
  }
  return word.toLowerCase().replace(/\p{L}/u, (letter) => letter.toUpperCase());
}

/**
 * Shows a bank, wallet or fund name as people read it. A curated name wins;
 * otherwise a trailing legal suffix is dropped, all-caps names are title-cased, and
 * mixed-case names are kept as they are. Never empty for a non-empty input.
 */
export function displayName(raw: string): string {
  const trimmed = raw.trim();
  const collapsed = trimmed.replace(/\s+/g, ' ');

  const curated = CURATED.get(collapsed.toUpperCase());
  if (curated !== undefined) {
    return curated;
  }

  const stripped = collapsed.replace(LEGAL_SUFFIX, '');
  if (stripped === '') {
    return trimmed;
  }
  if (/\p{Ll}/u.test(stripped)) {
    return stripped;
  }
  return stripped
    .split(' ')
    .map((word, index) => titleCaseWord(word, index === 0))
    .join(' ');
}
