/**
 * History chart geometry (T7c, S10, S14).
 *
 * The instrument page plots the rate the API returned against the past INDEC
 * monthly inflation, at build time, as an inline SVG. All the geometry lives
 * here — scales, tick positions, path strings, marker coordinates — so the
 * component stays markup and the chart ships zero client JavaScript.
 *
 * No financial math happens here: every value comes from the API and is only
 * placed on a scale.
 */
import { scaleLinear, scaleUtc, type ScaleLinear, type ScaleTime } from 'd3-scale';
import { line } from 'd3-shape';
import { formatPercent, formatShortDate } from './format';
import type { InstrumentHistory } from './types';

export interface TimePoint {
  date: string;
  value: number;
}

export type SeriesMarker = 'circle' | 'square';

export interface ChartSeriesInput {
  key: string;
  label: string;
  /** Dashed strokes plus a distinct marker carry the distinction without colour. */
  dashed: boolean;
  marker: SeriesMarker;
  points: TimePoint[];
}

export interface ChartPoint extends TimePoint {
  x: number;
  y: number;
}

export interface ChartSeries extends Omit<ChartSeriesInput, 'points'> {
  path: string;
  points: ChartPoint[];
}

export interface ChartTick {
  position: number;
  label: string;
}

export interface ChartBox {
  x0: number;
  y0: number;
  x1: number;
  y1: number;
}

export interface ChartGeometry {
  width: number;
  height: number;
  plot: ChartBox;
  xTicks: ChartTick[];
  yTicks: ChartTick[];
  series: ChartSeries[];
  hasData: boolean;
}

export interface ChartOptions {
  series: ChartSeriesInput[];
  width?: number;
  height?: number;
}

export const CHART_WIDTH = 640;
export const CHART_HEIGHT = 240;
const PADDING = { top: 12, right: 12, bottom: 28, left: 56 };
const DAY_MS = 24 * 60 * 60 * 1000;
/** Empty fallback: a fixed month, so an empty chart is still deterministic. */
const EMPTY_DOMAIN: [Date, Date] = [new Date(Date.UTC(2020, 0, 1)), new Date(Date.UTC(2020, 0, 31))];

const xTickFormat = { format: (date: Date): string => formatShortDate(date.toISOString().slice(0, 10)) };

function toDate(isoDate: string): Date {
  return new Date(`${isoDate}T00:00:00Z`);
}

function xDomain(all: TimePoint[]): [Date, Date] {
  if (all.length === 0) return EMPTY_DOMAIN;
  const times = all.map((point) => toDate(point.date).getTime());
  const min = Math.min(...times);
  const max = Math.max(...times);
  // A single ingestion day is the normal case: widen it so the point is a point.
  if (min === max) return [new Date(min - 3 * DAY_MS), new Date(max + 3 * DAY_MS)];
  return [new Date(min), new Date(max)];
}

function yMaxOf(all: TimePoint[]): number {
  const max = all.reduce((highest, point) => Math.max(highest, point.value), 0);
  const span = max - Math.min(0, ...all.map((point) => point.value));
  // Headroom above the highest value, so the peak is not glued to the frame.
  return max + (span === 0 ? 0.01 : span * 0.05);
}

/** The rate series and the INDEC inflation series of one instrument's history. */
export function seriesFromHistory(history: InstrumentHistory): ChartSeriesInput[] {
  const ratePoints: TimePoint[] = history.quotes.length > 0
    ? history.quotes
        .filter((quote) => quote.tnaEquivalent !== null)
        .map((quote) => ({ date: quote.quoteDate, value: quote.tnaEquivalent as number }))
    : history.points
        .filter((point) => point.rate !== null)
        .map((point) => ({ date: point.captureDate, value: point.rate as number }));

  const basis = history.points[0]?.basis;
  const rateLabel =
    history.quotes.length > 0
      ? 'TNA equivalente del fondo'
      : basis === undefined || basis === 'TNA'
        ? 'Tasa (TNA)'
        : `Tasa (${basis})`;

  return [
    { key: 'rate', label: rateLabel, dashed: false, marker: 'circle', points: ratePoints },
    {
      key: 'inflation',
      label: 'Inflación mensual (INDEC)',
      dashed: true,
      marker: 'square',
      points: history.monthlyInflation.map((point) => ({
        date: point.observationDate,
        value: point.value,
      })),
    },
  ];
}

export function buildChart(options: ChartOptions): ChartGeometry {
  const width = options.width ?? CHART_WIDTH;
  const height = options.height ?? CHART_HEIGHT;
  const plot: ChartBox = {
    x0: PADDING.left,
    y0: PADDING.top,
    x1: width - PADDING.right,
    y1: height - PADDING.bottom,
  };

  const all = options.series.flatMap((series) => series.points);
  const xScale: ScaleTime<number, number> = scaleUtc()
    .domain(xDomain(all))
    .range([plot.x0, plot.x1]);
  const yScale: ScaleLinear<number, number> = scaleLinear()
    .domain([Math.min(0, ...all.map((point) => point.value)), yMaxOf(all)])
    .range([plot.y1, plot.y0])
    .nice();

  const draw = line<TimePoint>()
    .x((point) => xScale(toDate(point.date)))
    .y((point) => yScale(point.value));

  const series: ChartSeries[] = options.series.map((entry) => ({
    key: entry.key,
    label: entry.label,
    dashed: entry.dashed,
    marker: entry.marker,
    path: draw(entry.points) ?? '',
    points: entry.points.map((point) => ({
      date: point.date,
      value: point.value,
      x: xScale(toDate(point.date)),
      y: yScale(point.value),
    })),
  }));

  const plotWidth = plot.x1 - plot.x0;
  const xTicks: ChartTick[] = all.length === 0 && options.series.length === 0
    ? []
    : xScale.ticks(Math.max(2, Math.round(plotWidth / 110))).map((tick) => ({
        position: xScale(tick),
        label: xTickFormat.format(tick),
      }));
  const yTicks: ChartTick[] = yScale.ticks(4).map((tick) => ({
    position: yScale(tick),
    label: formatPercent(tick),
  }));

  return {
    width,
    height,
    plot,
    xTicks,
    yTicks,
    series,
    hasData: all.length > 0,
  };
}
