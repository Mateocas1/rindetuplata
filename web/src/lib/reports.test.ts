import { mkdtemp, mkdir, writeFile } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { describe, expect, it } from 'vitest';
import {
  REPORTS_FIXTURES_DIR_ENV,
  leftOutReasonLabel,
  loadMonthlyReport,
  loadMonthlyReports,
  loadReportMonths,
  monthLabel,
  reportPath,
} from './reports';

/**
 * Build-time access to the monthly "who beat inflation" report. Like the other loaders it reads the API while
 * building and, with `WEB_FIXTURES=1`, the synthetic files in `web/fixtures/reports/`. The fixture is checked
 * against the API's own rules so it cannot drift into data no API would return.
 */
const FIXTURES = { WEB_FIXTURES: '1' } as const;

function okJson(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'content-type': 'application/json' },
  });
}

describe('monthLabel and reportPath', () => {
  it('names the month in Spanish, lower case, with its year', () => {
    expect(monthLabel('2026-10')).toBe('octubre de 2026');
    expect(monthLabel('2027-01')).toBe('enero de 2027');
    expect(monthLabel('2026-09')).toBe('septiembre de 2026');
  });

  it('puts every report under /informes/ with a trailing slash', () => {
    expect(reportPath('2026-10')).toBe('/informes/2026-10/');
  });
});

describe('leftOutReasonLabel', () => {
  it('explains every reason the API returns in Spanish', () => {
    for (const reason of [
      'NOT_CAPTURED_AT_START',
      'GAP_IN_CAPTURES',
      'MISSING_BASE_QUOTE',
      'MISSING_END_QUOTE',
      'NOT_OFFERED_TO_NON_CLIENTS',
      'INVALID_DATA',
      'FOREIGN_CURRENCY',
      'RESTRICTED_CLASS',
      'FUND_CLASS_NOT_FOUND',
    ]) {
      const label = leftOutReasonLabel(reason);
      expect(label, reason).not.toBe(reason);
      expect(label.length, reason).toBeGreaterThan(10);
    }
  });

  it('falls back to the raw code for a reason it does not know', () => {
    expect(leftOutReasonLabel('NEW_REASON')).toBe('NEW_REASON');
  });
});

