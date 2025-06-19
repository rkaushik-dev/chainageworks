import { describe, expect, it } from 'vitest';
import {
  certificateFor,
  createService,
  paymentsFor,
  progressFor,
} from '../../../src/app/service.js';
import { DEFAULT_CONFIG } from '../../../src/config/config.js';
import {
  certificateMarkdown,
  escapeCell,
  markdownHeading,
  markdownPairs,
  markdownTable,
  progressMarkdown,
  subcontractMarkdown,
} from '../../../src/reporting/format/markdown.js';
import { commercialJob, fluctuatingJob, sampleJob } from '../../support/jobFixtures.js';

const service = () => createService(commercialJob(), DEFAULT_CONFIG);

describe('escapeCell', () => {
  it('escapes a pipe so it cannot split a row', () => {
    expect(escapeCell('300 mm | 450 mm')).toBe('300 mm \\| 450 mm');
  });

  it('flattens a newline that would break the table', () => {
    expect(escapeCell('two\nlines')).toBe('two lines');
    expect(escapeCell('windows\r\nlines')).toBe('windows lines');
  });

  it('leaves ordinary text alone', () => {
    expect(escapeCell('capping to 12+400')).toBe('capping to 12+400');
  });
});

describe('markdownTable', () => {
  it('writes a heading, a rule and the rows', () => {
    const text = markdownTable(
      [{ heading: 'code' }, { heading: 'rate', align: 'right' }],
      [['E423.1', '4.85']],
    );
    expect(text.split('\n')).toEqual([
      '| code | rate |',
      '| :--- | ---: |',
      '| E423.1 | 4.85 |',
    ]);
  });

  it('writes a table with no rows at all', () => {
    expect(markdownTable([{ heading: 'code' }], []).split('\n')).toHaveLength(2);
  });
});

describe('markdownHeading and markdownPairs', () => {
  it('writes a heading at the level asked for', () => {
    expect(markdownHeading('Progress', 1)).toBe('# Progress');
    expect(markdownHeading('Packages')).toBe('## Packages');
  });

  it('writes the pairs as a bold list', () => {
    expect(markdownPairs([['Budget', '£312,190.00']])).toBe('- **Budget**: £312,190.00');
  });
});

describe('certificateMarkdown', () => {
  it('opens with the certificate number and the dates', () => {
    const text = certificateMarkdown(certificateFor(service(), 3));
    expect(text.split('\n')[0]).toBe('# Payment certificate 3');
    expect(text).toContain('- **Valuation to**: 2025-03-25');
    expect(text).toContain('- **Bill revision**: Rev A');
  });

  it('bolds the amount due', () => {
    const text = certificateMarkdown(certificateFor(service(), 3));
    expect(text).toContain('| **Amount due this certificate** |');
  });

  it('leaves the adjustment row out on a fixed price contract', () => {
    expect(certificateMarkdown(certificateFor(service(), 3))).not.toContain('Price adjustment');
  });

  it('adds the adjustment row and the warning when there is a clause', () => {
    const fluctuating = createService(fluctuatingJob(), DEFAULT_CONFIG);
    const text = certificateMarkdown(certificateFor(fluctuating, 3));
    expect(text).toContain('| Price adjustment at 12.750% |');
    expect(text).toContain('> The price adjustment rests on a provisional index figure.');
  });
});

describe('progressMarkdown', () => {
  it('opens with the cut off and tables the packages', () => {
    const text = progressMarkdown(progressFor(service(), 3));
    expect(text.split('\n')[0]).toBe('# Progress at 2025-03-25');
    expect(text).toContain('## Packages');
    expect(text).toContain('| WP-01 |');
  });

  it('gives one row for every package', () => {
    const text = progressMarkdown(progressFor(service(), 3));
    const rows = text.split('\n').filter((line) => line.startsWith('| WP-'));
    expect(rows).toHaveLength(3);
  });
});

describe('subcontractMarkdown', () => {
  it('tables the orders and the notice diary', () => {
    const text = subcontractMarkdown(paymentsFor(service(), 3));
    expect(text.split('\n')[0]).toBe('# Subcontract payments at 2025-03-25');
    expect(text).toContain('## Notice diary');
    expect(text).toContain('| SA-01 | Dowsett Plant Hire |');
    expect(text).toContain('- **Exposed by default**: £40,000.00');
  });

  it('leaves both tables out on a job with no orders', () => {
    const text = subcontractMarkdown(
      paymentsFor(createService(sampleJob(), DEFAULT_CONFIG), 3),
    );
    expect(text).not.toContain('## Notice diary');
    expect(text).toContain('- **Orders**: 0');
  });
});
