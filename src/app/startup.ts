import { formatMoney } from '../domain/money/money.js';
import type { Config } from '../config/config.js';
import { diagnose, isHealthy } from './doctor.js';
import type { Service } from './service.js';

export interface Startup {
  readonly lines: readonly string[];
  /** True when the job has an error the server should not serve over. */
  readonly refuses: boolean;
}

/**
 * What the server says about itself before it takes a request.
 *
 * Printed to stderr rather than logged, because the only reader is whoever
 * just typed the command and they are looking at a terminal. A structured log
 * line here would be read by nobody and parsed by nothing.
 */
export function startupReport(service: Service, config: Config): Startup {
  const job = service.job;
  const diagnosis = diagnose(job);
  const blockers = diagnosis.blockers;

  const lines = [
    `chainageworks serving ${config.jobFile}`,
    `job          ${job.settings.id} ${job.settings.name}`,
    `contract sum ${formatMoney(job.settings.contractSum)}`,
    `packages     ${String(job.packages.count())}`,
    `records      ${String(job.records.count())}`,
    `listening    ${config.http.host}:${String(config.http.port)}`,
  ];

  if (blockers.length > 0) {
    lines.push(`blockers     ${blockers.join('; ')}`);
  }
  if (diagnosis.errors > 0) {
    lines.push(`errors       ${String(diagnosis.errors)}, run cw doctor`);
  }
  if (diagnosis.warnings > 0) {
    lines.push(`warnings     ${String(diagnosis.warnings)}`);
  }

  /**
   * A job with a blocker cannot value anything, so the server refuses rather
   * than answering five hundreds all afternoon. A job with a dangling
   * reference still serves: the numbers it gives are wrong in one place and
   * right everywhere else, and refusing the whole thing helps nobody.
   */
  return { lines, refuses: blockers.length > 0 || !canValue(service) };
}

function canValue(service: Service): boolean {
  return service.job.billRevisions.count() > 0 && service.job.packages.count() > 0;
}

/** The one line a supervisor watching stderr needs to see. */
export function startupSummary(startup: Startup): string {
  return startup.refuses ? 'refusing to serve, the job cannot be valued' : 'ready';
}

export function isSound(service: Service): boolean {
  return isHealthy(diagnose(service.job));
}
