import { describe, expect, it } from 'vitest';
import { createService } from '../../../src/app/service.js';
import { isSound, startupReport, startupSummary } from '../../../src/app/startup.js';
import { DEFAULT_CONFIG, mergeConfig } from '../../../src/config/config.js';
import { buildSignOff } from '../../../src/domain/itp/signOff.js';
import { d, range } from '../../support/contextFixtures.js';
import { commercialJob, emptyJob, sampleJob } from '../../support/jobFixtures.js';

const config = DEFAULT_CONFIG;

describe('startupReport on a job that can be valued', () => {
  const startup = startupReport(createService(sampleJob(), config), config);

  it('names the file, the job and where it is listening', () => {
    expect(startup.lines[0]).toBe('chainageworks serving chainageworks.json');
    expect(startup.lines[1]).toBe('job          A1-IMP A1 improvement, section 2');
    expect(startup.lines).toContain('contract sum £312,190.00');
    expect(startup.lines).toContain('listening    127.0.0.1:8730');
  });

  it('counts what the job is carrying', () => {
    expect(startup.lines).toContain('packages     3');
    expect(startup.lines).toContain('records      3');
  });

  it('takes the host and the port off the config it was given', () => {
    const other = mergeConfig(config, { http: { host: '0.0.0.0', port: 9100 } });
    const lines = startupReport(createService(sampleJob(), other), other).lines;
    expect(lines).toContain('listening    0.0.0.0:9100');
  });

  it('serves', () => {
    expect(startup.refuses).toBe(false);
    expect(startupSummary(startup)).toBe('ready');
  });

  it('mentions the warnings without refusing over them', () => {
    expect(startup.lines.some((line) => line.startsWith('warnings'))).toBe(true);
    expect(startup.lines.some((line) => line.startsWith('errors'))).toBe(false);
  });
});

describe('startupReport on a job with nothing loaded', () => {
  const startup = startupReport(createService(emptyJob(), config), config);

  it('names the blockers', () => {
    expect(startup.lines.some((line) => line.startsWith('blockers'))).toBe(true);
    expect(startup.lines.join('\n')).toContain('no bill has been loaded');
  });

  it('refuses to serve', () => {
    expect(startup.refuses).toBe(true);
    expect(startupSummary(startup)).toBe('refusing to serve, the job cannot be valued');
  });
});

describe('startupReport on a job with a dangling reference', () => {
  const job = commercialJob();
  job.signOffs.add(
    buildSignOff({
      id: 'SO-99',
      pointId: 'IP-NOWHERE',
      packageId: 'WP-01',
      extent: range('12+000', '12+400'),
      signedOn: d('2025-03-12'),
      by: 'R Engineer',
    }),
  );
  const service = createService(job, config);
  const startup = startupReport(service, config);

  it('says how many errors there are and points at the doctor', () => {
    expect(startup.lines.join('\n')).toContain('errors       1, run cw doctor');
  });

  it('serves anyway, because most of the numbers are still right', () => {
    expect(startup.refuses).toBe(false);
    expect(startupSummary(startup)).toBe('ready');
  });

  it('is not sound, which is a different question from whether it serves', () => {
    expect(isSound(service)).toBe(false);
  });
});

describe('isSound', () => {
  it('is true for a job the tool built itself', () => {
    expect(isSound(createService(commercialJob(), config))).toBe(true);
  });

  it('is false for a job with nothing loaded', () => {
    expect(isSound(createService(emptyJob(), config))).toBe(false);
  });
});
