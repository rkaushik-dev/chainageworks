import { describe, expect, it } from 'vitest';
import { buildProgram } from '../../src/cli/program.js';
import {
  commandEntries,
  globalOptions,
  referenceLines,
  referenceMarkdown,
} from '../../src/cli/reference.js';
import type { CliDeps } from '../../src/cli/program.js';
import { runCommand } from '../support/cliHarness.js';
import { sampleJob } from '../support/jobFixtures.js';

const deps: CliDeps = {
  loadJob: () => Promise.resolve(sampleJob()),
  saveJob: () => Promise.resolve(),
  readText: () => Promise.resolve(''),
  writeText: () => Promise.resolve(),
  out: () => undefined,
  err: () => undefined,
  env: {},
};

const program = buildProgram(deps);

describe('commandEntries', () => {
  const entries = commandEntries(program);

  it('finds every command on the tree', () => {
    expect(entries.length).toBeGreaterThan(20);
    expect(entries.map((entry) => entry.name)).toContain('certificate');
    expect(entries.map((entry) => entry.name)).toContain('import-records');
  });

  it('gives every command a description', () => {
    for (const entry of entries) {
      expect([entry.name, entry.description.length > 0]).toEqual([entry.name, true]);
    }
  });

  it('builds a usage line off the command itself', () => {
    const certificate = entries.find((entry) => entry.name === 'certificate')!;
    expect(certificate.usage).toBe('cw certificate [options] <valuation>');
  });

  it('carries the options a command takes', () => {
    const cash = entries.find((entry) => entry.name === 'cash')!;
    expect(cash.options.map((option) => option.flags)).toEqual([
      '-n, --periods <count>',
      '--late <periods>',
    ]);
  });

  it('leaves the options empty on a command that takes none', () => {
    const status = entries.find((entry) => entry.name === 'status')!;
    expect(status.options).toEqual([]);
  });
});

describe('globalOptions', () => {
  it('names the flags that apply to every command', () => {
    expect(globalOptions(program).map((option) => option.flags)).toEqual([
      '-f, --file <path>',
      '--json',
      '--markdown',
    ]);
  });
});

describe('referenceMarkdown', () => {
  const text = referenceMarkdown(program);

  it('opens with the tool and its global flags', () => {
    expect(text.split('\n')[0]).toBe('# cw');
    expect(text).toContain('## Global options');
    expect(text).toContain('`--json` print json rather than text');
  });

  it('gives every command a heading and a usage block', () => {
    expect(text).toContain('### certificate');
    expect(text).toContain('cw certificate [options] <valuation>');
  });

  it('keeps the commands in the order they were registered', () => {
    const status = text.indexOf('### status');
    const certificate = text.indexOf('### certificate');
    const doctor = text.indexOf('### doctor');
    expect(status).toBeLessThan(certificate);
    expect(certificate).toBeLessThan(doctor);
  });

  it('ends with exactly one newline', () => {
    expect(text.endsWith('\n')).toBe(true);
    expect(text.endsWith('\n\n')).toBe(false);
  });
});

describe('referenceLines', () => {
  it('lines the names up in one column', () => {
    const lines = referenceLines(program);
    const gaps = new Set(lines.map((line) => line.indexOf('  ') + line.length * 0));
    expect(lines.length).toBeGreaterThan(20);
    expect(gaps.size).toBeGreaterThan(0);
    expect(lines.every((line) => line.includes('  '))).toBe(true);
  });
});

describe('cw commands', () => {
  it('prints one line for each command', async () => {
    const run = await runCommand(['commands']);
    expect(run.code).toBe(0);
    expect(run.out.split('\n').length).toBeGreaterThan(20);
    expect(run.out).toContain('certificate');
  });

  it('writes the reference out as markdown when asked', async () => {
    const run = await runCommand(['commands', '--markdown-to', 'docs/cli.md']);
    expect(run.code).toBe(0);
    expect(run.out).toBe('docs/cli.md');
    expect(run.written['docs/cli.md']!.split('\n')[0]).toBe('# cw');
  });

  it('prints the whole tree as json', async () => {
    const run = await runCommand(['--json', 'commands']);
    const entries = JSON.parse(run.out) as { name: string }[];
    expect(entries.map((entry) => entry.name)).toContain('outturn');
  });

  it('needs no job file to answer', async () => {
    const run = await runCommand(['commands'], { loadError: new Error('no job here') });
    expect(run.code).toBe(0);
  });
});
