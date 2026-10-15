import { describe, expect, it } from 'vitest';
import {
  DEFAULT_RANKING_QUERY,
  parseRankingQuery,
  serializeRankingQuery,
  toApiParams,
  tryParseAmount,
  tryParseDays,
  type RankingQueryState,
} from './rankingQuery';

/**
 * Query-string state for the home ranking (T7b: `?monto=&dias=&tipo=&cliente=`,
 * defaults 1000000 / 30). Values are always validated: anything unusable falls
 * back to the default, so a hand-edited URL can never send the API a bad request.
 */
describe('parseRankingQuery', () => {
  it('returns the defaults for an empty query string', () => {
    expect(parseRankingQuery('')).toEqual(DEFAULT_RANKING_QUERY);
    expect(parseRankingQuery('?')).toEqual(DEFAULT_RANKING_QUERY);
  });

  it('reads amount and days', () => {
    const state = parseRankingQuery('?monto=500000&dias=60');
    expect(state.amount).toBe(500000);
    expect(state.days).toBe(60);
  });

  it('accepts a decimal amount', () => {
    expect(parseRankingQuery('?monto=1500000.5').amount).toBe(1500000.5);
  });

  it('falls back to the default amount when it is not a positive number', () => {
    for (const raw of ['abc', '0', '-5', '1e6', '', ' ', 'NaN']) {
      expect(parseRankingQuery(`?monto=${raw}`).amount).toBe(DEFAULT_RANKING_QUERY.amount);
    }
  });

  it('falls back to the default days when it is not a whole number in range', () => {
    for (const raw of ['abc', '0', '-5', '7.5', '3651', '']) {
      expect(parseRankingQuery(`?dias=${raw}`).days).toBe(DEFAULT_RANKING_QUERY.days);
    }
  });

  it('reads the kinds in the canonical order, upper case', () => {
    expect(parseRankingQuery('?tipo=CRYPTO,WALLET').kinds).toEqual(['WALLET', 'CRYPTO']);
    expect(parseRankingQuery('?tipo=wallet').kinds).toEqual(['WALLET']);
  });

  it('ignores unknown kinds and treats an empty result as every kind', () => {
    expect(parseRankingQuery('?tipo=banana').kinds).toEqual([]);
    expect(parseRankingQuery('?tipo=WALLET,banana').kinds).toEqual(['WALLET']);
    expect(parseRankingQuery('?tipo=').kinds).toEqual([]);
  });

  it('deduplicates repeated kinds', () => {
    expect(parseRankingQuery('?tipo=WALLET,WALLET').kinds).toEqual(['WALLET']);
  });

  it('reads client instrument ids as sorted unique digits', () => {
    expect(parseRankingQuery('?cliente=423,29').clientOf).toEqual(['29', '423']);
    expect(parseRankingQuery('?cliente=29,29,abc,-1,1.5').clientOf).toEqual(['29']);
    expect(parseRankingQuery('?cliente=').clientOf).toEqual([]);
  });

  it('URL-decodes the values', () => {
    expect(parseRankingQuery('?monto=500000&tipo=PLAZO_FIJO%2CWALLET').kinds).toEqual([
      'PLAZO_FIJO',
      'WALLET',
    ]);
  });

  it('ignores unrelated parameters', () => {
    expect(parseRankingQuery('?utm_source=x&monto=250000').amount).toBe(250000);
  });
});

describe('serializeRankingQuery', () => {
  it('returns an empty string when everything is at its default', () => {
    expect(serializeRankingQuery(DEFAULT_RANKING_QUERY)).toBe('');
  });

  it('serializes only what differs from the default', () => {
    expect(serializeRankingQuery({ ...DEFAULT_RANKING_QUERY, days: 60 })).toBe('?dias=60');
  });

  it('serializes amount, days, kinds and clients', () => {
    const search = serializeRankingQuery({
      amount: 500000,
      days: 60,
      kinds: ['PLAZO_FIJO', 'WALLET'],
      clientOf: ['423', '29'],
    });
    expect(search).toBe('?monto=500000&dias=60&tipo=PLAZO_FIJO%2CWALLET&cliente=29%2C423');
  });

  it('round-trips through parseRankingQuery', () => {
    const state: RankingQueryState = {
      amount: 250000,
      days: 90,
      kinds: ['FUND'],
      clientOf: ['29'],
    };
    expect(parseRankingQuery(serializeRankingQuery(state))).toEqual({
      amount: 250000,
      days: 90,
      kinds: ['FUND'],
      clientOf: ['29'],
    });
  });
});

describe('tryParseAmount', () => {
  it('accepts a positive amount with or without decimals', () => {
    expect(tryParseAmount('500000')).toBe(500000);
    expect(tryParseAmount('1500000.5')).toBe(1500000.5);
    expect(tryParseAmount('  250000  ')).toBe(250000);
  });

  it('rejects anything the API would refuse', () => {
    for (const raw of ['', '  ', 'abc', '0', '-5', '1e6', 'Infinity']) {
      expect(tryParseAmount(raw)).toBeNull();
    }
  });
});

describe('tryParseDays', () => {
  it('accepts a whole number of days inside the supported range', () => {
    expect(tryParseDays('30')).toBe(30);
    expect(tryParseDays('1')).toBe(1);
    expect(tryParseDays('3650')).toBe(3650);
  });

  it('rejects anything else', () => {
    for (const raw of ['', '0', '-5', '7.5', '3651', 'muchos']) {
      expect(tryParseDays(raw)).toBeNull();
    }
  });
});

describe('toApiParams', () => {
  it('names the API parameters and repeats kinds and clients', () => {
    const params = toApiParams({
      amount: 500000,
      days: 60,
      kinds: ['CRYPTO', 'PLAZO_FIJO'],
      clientOf: ['29'],
    });
    expect(params.getAll('amount')).toEqual(['500000']);
    expect(params.getAll('days')).toEqual(['60']);
    expect(params.getAll('kind')).toEqual(['PLAZO_FIJO', 'CRYPTO']);
    expect(params.getAll('clientOf')).toEqual(['29']);
  });

  it('omits the kind filter when every kind is requested', () => {
    const params = toApiParams({ ...DEFAULT_RANKING_QUERY, amount: 1 });
    expect(params.has('kind')).toBe(false);
    expect(params.get('amount')).toBe('1');
  });

  it('always sends the amount and the days', () => {
    const params = toApiParams(DEFAULT_RANKING_QUERY);
    expect(params.get('amount')).toBe('1000000');
    expect(params.get('days')).toBe('30');
  });
});
