/**
 * `realEarned` (UI clarity T2, decision D4): the pesos an investment earns above (positive) or below (negative) the
 * expected inflation over its horizon, in today's pesos.
 *
 *   realEarned = finalAmount / (1 + inflationOverHorizon) - amount
 *
 * The API computes it in Java (`Inflation.realEarned`); the recorded fixtures predate the field, so
 * `derive-real-earned.mjs` derives it with this module. The arithmetic is exact: decimals are read as BigInt
 * mantissas with a power-of-ten scale, the quotient is one exact fraction, and it is rounded once, to cents,
 * half-even. A float division is never used for the result.
 */

/** Parses a decimal (string or finite number) into an exact `{ mantissa, scale }`, value = mantissa / 10^scale. */
function parseDecimal(value) {
  const text = String(value).trim();
  const match = /^([+-]?)(\d*)(?:\.(\d*))?(?:[eE]([+-]?\d+))?$/.exec(text);
  if (match === null || (match[2] === '' && (match[3] ?? '') === '')) {
    throw new Error(`Not a decimal number: ${text}`);
  }
  const [, sign, whole, fraction = '', exponent = '0'] = match;
  let mantissa = BigInt(whole + fraction);
  let scale = fraction.length - Number(exponent);
  if (scale < 0) {
    mantissa *= 10n ** BigInt(-scale);
    scale = 0;
  }
  return { mantissa: sign === '-' ? -mantissa : mantissa, scale };
}

/** `numerator / denominator` rounded to an integer, ties to the even neighbour; `denominator` must be positive. */
function divideHalfEven(numerator, denominator) {
  let quotient = numerator / denominator;
  const remainder = numerator % denominator;
  const twice = (remainder < 0n ? -remainder : remainder) * 2n;
  if (twice > denominator || (twice === denominator && quotient % 2n !== 0n)) {
    quotient += numerator < 0n ? -1n : 1n;
  }
  return quotient;
}

/**
 * The real result in pesos as a fixed two-decimal string (`"3805.67"`, `"-9803.92"`, `"0.00"`).
 * Throws when the inflation is -100% or less, like `Inflation.realEarned` in Java.
 */
export function realEarned(amount, finalAmount, overHorizon) {
  const a = parseDecimal(amount);
  const f = parseDecimal(finalAmount);
  const i = parseDecimal(overHorizon);
  const tenToI = 10n ** BigInt(i.scale);
  const factor = tenToI + i.mantissa; // (1 + inflation) * 10^i.scale
  if (factor <= 0n) {
    throw new Error(`Inflation must be greater than -100%: ${overHorizon}`);
  }
  // f / 10^sf * 10^si / factor - a / 10^sa, over one common denominator, times 100 for cents.
  const tenToF = 10n ** BigInt(f.scale);
  const tenToA = 10n ** BigInt(a.scale);
  const numerator = (f.mantissa * tenToI * tenToA - a.mantissa * tenToF * factor) * 100n;
  const denominator = tenToF * tenToA * factor;
  const cents = divideHalfEven(numerator, denominator);
  const magnitude = cents < 0n ? -cents : cents;
  const pesos = magnitude / 100n;
  const centavos = String(magnitude % 100n).padStart(2, '0');
  return `${cents < 0n ? '-' : ''}${pesos}.${centavos}`;
}

/** Matches `"realReturn":V,` plus a `"realEarned":V,` that a previous run may have added. */
const REAL_RETURN = /"realReturn":(null|-?[0-9.eE+-]+),(?:"realEarned":(?:null|-?[0-9.]+),)?/g;

/**
 * Adds (or corrects) `"realEarned"` right after every `"realReturn"` of a fixture's JSON text, keeping the rest of
 * the text byte for byte (the recordings keep numbers such as `1000000.00` and `0.015860`). `entries` are the
 * ranked entries in text order; `amount` and `overHorizon` come from the request and inflation of the same
 * request. The value is `null` exactly when `realReturn` is null. Idempotent.
 */
export function addRealEarned(text, entries, amount, overHorizon) {
  let index = 0;
  const result = text.replace(REAL_RETURN, (_match, realReturn) => {
    const entry = entries[index++];
    if (entry === undefined) {
      throw new Error('More realReturn fields in the text than entries');
    }
    if (realReturn !== 'null' && (overHorizon === null || overHorizon === undefined)) {
      throw new Error('An entry has a realReturn but the request has no inflation over the horizon');
    }
    const value = realReturn === 'null' ? 'null' : realEarned(amount, entry.finalAmount, overHorizon);
    return `"realReturn":${realReturn},"realEarned":${value},`;
  });
  if (index !== entries.length) {
    throw new Error(`Expected ${entries.length} realReturn fields, found ${index}`);
  }
  return result;
}