describe('fixture mode', () => {
  it('lists the months of the synthetic index, newest first', async () => {
    const months = await loadReportMonths({ env: FIXTURES });

    expect(months.map((entry) => entry.month)).toEqual(['2026-10']);
    expect(months[0]).toMatchObject({ monthlyInflation: 0.021, ranked: 5, beatInflation: 4 });
  });

  it('reads the report of a month and keeps it consistent with the API rules', async () => {
    const report = await loadMonthlyReport('2026-10', { env: FIXTURES });

    expect(report.month).toBe('2026-10');
    expect(report.inflation.index).toBe('INDEC_CPI_MONTHLY');
    expect(report.inflation.source.id).toBe('bcra-estadisticas-monetarias');
    expect(report.totals.ranked).toBe(report.rows.length);
    expect(report.totals.leftOut).toBe(report.leftOut.length);
    expect(report.totals.beatInflation).toBe(report.rows.filter((row) => row.beatInflation).length);
    const reals = report.rows.map((row) => row.real);
    expect(reals).toEqual([...reals].sort((a, b) => b - a));
    for (const row of report.rows) {
      const real = (1 + row.nominal) / (1 + report.inflation.value) - 1;
      // The API rounds both returns to six decimals, so the quotient of the rounded nominal is off by about 1e-6.
      expect(row.real, row.name).toBeCloseTo(real, 5);
      expect(row.beatInflation, row.name).toBe(row.real > 0);
      expect(row.coverage.days, row.name).toBeLessThanOrEqual(row.coverage.total);
    }
  });

  it('loads every available month with its report', async () => {
    const reports = await loadMonthlyReports({ env: FIXTURES });

    expect(reports.map((report) => report.month)).toEqual(['2026-10']);
  });

  it('says which months are recorded when the one asked for is not', async () => {
    await expect(loadMonthlyReport('2026-08', { env: FIXTURES })).rejects.toThrow(/2026-08.*2026-10/s);
  });

  it('refuses anything that is not a yyyy-MM month before touching the file system', async () => {
    for (const month of ['../fixtures', '2026-13', '2026-9', 'octubre', '']) {
      await expect(loadMonthlyReport(month, { env: FIXTURES }), month).rejects.toThrow(/yyyy-MM/);
    }
  });

  it('reads another directory when the env override names one, an empty list included', async () => {
    const directory = await mkdtemp(join(tmpdir(), 'reports-'));
    await writeFile(join(directory, 'index.json'), JSON.stringify({ months: [] }));
    const env = { ...FIXTURES, [REPORTS_FIXTURES_DIR_ENV]: directory };

    expect(await loadReportMonths({ env })).toEqual([]);
    expect(await loadMonthlyReports({ env })).toEqual([]);
  });

  it('reads reports/ under the fixtures directory option', async () => {
    const directory = await mkdtemp(join(tmpdir(), 'fixtures-'));
    await mkdir(join(directory, 'reports'));
    await writeFile(join(directory, 'reports', 'index.json'), JSON.stringify({ months: [] }));

    expect(await loadReportMonths({ env: FIXTURES, fixturesDir: directory })).toEqual([]);
  });

  it('fails the build on a malformed list instead of rendering a wrong page', async () => {
    const directory = await mkdtemp(join(tmpdir(), 'reports-'));
    await writeFile(join(directory, 'index.json'), JSON.stringify({ meses: [] }));
    const env = { ...FIXTURES, [REPORTS_FIXTURES_DIR_ENV]: directory };

    await expect(loadReportMonths({ env })).rejects.toThrow(/Invalid report list/);
  });
});

describe('API mode', () => {
  const env = { API_URL: 'http://localhost:8080/' };

  it('requests the list and the month from the reports endpoints', async () => {
    const urls: string[] = [];
    const fetcher: typeof fetch = async (input) => {
      urls.push(String(input));
      return String(input).endsWith('/monthly')
        ? okJson({ months: [{ month: '2026-11', monthlyInflation: 0.02, ranked: 3, beatInflation: 1 }] })
        : okJson({
            month: '2026-11',
            from: '2026-11-01',
            to: '2026-11-30',
            inflation: { index: 'INDEC_CPI_MONTHLY', value: 0.02, observationDate: '2026-11-30', confidence: 'OFFICIAL_AUTOMATIC', source: { id: 'x', displayName: 'X', official: true } },
            rows: [],
            leftOut: [],
            totals: { ranked: 0, beatInflation: 0, leftOut: 0 },
          });
    };

    expect((await loadReportMonths({ env, fetch: fetcher })).map((entry) => entry.month)).toEqual(['2026-11']);
    expect((await loadMonthlyReport('2026-11', { env, fetch: fetcher })).month).toBe('2026-11');
    expect(urls).toEqual([
      'http://localhost:8080/api/v1/reports/monthly',
      'http://localhost:8080/api/v1/reports/monthly/2026-11',
    ]);
  });

  it('sends the build token like the other loaders, and never puts it in an error', async () => {
    let headers: Record<string, string> = {};
    const fetcher: typeof fetch = async (_input, init) => {
      headers = init?.headers as Record<string, string>;
      return okJson({ months: [] });
    };
    await loadReportMonths({ env: { ...env, API_BUILD_TOKEN: ' s3cret ' }, fetch: fetcher });
    expect(headers['x-build-token']).toBe('s3cret');

    const failing: typeof fetch = async () => okJson({ detail: 'no' }, 500);
    await expect(
      loadReportMonths({ env: { ...env, API_BUILD_TOKEN: 's3cret' }, fetch: failing, sleep: async () => undefined }),
    ).rejects.not.toThrow(/s3cret/);
  });

  it('fails the build with the status when the API refuses', async () => {
    const fetcher: typeof fetch = async () => okJson({ detail: 'boom' }, 500);

    await expect(loadReportMonths({ env, fetch: fetcher, sleep: async () => undefined })).rejects.toThrow(
      /failed with 500/,
    );
  });

  it('requires API_URL outside fixture mode', async () => {
    await expect(loadReportMonths({ env: {} })).rejects.toThrow(/API_URL/);
  });

  it('rejects a payload that is not a report', async () => {
    const fetcher: typeof fetch = async () => okJson({ month: '2026-11' });

    await expect(loadMonthlyReport('2026-11', { env, fetch: fetcher })).rejects.toThrow(/Invalid report/);
  });
});

