/**
 * The real-result line: pesos above or below expected inflation in words, sign and color,
 * the percentage secondary; the percentage alone when the API sent no `realEarned`; "no data" without inflation.
 */
import { createElement as h } from 'react';
import { renderToStaticMarkup } from 'react-dom/server';
import { describe, expect, it } from 'vitest';
import { formatMoneyWhole } from '../../lib/format';
import RealResult from './RealResult';

const render = (props: Parameters<typeof RealResult>[0]) => renderToStaticMarkup(h(RealResult, props));
const text = (html: string) => html.replace(/<[^>]+>/g, '').replace(/[ \t\n]+/g, ' ').trim();

describe('formatMoneyWhole', () => {
  it('rounds to whole pesos with the typographic minus', () => {
    expect(formatMoneyWhole(3806.13)).toBe('$ 3.806');
    expect(formatMoneyWhole(-2401.5)).toBe('−$ 2.402');
    expect(formatMoneyWhole(0)).toBe('$ 0');
  });
});

describe('RealResult', () => {
  it('says how many pesos it beats inflation by, in the gain tone', () => {
    const html = render({ realEarned: 3806.13, realReturn: 0.003806, indexLabel: 'REM' });
    expect(text(html)).toContain('$ 3.806 por encima de la inflación');
    expect(text(html)).toContain('+0,38% real');
    expect(html).toContain('text-gain');
    expect(text(html)).toContain('contra la inflación esperada (REM)');
  });

  it('says how many pesos it loses against inflation, in the loss tone', () => {
    const html = render({ realEarned: -2401.2, realReturn: -0.0024, indexLabel: 'REM' });
    expect(text(html)).toContain('$ 2.401 por debajo de la inflación');
    expect(text(html)).toContain('−0,24% real');
    expect(html).toContain('text-loss');
  });

  it('falls back to the percentage when the API sent no pesos', () => {
    const html = render({ realReturn: 0.003806, indexLabel: 'REM' });
    expect(text(html)).toContain('+0,38% por encima de la inflación');
    expect(html).toContain('text-gain');
  });

  it('calls an exact tie with inflation what it is', () => {
    expect(text(render({ realEarned: 0, realReturn: 0, indexLabel: 'REM' }))).toContain('Igual que la inflación');
  });

  it('says there is no inflation data instead of inventing one', () => {
    expect(text(render({ realEarned: null, realReturn: null, indexLabel: 'REM' }))).toBe('Sin dato de inflación');
  });
});
