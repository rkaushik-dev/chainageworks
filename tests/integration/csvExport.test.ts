import { describe, expect, it } from 'vitest';
import { certificateFor, contextOf, createService, outturnFor } from '../../src/app/service.js';
import { DEFAULT_CONFIG } from '../../src/config/config.js';
import { parseCsv } from '../../src/io/csv.js';
import {
  billCsv,
  certificateCsv,
  certificateTotalsCsv,
  outturnCsv,
  recordsCsv,
} from '../../src/reporting/csvExport.js';
import { runCommand } from '../support/cliHarness.js';
import { d } from '../support/contextFixtures.js';
import { commercialJob, sampleJob } from '../support/jobFixtures.js';

const service = () => createService(sampleJob(), DEFAULT_CONFIG);
const cutOff = d('2025-03-25');

describe('billCsv', () => {
  const text = billCsv(contextOf(service()), cutOff);
  const table = parseCsv(text);

  it('writes a heading and a row for every item', () => {
    expect(table.headings).toEqual([
      'code',
      'description',
      'unit',
      'quantity',
      'rate',
      'kind',
      'value',
    ]);
    expect(table.rows).toHaveLength(8);
  });

  it('writes money a spreadsheet can add up', () => {
    expect(text).toContain('E423.1');
    expect(text).not.toContain('£');
    expect(text).not.toMatch(/\d,\d\d\d\./);
  });
});

describe('recordsCsv', () => {
  it('splits the extent back into a from and a to column', () => {
    const table = parseCsv(recordsCsv(contextOf(service()), cutOff));
    expect(table.headings).toContain('from');
    expect(table.headings).toContain('to');
    expect(table.rows).toHaveLength(3);
    expect(recordsCsv(contextOf(service()), cutOff)).toContain('12+000.000');
  });

  it('leaves out anything measured after the date', () => {
    const early = parseCsv(recordsCsv(contextOf(service()), d('2025-03-05')));
    expect(early.rows).toHaveLength(1);
  });
});

describe('certificateCsv', () => {
  const text = certificateCsv(certificateFor(service(), 3));
  const table = parseCsv(text);

  it('writes one row for every allocation on every package', () => {
    expect(table.rows.length).toBeGreaterThan(3);
    expect(table.headings).toContain('capped');
  });

  it('repeats the package columns down the file', () => {
    const packages = table.rows.map((row) => row.values[2]);
    expect(new Set(packages).size).toBeLessThan(packages.length);
  });

  it('carries the valuation number and cut off on every row', () => {
    for (const row of table.rows) {
      expect(row.values[0]).toBe('3');
      expect(row.values[1]).toBe('2025-03-25');
    }
  });
});

describe('certificateTotalsCsv', () => {
  it('writes the certificate the way it prints', () => {
    const table = parseCsv(certificateTotalsCsv(certificateFor(service(), 3)));
    expect(table.rows.map((row) => row.values[0])).toEqual([
      'measured work',
      'materials on site',
      'daywork',
      'gross valuation',
      'less retention',
      'net valuation',
      'less previously certified',
      'amount due this certificate',
    ]);
    expect(table.rows.at(-1)!.values[1]).toBe('15344.87');
  });
});

describe('outturnCsv', () => {
  it('writes a row for every bill item with the basis it was forecast on', () => {
    const table = parseCsv(outturnCsv(outturnFor(service(), 3)));
    expect(table.rows).toHaveLength(8);
    expect(table.headings).toContain('basis');
    expect(table.rows.every((row) => ['bill', 'trend'].includes(row.values[7] ?? ''))).toBe(
      true,
    );
  });
});

describe('cw csv', () => {
  it('writes the table it was asked for', async () => {
    const run = await runCommand(['csv', 'bill', '3', 'out/bill.csv'], { job: sampleJob() });
    expect(run.code).toBe(0);
    expect(run.out).toBe('out/bill.csv');
    expect(run.written['out/bill.csv']!.split('\n')[0]).toBe(
      'code,description,unit,quantity,rate,kind,value',
    );
  });

  it('writes every table it knows', async () => {
    for (const table of ['bill', 'records', 'certificate', 'totals', 'outturn']) {
      const run = await runCommand(['csv', table, '3', `out/${table}.csv`], {
        job: commercialJob(),
      });
      expect([table, run.code]).toEqual([table, 0]);
      expect(run.written[`out/${table}.csv`]!.length).toBeGreaterThan(0);
    }
  });

  it('reports the row count as json', async () => {
    const run = await runCommand(['--json', 'csv', 'bill', '3', 'out/bill.csv'], {
      job: sampleJob(),
    });
    expect(JSON.parse(run.out)).toEqual({ file: 'out/bill.csv', rows: 8 });
  });

  it('refuses a table it does not know and names the ones it does', async () => {
    const run = await runCommand(['csv', 'insurances', '3', 'out/x.csv'], { job: sampleJob() });
    expect(run.code).toBe(1);
    expect(run.err).toContain('no such table');
    expect(run.written).toEqual({});
  });

  it('saves nothing back to the job file', async () => {
    const run = await runCommand(['csv', 'bill', '3', 'out/bill.csv'], { job: sampleJob() });
    expect(run.saved).toEqual([]);
  });
});
