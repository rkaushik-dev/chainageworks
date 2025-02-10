import { BadInputError } from '../../src/errors.js';
import type { CliDeps } from '../../src/cli/program.js';
import { runCli } from '../../src/cli/program.js';
import type { Job } from '../../src/store/job.js';
import { sampleJob } from './jobFixtures.js';

export interface CliRun {
  readonly code: number;
  readonly out: string;
  readonly err: string;
  readonly saved: readonly { path: string; job: Job }[];
}

export interface HarnessOptions {
  readonly job?: Job;
  readonly env?: Record<string, string | undefined>;
  /** Fail the load, to exercise the error path. */
  readonly loadError?: Error;
  /** Files the import commands can read, keyed by the path they are given. */
  readonly files?: Record<string, string>;
}

/**
 * Run the CLI with the disk and the terminal replaced.
 *
 * Everything the command wrote is collected, so a test can assert on the exit
 * code and the text without a subprocess.
 */
export async function runCommand(
  argv: readonly string[],
  options: HarnessOptions = {},
): Promise<CliRun> {
  const out: string[] = [];
  const err: string[] = [];
  const saved: { path: string; job: Job }[] = [];
  const job = options.job ?? sampleJob();

  const deps: CliDeps = {
    loadJob: (path: string) => {
      if (options.loadError !== undefined) return Promise.reject(options.loadError);
      void path;
      return Promise.resolve(job);
    },
    saveJob: (toSave: Job, path: string) => {
      saved.push({ path, job: toSave });
      return Promise.resolve();
    },
    readText: (path: string) => {
      const text = options.files?.[path];
      if (text === undefined) {
        return Promise.reject(new BadInputError('no such file', { path }));
      }
      return Promise.resolve(text);
    },
    out: (text: string) => out.push(text),
    err: (text: string) => err.push(text),
    env: options.env ?? {},
  };

  const code = await runCli(argv, deps);
  return { code, out: out.join('\n'), err: err.join('\n'), saved };
}
