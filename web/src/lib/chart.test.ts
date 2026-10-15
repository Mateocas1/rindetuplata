import { readFileSync } from 'node:fs';
import { join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { describe, expect, it } from 'vitest';
import { buildChart, seriesFromHistory, type ChartSeriesInput } from './chart';
import type { InstrumentHistory } from './types';

/**
 * History chart geometry (T7c, S10): the scales, tick positions and path strings
 * the instrument page draws as an inline SVG at build time. d3 does the geometry;
 * the component only applies classes, so the chart ships zero client JavaScript.
 */
const webRoot = resolve(fileURLToPath(new URL('.', import.meta.url)), '../..');

function history(id: string): InstrumentHistory {
  return JSON.parse(
    readFileSync(join(webRoot, `fixtures/history/${id}.json`), 'utf8'),
  ) as InstrumentHistory;
}

const rate: ChartSeriesInput = {
  key: 'rate',
  label: 'Tasa (TNA)',
  dashed: false,
  marker: 'circle',
  points: [
    { date: '2026-10-01', value: 0.2 },
    { date: '2026-10-08', value: 0.24 },
    { date: '2026-10-14', value: 0.22 },
  ],
};

const inflation: ChartSeriesInput = {
  key: 'inflation',
  label: 'Inflación mensual (INDEC)',
  dashed: true,
  marker: 'square',
  points: [
    { date: '2026-08-31', value: 0.017 },
    { date: '2026-09-30', value: 0.019 },
  ],
};

describe('seriesFromHistory', () => {
  it('plots the rate snapshots of a plazo fijo against INDEC monthly inflation', () => {
    const series = seriesFromHistory(history('29'));
    expect(series.map((entry) => entry.key)).toEqual(['rate', 'inflation']);
    expect(series[0].label).toBe('Tasa (TNA)');
    expect(series[0].points).toEqual([{ date: '2026-10-14', value: 0.24 }]);
    expect(series[0].dashed).toBe(false);
    expect(series[0].marker).not.toBe(series[1].marker);
    expect(series[1].dashed).toBe(true);
    expect(series[1].label).toBe('Inflación mensual (INDEC)');
    expect(series[1].points.map((point) => point.date)).toEqual([
      '2026-07-31',
      '2026-08-31',
      '2026-09-30',
    ]);
  });

  it('plots the fund TNA equivalent and drops quotes whose value fell', () => {
    const series = seriesFromHistory(history('102'));
    expect(series[0].key).toBe('rate');
    expect(series[0].label).toBe('TNA equivalente del fondo');
    expect(series[0].points).toEqual([{ date: '2026-10-14', value: 0.175192 }]);
  });

  it('names the basis when the API gives a TEA instead of a TNA', () => {
    const tea = history('29');
    tea.points = [{ ...tea.points[0], basis: 'TEA' }];
    expect(seriesFromHistory(tea)[0].label).toBe('Tasa (TEA)');
  });

  it('keeps a series with no points so the component can explain the gap', () => {
    const empty = history('102');
    empty.quotes = [{ quoteDate: '2026-10-14', vcp: 10, tnaEquivalent: null }];
    empty.monthlyInflation = [];
    const series = seriesFromHistory(empty);
    expect(series[0].points).toEqual([]);
    expect(series[1].points).toEqual([]);
  });
});

describe('buildChart', () => {
  const chart = buildChart({ series: [rate, inflation] });

  it('lays the plot inside the figure and keeps the series metadata', () => {
    expect(chart.width).toBeGreaterThan(0);
    expect(chart.height).toBeGreaterThan(0);
    expect(chart.plot.x0).toBeGreaterThan(0);
    expect(chart.plot.x1).toBeLessThan(chart.width);
    expect(chart.plot.y1).toBeLessThan(chart.height);
    expect(chart.plot.y0).toBeLessThan(chart.plot.y1);
    expect(chart.hasData).toBe(true);
    expect(chart.series.map((entry) => [entry.key, entry.dashed, entry.marker])).toEqual([
      ['rate', false, 'circle'],
      ['inflation', true, 'square'],
    ]);
  });

  it('draws one path per point, in date order', () => {
    const rateGeometry = chart.series[0];
    expect(rateGeometry.path.startsWith('M')).toBe(true);
    expect(rateGeometry.path.match(/L/g)).toHaveLength(2);
    expect(rateGeometry.points).toHaveLength(3);
    expect(rateGeometry.points[0].date).toBe('2026-10-01');
    expect(rateGeometry.points[1].date).toBe('2026-10-08');
    expect(rateGeometry.points[2].date).toBe('2026-10-14');
  });

  it('maps a bigger rate to a smaller y and a later date to a bigger x', () => {
    const [first, second, third] = chart.series[0].points;
    expect(second.value).toBeGreaterThan(first.value);
    expect(second.y).toBeLessThan(first.y);
    expect(second.x).toBeGreaterThan(first.x);
    expect(third.x).toBeGreaterThan(second.x);
  });

  it('keeps every point and tick inside the plot box', () => {
    for (const series of chart.series) {
      for (const point of series.points) {
        expect(point.x).toBeGreaterThanOrEqual(chart.plot.x0);
        expect(point.x).toBeLessThanOrEqual(chart.plot.x1);
        expect(point.y).toBeGreaterThanOrEqual(chart.plot.y0);
        expect(point.y).toBeLessThanOrEqual(chart.plot.y1);
      }
    }
    for (const tick of [...chart.xTicks, ...chart.yTicks]) {
      expect(tick.position).toBeGreaterThanOrEqual(0);
      expect(tick.label).not.toBe('');
    }
    for (const tick of chart.xTicks) {
      expect(tick.position).toBeGreaterThanOrEqual(chart.plot.x0);
      expect(tick.position).toBeLessThanOrEqual(chart.plot.x1);
    }
    for (const tick of chart.yTicks) {
      expect(tick.position).toBeGreaterThanOrEqual(chart.plot.y0);
      expect(tick.position).toBeLessThanOrEqual(chart.plot.y1);
    }
  });

  it('includes zero in the value axis so a loss is visible against the baseline', () => {
    expect(chart.yTicks.some((tick) => tick.label === '0,00%')).toBe(true);
  });

  it('survives a single point per series (one ingestion day is the normal case)', () => {
    const single = buildChart({ series: [rate, inflation] });
    const onePoint: ChartSeriesInput = { ...rate, points: [rate.points[0]] };
    const geometry = buildChart({ series: [onePoint] });
    expect(geometry.series[0].points).toHaveLength(1);
    expect(geometry.series[0].path.startsWith('M')).toBe(true);
    expect(geometry.xTicks.length).toBeGreaterThan(0);
    expect(single.series[0].points).toHaveLength(3);
  });

  it('reports an empty chart when no series has a point', () => {
    const geometry = buildChart({
      series: [
        { ...rate, points: [] },
        { ...inflation, points: [] },
      ],
    });
    expect(geometry.hasData).toBe(false);
    expect(geometry.series.every((entry) => entry.path === '')).toBe(true);
    expect(geometry.series.every((entry) => entry.points.length === 0)).toBe(true);
  });

  it('is deterministic for the same input', () => {
    const again = buildChart({ series: [rate, inflation] });
    expect(again.series[0].path).toBe(chart.series[0].path);
    expect(again.xTicks).toEqual(chart.xTicks);
    expect(again.yTicks).toEqual(chart.yTicks);
  });

  it('handles every recorded history without throwing', () => {
    for (const id of ['29', '102', '1322', '1275']) {
      const geometry = buildChart({ series: seriesFromHistory(history(id)) });
      expect(geometry.series).toHaveLength(2);
      for (const series of geometry.series) {
        for (const point of series.points) {
          expect(Number.isFinite(point.x)).toBe(true);
          expect(Number.isFinite(point.y)).toBe(true);
        }
      }
    }
  });
});

describe('axis labels', () => {
  it('uses the shared short date formatter from format.ts', () => {
    const geometry = buildChart({ series: [rate, inflation] });
    expect(geometry.xTicks.length).toBeGreaterThan(0);
    for (const tick of geometry.xTicks) {
      expect(tick.label).toMatch(/^\d{2}\/\d{2}\/\d{2}$/);
    }
  });
});
