import { randomBytes } from 'node:crypto';
import { constants } from 'node:fs';
import { access, copyFile, mkdir, open, readFile, rename, unlink } from 'node:fs/promises';
import { dirname, join } from 'node:path';
import { BadInputError, CwError } from '../errors.js';
import type { Job } from './job.js';
import { jobFromJson, jobToJson } from './codec/job.js';

export interface SaveOptions {
  /** Keep the previous file alongside the new one. On by default. */
  readonly backup?: boolean;
  /** Indent the JSON so a person can read it. On by default. */
  readonly pretty?: boolean;
}

function tempPathFor(path: string): string {
  return join(dirname(path), `.${randomBytes(6).toString('hex')}.tmp`);
}

/**
 * Write the job out without ever leaving a half written file behind.
 *
 * The file is written under a temporary name, flushed to the disk, and only
 * then renamed over the target. A rename inside one directory is atomic on
 * every filesystem this runs on, so a reader sees either the old job or the new
 * one. Writing in place would leave a truncated file behind after a power cut,
 * and a truncated job file is a month of measurement nobody can get back.
 */
export async function saveJob(
  job: Job,
  path: string,
  options: SaveOptions = {},
): Promise<void> {
  const text = JSON.stringify(jobToJson(job), null, options.pretty === false ? 0 : 2);
  await mkdir(dirname(path), { recursive: true });

  if (options.backup !== false && (await fileExists(path))) {
    await copyFile(path, `${path}.bak`);
  }

  const temporary = tempPathFor(path);
  const handle = await open(temporary, 'w');
  try {
    await handle.writeFile(`${text}\n`, 'utf8');
    await handle.sync();
  } finally {
    await handle.close();
  }
  try {
    await rename(temporary, path);
  } catch (error) {
    await unlink(temporary).catch(() => undefined);
    throw error;
  }
}

export async function loadJob(path: string): Promise<Job> {
  let text: string;
  try {
    text = await readFile(path, 'utf8');
  } catch {
    throw new BadInputError('no job file at that path', { path });
  }
  return parseJob(text, path);
}

/**
 * A file that is not JSON at all and a file that is JSON but not a job need
 * different answers, because the first is usually the wrong path and the second
 * is usually a version mismatch.
 */
export function parseJob(text: string, path = '<memory>'): Job {
  let parsed: unknown;
  try {
    parsed = JSON.parse(text) as unknown;
  } catch {
    throw new BadInputError('the job file is not valid json', { path });
  }
  try {
    return jobFromJson(parsed);
  } catch (error) {
    if (error instanceof CwError) {
      throw new BadInputError(`${path}: ${error.message}`, { ...error.details, path });
    }
    throw error;
  }
}

export async function fileExists(path: string): Promise<boolean> {
  try {
    await access(path, constants.F_OK);
    return true;
  } catch {
    return false;
  }
}

/** Put the job back the way the last save found it. */
export async function restoreBackup(path: string): Promise<void> {
  const backup = `${path}.bak`;
  if (!(await fileExists(backup))) {
    throw new BadInputError('there is no backup to restore', { path: backup });
  }
  await copyFile(backup, path);
}

/**
 * Save, then read the file straight back and check the job still values the
 * same. Used by the CLI before it overwrites a month of work, because a codec
 * that has quietly stopped round tripping is silent until somebody reopens the
 * file weeks later.
 */
export async function saveAndVerify(job: Job, path: string): Promise<void> {
  await saveJob(job, path);
  const again = await loadJob(path);
  if (again.records.count() !== job.records.count()) {
    throw new CwError('internal', 'the job did not read back the way it was written', {
      path,
      wrote: job.records.count(),
      read: again.records.count(),
    });
  }
}
