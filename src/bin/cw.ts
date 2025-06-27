#!/usr/bin/env node
import { mkdir, readFile, writeFile } from 'node:fs/promises';
import { dirname } from 'node:path';
import { runCli } from '../cli/program.js';
import { loadJob, saveJob } from '../store/fileStore.js';

/**
 * The real wiring, and nothing else.
 *
 * Everything here is a one line adapter onto a node builtin, which is what
 * keeps the command surface itself testable: the file below has no branches to
 * get wrong.
 */
const code = await runCli(process.argv.slice(2), {
  loadJob,
  saveJob: (job, path) => saveJob(job, path),
  readText: (path) => readFile(path, 'utf8'),
  writeText: async (path, text) => {
    await mkdir(dirname(path), { recursive: true });
    await writeFile(path, text, 'utf8');
  },
  out: (text) => {
    process.stdout.write(`${text}\n`);
  },
  err: (text) => {
    process.stderr.write(`${text}\n`);
  },
  env: process.env,
});

process.exitCode = code;
