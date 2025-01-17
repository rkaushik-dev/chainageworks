import { z } from 'zod';
import { BadInputError } from '../errors.js';

export const LOG_LEVELS = ['silent', 'error', 'warn', 'info', 'debug'] as const;
export type LogLevel = (typeof LOG_LEVELS)[number];

/**
 * Everything the process needs that is not part of the contract.
 *
 * Kept apart from the job settings on purpose. A port number and a file path
 * belong to the machine the tool is running on; a retention percentage belongs
 * to the contract, and putting the two in one file makes it far too easy to
 * copy a colleague's setup and quietly inherit their retention terms.
 */
export const configSchema = z.object({
  jobFile: z.string().min(1, 'a job file path is needed'),
  http: z.object({
    host: z.string().min(1),
    port: z.number().int().min(0).max(65_535),
  }),
  logLevel: z.enum(LOG_LEVELS),
  /** Write the job file after every command that changes it. */
  autoSave: z.boolean(),
  /** Refuse to serve a valuation while the register does not reconcile. */
  strictReconciliation: z.boolean(),
});

export type Config = z.infer<typeof configSchema>;

export const DEFAULT_CONFIG: Config = {
  jobFile: 'chainageworks.json',
  http: { host: '127.0.0.1', port: 8730 },
  logLevel: 'info',
  autoSave: true,
  strictReconciliation: false,
};

export function defaultConfig(): Config {
  return structuredClone(DEFAULT_CONFIG);
}

export type ConfigOverrides = {
  readonly jobFile?: string;
  readonly http?: { readonly host?: string; readonly port?: number };
  readonly logLevel?: LogLevel;
  readonly autoSave?: boolean;
  readonly strictReconciliation?: boolean;
};

export function mergeConfig(base: Config, overrides: ConfigOverrides): Config {
  return parseConfig({
    jobFile: overrides.jobFile ?? base.jobFile,
    http: {
      host: overrides.http?.host ?? base.http.host,
      port: overrides.http?.port ?? base.http.port,
    },
    logLevel: overrides.logLevel ?? base.logLevel,
    autoSave: overrides.autoSave ?? base.autoSave,
    strictReconciliation: overrides.strictReconciliation ?? base.strictReconciliation,
  });
}

/**
 * Validate a config, turning zod's report into one of our errors.
 *
 * The message names the field, because the usual way this fails is a port
 * number arriving as a string out of an environment variable and the operator
 * needing to know which variable to fix.
 */
export function parseConfig(value: unknown): Config {
  const parsed = configSchema.safeParse(value);
  if (!parsed.success) {
    const first = parsed.error.issues[0];
    const field = first === undefined ? 'config' : first.path.join('.');
    throw new BadInputError(`${field}: ${first?.message ?? 'is not valid'}`, { field });
  }
  return parsed.data;
}

const ENV_PREFIX = 'CHAINAGEWORKS_';

function readFlag(raw: string | undefined, fallback: boolean, name: string): boolean {
  if (raw === undefined || raw === '') return fallback;
  const cleaned = raw.trim().toLowerCase();
  if (['1', 'true', 'yes', 'on'].includes(cleaned)) return true;
  if (['0', 'false', 'no', 'off'].includes(cleaned)) return false;
  throw new BadInputError(`${name} is not a yes or a no`, { value: raw });
}

function readPort(raw: string | undefined, fallback: number, name: string): number {
  if (raw === undefined || raw === '') return fallback;
  const value = Number(raw);
  if (!Number.isInteger(value)) {
    throw new BadInputError(`${name} is not a whole number`, { value: raw });
  }
  return value;
}

/** Read the environment over the defaults. Anything absent keeps its default. */
export function configFromEnv(
  env: Record<string, string | undefined>,
  base: Config = DEFAULT_CONFIG,
): Config {
  const level = env[`${ENV_PREFIX}LOG_LEVEL`];
  if (level !== undefined && !LOG_LEVELS.includes(level as LogLevel)) {
    throw new BadInputError(`${ENV_PREFIX}LOG_LEVEL is not a level this tool knows`, {
      value: level,
    });
  }
  const jobFile = env[`${ENV_PREFIX}JOB_FILE`];
  const host = env[`${ENV_PREFIX}HOST`];
  return mergeConfig(base, {
    jobFile: jobFile === undefined || jobFile === '' ? base.jobFile : jobFile,
    http: {
      host: host === undefined || host === '' ? base.http.host : host,
      port: readPort(env[`${ENV_PREFIX}PORT`], base.http.port, `${ENV_PREFIX}PORT`),
    },
    logLevel: level === undefined ? base.logLevel : (level as LogLevel),
    autoSave: readFlag(env[`${ENV_PREFIX}AUTO_SAVE`], base.autoSave, `${ENV_PREFIX}AUTO_SAVE`),
    strictReconciliation: readFlag(
      env[`${ENV_PREFIX}STRICT`],
      base.strictReconciliation,
      `${ENV_PREFIX}STRICT`,
    ),
  });
}

export function describeConfig(config: Config): string {
  return [
    `job file            ${config.jobFile}`,
    `http                ${config.http.host}:${String(config.http.port)}`,
    `log level           ${config.logLevel}`,
    `auto save           ${config.autoSave ? 'on' : 'off'}`,
    `strict reconcile    ${config.strictReconciliation ? 'on' : 'off'}`,
  ].join('\n');
}

const LEVEL_ORDER: Readonly<Record<LogLevel, number>> = {
  silent: 0,
  error: 1,
  warn: 2,
  info: 3,
  debug: 4,
};

export function shouldLog(config: Config, level: Exclude<LogLevel, 'silent'>): boolean {
  return LEVEL_ORDER[config.logLevel] >= LEVEL_ORDER[level];
}
