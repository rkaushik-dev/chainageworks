import { mkdtemp, readFile, rm, writeFile } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { moneyMajor } from '../../../src/domain/money/money.js';
import { earnAll } from '../../../src/progress/earn.js';
import { jobContext } from '../../../src/store/job.js';
import {
  fileExists,
  loadJob,
  parseJob,
  restoreBackup,
  saveAndVerify,
  saveJob,
} from '../../../src/store/fileStore.js';
import { d } from '../../support/contextFixtures.js';
import { sampleJob } from '../../support/jobFixtures.js';

let dir: string;
let path: string;

beforeEach(async () => {
  dir = await mkdtemp(join(tmpdir(), 'chainageworks-'));
  path = join(dir, 'job.json');
});

afterEach(async () => {
  await rm(dir, { recursive: true, force: true });
});

describe('saveJob', () => {
  it('writes a file that reads back as the same job', async () => {
    await saveJob(sampleJob(), path);
    expect(await fileExists(path)).toBe(true);
    const again = await loadJob(path);
    expect(again.settings.id).toBe('A1-IMP');
    expect(again.records.count()).toBe(3);
  });

  it('values the same after a trip through the disk', async () => {
    await saveJob(sampleJob(), path);
    const again = await loadJob(path);
    expect(moneyMajor(earnAll(jobContext(again), d('2025-03-31'))[0]!.earnedValue)).toBe(
      16_152.5,
    );
  });

  it('writes readable json by default and one line on request', async () => {
    await saveJob(sampleJob(), path);
    expect((await readFile(path, 'utf8')).split('\n').length).toBeGreaterThan(50);
    await saveJob(sampleJob(), path, { pretty: false });
    expect((await readFile(path, 'utf8')).trimEnd().split('\n')).toHaveLength(1);
  });

  it('makes the directory it is given', async () => {
    const nested = join(dir, 'a', 'b', 'job.json');
    await saveJob(sampleJob(), nested);
    expect(await fileExists(nested)).toBe(true);
  });

  it('leaves no temporary files behind', async () => {
    await saveJob(sampleJob(), path);
    const { readdir } = await import('node:fs/promises');
    expect((await readdir(dir)).filter((f) => f.endsWith('.tmp'))).toEqual([]);
  });

  it('keeps the previous file as a backup', async () => {
    const first = sampleJob();
    await saveJob(first, path);
    const second = sampleJob();
    second.records.remove('SR-03');
    await saveJob(second, path);
    expect((await loadJob(path)).records.count()).toBe(2);
    expect(await fileExists(`${path}.bak`)).toBe(true);
    await restoreBackup(path);
    expect((await loadJob(path)).records.count()).toBe(3);
  });

  it('writes no backup on the first save or when asked not to', async () => {
    await saveJob(sampleJob(), path);
    expect(await fileExists(`${path}.bak`)).toBe(false);
    await saveJob(sampleJob(), path, { backup: false });
    expect(await fileExists(`${path}.bak`)).toBe(false);
  });

  it('refuses to restore a backup that is not there', async () => {
    await expect(restoreBackup(path)).rejects.toThrow(/no backup to restore/);
  });
});

describe('loadJob', () => {
  it('says so when there is no file', async () => {
    await expect(loadJob(join(dir, 'missing.json'))).rejects.toThrow(
      /no job file at that path/,
    );
  });

  it('separates a file that is not json from a file that is not a job', async () => {
    await writeFile(path, 'not json at all', 'utf8');
    await expect(loadJob(path)).rejects.toThrow(/not valid json/);

    await writeFile(path, '{"version":99}', 'utf8');
    await expect(loadJob(path)).rejects.toThrow(/different version/);
  });

  it('names the file in the message', async () => {
    await writeFile(path, '{"version":99}', 'utf8');
    await expect(loadJob(path)).rejects.toThrow(new RegExp('job.json'));
  });
});

describe('parseJob', () => {
  it('reads text without touching the disk', () => {
    const text = JSON.stringify({ version: 1 });
    expect(() => parseJob(text)).toThrow();
  });

  it('reports the placeholder path when it was given none', () => {
    expect(() => parseJob('{"version":99}')).toThrow(/<memory>/);
  });
});

describe('saveAndVerify', () => {
  it('saves and reads the job straight back', async () => {
    await saveAndVerify(sampleJob(), path);
    expect((await loadJob(path)).records.count()).toBe(3);
  });
});