describe('the build client on the report loaders (T4d, L36a; F6)', () => {
  const env = { API_URL: 'http://localhost:8080' };
  const noSleep = async () => undefined;
  const LIST = { months: [{ month: '2026-11', monthlyInflation: 0.02, ranked: 3, beatInflation: 1 }] };
  const REPORT = {
    month: '2026-11',
    from: '2026-11-01',
    to: '2026-11-30',
    inflation: { index: 'INDEC_CPI_MONTHLY', value: 0.02, observationDate: '2026-11-30', confidence: 'OFFICIAL_AUTOMATIC', source: { id: 'x', displayName: 'X', official: true } },
    rows: [],
    leftOut: [],
    totals: { ranked: 0, beatInflation: 0, leftOut: 0 },
  };

  function counting(respond: (call: number) => Response | Promise<Response>) {
    const calls: string[] = [];
    const fetcher = (async (input: RequestInfo | URL) => {
      calls.push(String(input));
      return respond(calls.length);
    }) as unknown as typeof fetch;
    return { fetch: fetcher, calls };
  }

  it('retries a 503 on the list and returns it', async () => {
    const waits: number[] = [];
    const { fetch, calls } = counting((call) => (call === 1 ? new Response('down', { status: 503 }) : okJson(LIST)));
    const months = await loadReportMonths({
      env,
      fetch,
      sleep: async (ms) => {
        waits.push(ms);
      },
    });
    expect(months.map((entry) => entry.month)).toEqual(['2026-11']);
    expect(calls).toHaveLength(2);
    expect(waits).toEqual([1000]);
  });

  it('gives a hung report request a deadline (API_TIMEOUT_MS) and names the URL', async () => {
    const { fetch, calls } = counting(() => new Promise<Response>(() => undefined));
    const failure = loadMonthlyReport('2026-11', { env: { ...env, API_TIMEOUT_MS: '20' }, fetch, sleep: noSleep });
    await expect(failure).rejects.toThrow(
      /GET http:\/\/localhost:8080\/api\/v1\/reports\/monthly\/2026-11 failed after 3 attempts: timed out after 20 ms/,
    );
    expect(calls).toHaveLength(3);
  });

  it('never retries a 404 for a month the API has no report for', async () => {
    const { fetch, calls } = counting(() => okJson({ detail: 'no report' }, 404));
    await expect(loadMonthlyReport('2026-11', { env, fetch, sleep: noSleep })).rejects.toThrow(/failed with 404/);
    expect(calls).toHaveLength(1);
  });

  it('sends an identical request once per build', async () => {
    const { fetch, calls } = counting((call) => okJson(call === 1 ? LIST : REPORT));
    const options = { env, fetch, sleep: noSleep };
    await loadReportMonths(options);
    await loadReportMonths(options);
    await loadMonthlyReport('2026-11', options);
    await loadMonthlyReport('2026-11', options);
    expect(calls).toEqual([
      'http://localhost:8080/api/v1/reports/monthly',
      'http://localhost:8080/api/v1/reports/monthly/2026-11',
    ]);
  });
});
