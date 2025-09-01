import type { Command } from 'commander';

export interface CommandEntry {
  readonly name: string;
  readonly usage: string;
  readonly description: string;
  readonly options: readonly { flags: string; description: string }[];
}

/**
 * The command tree, read off commander rather than kept beside it.
 *
 * A reference written by hand is a reference that stops matching the tool, and
 * this one is written by the same object the parser uses. A command added
 * without a description shows up here as an empty cell, which is a visible
 * gap rather than a silent omission.
 */
export function commandEntries(program: Command): CommandEntry[] {
  return program.commands.map((command) => ({
    name: command.name(),
    usage: `${program.name()} ${command.name()} ${command.usage()}`.trim(),
    description: command.description(),
    options: command.options.map((option) => ({
      flags: option.flags,
      description: option.description,
    })),
  }));
}

/** The global flags, which apply whichever command is run. */
export function globalOptions(program: Command): { flags: string; description: string }[] {
  return program.options.map((option) => ({
    flags: option.flags,
    description: option.description,
  }));
}

/**
 * The reference as markdown, in the order the commands were registered.
 *
 * Registration order rather than alphabetical, because the tree was built in
 * the order somebody uses it: load a job, look at it, value it, export it.
 * Sorting the list alphabetically would put the export commands first and the
 * import commands in the middle, which teaches the reader nothing.
 */
export function referenceMarkdown(program: Command): string {
  const lines: string[] = [
    `# ${program.name()}`,
    '',
    program.description(),
    '',
    '## Global options',
    '',
    ...globalOptions(program).map((option) => `- \`${option.flags}\` ${option.description}`),
    '',
    '## Commands',
    '',
  ];

  for (const entry of commandEntries(program)) {
    lines.push(`### ${entry.name}`, '', entry.description, '', '```', entry.usage, '```', '');
    if (entry.options.length > 0) {
      for (const option of entry.options) {
        lines.push(`- \`${option.flags}\` ${option.description}`);
      }
      lines.push('');
    }
  }
  return lines.join('\n').trimEnd() + '\n';
}

/** One line a command, which is what a terminal wants rather than a document. */
export function referenceLines(program: Command): string[] {
  const width = Math.max(...commandEntries(program).map((entry) => entry.name.length));
  return commandEntries(program).map(
    (entry) => `${entry.name.padEnd(width)}  ${entry.description}`,
  );
}
