import { describe, expect, it } from 'vitest';
import { BadInputError } from '../../../src/errors.js';
import {
  configFromEnv,
  DEFAULT_CONFIG,
  defaultConfig,
  describeConfig,
  LOG_LEVELS,
  mergeConfig,
  parseConfig,
  shouldLog,
} from '../../../src/config/config.js';

describe('defaultConfig', () => {
  it('carries a working set of defaults', () => {
    const config = defaultConfig();
    expect(config.jobFile).toBe('chainageworks.json');
    expect(config.http).toEqual({ host: '127.0.0.1', port: 8730 });
    expect(config.logLevel).toBe('info');
    expect(config.autoSave).toBe(true);
    expect(config.strictReconciliation).toBe(false);
  });

  it('hands back a copy, so a caller cannot edit the defaults', () => {
    const config = defaultConfig();
    config.http.port = 9999;
    expect(DEFAULT_CONFIG.http.port).toBe(8730);
  });

  it('listens on the loopback address, not on everything', () => {
    expect(DEFAULT_CONFIG.http.host).toBe('127.0.0.1');
  });
});

describe('parseConfig', () => {
  it('accepts the defaults', () => {
    expect(parseConfig(DEFAULT_CONFIG)).toEqual(DEFAULT_CONFIG);
  });

  it('names the field that is wrong', () => {
    expect(() => parseConfig({ ...DEFAULT_CONFIG, jobFile: '' })).toThrow(
      /jobFile: a job file path is needed/,
    );
    expect(() => parseConfig({ ...DEFAULT_CONFIG, http: { host: 'x', port: 70_000 } })).toThrow(
      /http.port/,
    );
    expect(() => parseConfig({ ...DEFAULT_CONFIG, logLevel: 'chatty' })).toThrow(/logLevel/);
  });

  it('refuses a port that is not a whole number', () => {
    expect(() => parseConfig({ ...DEFAULT_CONFIG, http: { host: 'x', port: 80.5 } })).toThrow(
      BadInputError,
    );
  });

  it('refuses something that is not a config at all', () => {
    expect(() => parseConfig(null)).toThrow(BadInputError);
    expect(() => parseConfig({})).toThrow(BadInputError);
  });
});

describe('mergeConfig', () => {
  it('takes the override and keeps the rest', () => {
    const merged = mergeConfig(DEFAULT_CONFIG, { jobFile: 'other.json' });
    expect(merged.jobFile).toBe('other.json');
    expect(merged.http.port).toBe(8730);
  });

  it('merges the http block a field at a time', () => {
    const merged = mergeConfig(DEFAULT_CONFIG, { http: { port: 9000 } });
    expect(merged.http).toEqual({ host: '127.0.0.1', port: 9000 });
  });

  it('validates the result', () => {
    expect(() => mergeConfig(DEFAULT_CONFIG, { http: { port: -1 } })).toThrow(/http.port/);
  });
});

describe('configFromEnv', () => {
  it('keeps the defaults for an empty environment', () => {
    expect(configFromEnv({})).toEqual(DEFAULT_CONFIG);
  });

  it('reads every variable it knows', () => {
    const config = configFromEnv({
      CHAINAGEWORKS_JOB_FILE: '/srv/a1/job.json',
      CHAINAGEWORKS_HOST: '0.0.0.0',
      CHAINAGEWORKS_PORT: '9000',
      CHAINAGEWORKS_LOG_LEVEL: 'debug',
      CHAINAGEWORKS_AUTO_SAVE: 'off',
      CHAINAGEWORKS_STRICT: 'yes',
    });
    expect(config.jobFile).toBe('/srv/a1/job.json');
    expect(config.http).toEqual({ host: '0.0.0.0', port: 9000 });
    expect(config.logLevel).toBe('debug');
    expect(config.autoSave).toBe(false);
    expect(config.strictReconciliation).toBe(true);
  });

  it('reads a flag in any of the usual spellings', () => {
    for (const yes of ['1', 'true', 'YES', 'on']) {
      expect(configFromEnv({ CHAINAGEWORKS_STRICT: yes }).strictReconciliation).toBe(true);
    }
    for (const no of ['0', 'false', 'NO', 'off']) {
      expect(configFromEnv({ CHAINAGEWORKS_STRICT: no }).strictReconciliation).toBe(false);
    }
  });

  it('treats an empty variable as absent', () => {
    expect(configFromEnv({ CHAINAGEWORKS_PORT: '', CHAINAGEWORKS_STRICT: '' })).toEqual(
      DEFAULT_CONFIG,
    );
  });

  it('names the variable when it cannot read it', () => {
    expect(() => configFromEnv({ CHAINAGEWORKS_PORT: 'eight thousand' })).toThrow(
      /CHAINAGEWORKS_PORT is not a whole number/,
    );
    expect(() => configFromEnv({ CHAINAGEWORKS_STRICT: 'perhaps' })).toThrow(
      /CHAINAGEWORKS_STRICT is not a yes or a no/,
    );
    expect(() => configFromEnv({ CHAINAGEWORKS_LOG_LEVEL: 'chatty' })).toThrow(
      /CHAINAGEWORKS_LOG_LEVEL is not a level/,
    );
  });

  it('layers over a base other than the defaults', () => {
    const base = mergeConfig(DEFAULT_CONFIG, { jobFile: 'base.json', logLevel: 'warn' });
    const config = configFromEnv({ CHAINAGEWORKS_PORT: '9100' }, base);
    expect(config.jobFile).toBe('base.json');
    expect(config.logLevel).toBe('warn');
    expect(config.http.port).toBe(9100);
  });
});

describe('shouldLog', () => {
  it('lets through everything at or under the level', () => {
    const debug = mergeConfig(DEFAULT_CONFIG, { logLevel: 'debug' });
    expect(LOG_LEVELS.filter((l) => l !== 'silent').every((l) => shouldLog(debug, l))).toBe(
      true,
    );
  });

  it('stops at the level that is set', () => {
    const warn = mergeConfig(DEFAULT_CONFIG, { logLevel: 'warn' });
    expect(shouldLog(warn, 'error')).toBe(true);
    expect(shouldLog(warn, 'warn')).toBe(true);
    expect(shouldLog(warn, 'info')).toBe(false);
    expect(shouldLog(warn, 'debug')).toBe(false);
  });

  it('says nothing at all when it is silent', () => {
    const silent = mergeConfig(DEFAULT_CONFIG, { logLevel: 'silent' });
    expect(shouldLog(silent, 'error')).toBe(false);
  });
});

describe('describeConfig', () => {
  it('reads as a settings block', () => {
    expect(describeConfig(DEFAULT_CONFIG).split('\n')).toEqual([
      'job file            chainageworks.json',
      'http                127.0.0.1:8730',
      'log level           info',
      'auto save           on',
      'strict reconcile    off',
    ]);
  });
});
