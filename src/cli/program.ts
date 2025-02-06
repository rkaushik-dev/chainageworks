import { Command } from 'commander';
import { asCwError } from '../errors.js';
import type { Config } from '../config/config.js';
import { configFromEnv, describeConfig, mergeConfig } from '../config/config.js';
import { formatMoney } from '../domain/money/money.js';
import { formatChainageRange } from '../domain/chainage/range.js';
import { formatItemCode } from '../domain/bill/itemCode.js';
import { formatQuantity } from '../domain/quantity/quantity.js';
import { formatRate } from '../domain/money/rate.js';
import { billAsAt } from '../domain/bill/revision.js';
import { parseSiteDate } from '../domain/time/siteDate.js';
import {
  certificateFor,
  contextOf,
  createService,
  curveFor,
  periodOf,
  progressFor,
  scheduleOf,
  stripFor,
} from '../app/service.js';
import {
  renderCertificate,
  renderCurve,
  renderProgressReport,
  renderReconciliation,
} from '../reporting/format/text.js';
import { renderStrip } from '../reporting/stripMap.js';
import { renderPairs, renderTable } from '../reporting/format/table.js';
import type { Job } from '../store/job.js';
import { jobBlockers } from '../store/job.js';
import { describePeriod } from '../valuation/period.js';

/**
 * Everything the CLI touches that is not pure.
 *
 * Injected rather than imported so the whole command surface can be exercised
 * without a disk or a terminal. A CLI that can only be tested by running it is
 * a CLI whose error paths are never tested at all.
 */
export interface CliDeps {
  readonly loadJob: (path: string) => Promise<Job>;
  readonly saveJob: (job: Job, path: string) => Promise<void>;
  readonly out: (text: string) => void;
  readonly err: (text: string) => void;
  readonly env: Record<string, string | undefined>;
}

interface GlobalOptions {
  readonly file?: string;
  readonly json?: boolean;
}

function configFor(deps: CliDeps, options: GlobalOptions): Config {
  const base = configFromEnv(deps.env);
  return options.file === undefined ? base : mergeConfig(base, { jobFile: options.file });
}

async function serviceFor(deps: CliDeps, options: GlobalOptions) {
  const config = configFor(deps, options);
  return createService(await deps.loadJob(config.jobFile), config);
}

function emit(deps: CliDeps, options: GlobalOptions, text: string, value: unknown): void {
  deps.out(options.json === true ? JSON.stringify(value, null, 2) : text);
}

/**
 * Build the command tree.
 *
 * Commander is told not to exit the process or write to the console itself, so
 * a failure comes back as a thrown error this module turns into an exit code.
 */
export function buildProgram(deps: CliDeps): Command {
  const program = new Command();
  program
    .name('cw')
    .description('progress measurement and interim valuation for linear civil works')
    .option('-f, --file <path>', 'the job file to read')
    .option('--json', 'print json rather than text')
    .exitOverride()
    .configureOutput({
      writeOut: (text) => {
        deps.out(text.trimEnd());
      },
      writeErr: (text) => {
        deps.err(text.trimEnd());
      },
    });

  const globals = (): GlobalOptions => program.opts<GlobalOptions>();

  program
    .command('status')
    .description('what the job holds and what it is missing')
    .action(async () => {
      const service = await serviceFor(deps, globals());
      const job = service.job;
      const blockers = jobBlockers(job);
      const text = renderPairs([
        ['job', `${job.settings.id} ${job.settings.name}`],
        ['contract sum', formatMoney(job.settings.contractSum)],
        ['bill revisions', String(job.billRevisions.count())],
        ['packages', String(job.packages.count())],
        ['survey records', String(job.records.count())],
        ['sign offs', String(job.signOffs.count())],
        ['open reports', String(job.ncrs.where((n) => n.status === 'open').length)],
        ['blockers', blockers.length === 0 ? 'none' : blockers.join('; ')],
      ]);
      emit(deps, globals(), text, {
        id: job.settings.id,
        name: job.settings.name,
        blockers,
      });
    });

  program
    .command('config')
    .description('the settings this run would use')
    .action(() => {
      const config = configFor(deps, globals());
      emit(deps, globals(), describeConfig(config), config);
    });

  program
    .command('bill')
    .description('the bill in force on a date')
    .option('--as-at <date>', 'the date to price at')
    .action(async (options: { asAt?: string }) => {
      const service = await serviceFor(deps, globals());
      const asAt =
        options.asAt === undefined
          ? scheduleOf(service, 1).periods[0]!.cutOff
          : parseSiteDate(options.asAt);
      const bill = billAsAt(contextOf(service).billHistory, asAt);
      const text = renderTable(
        [
          { heading: 'code' },
          { heading: 'description', width: 44 },
          { heading: 'quantity', align: 'right' },
          { heading: 'rate', align: 'right' },
          { heading: 'kind' },
        ],
        bill.items.map((item) => [
          formatItemCode(item.code),
          item.description,
          formatQuantity(item.quantity),
          formatRate(item.rate),
          item.kind,
        ]),
      );
      emit(deps, globals(), text, bill.items.length);
    });

  program
    .command('packages')
    .description('the work package register')
    .action(async () => {
      const service = await serviceFor(deps, globals());
      const text = renderTable(
        [
          { heading: 'package' },
          { heading: 'section' },
          { heading: 'extent' },
          { heading: 'rule of credit' },
          { heading: 'items', align: 'right' },
        ],
        service.job.packages
          .all()
          .map((pkg) => [
            pkg.id,
            pkg.section,
            formatChainageRange(pkg.extent),
            pkg.profileId,
            String(pkg.allocations.length),
          ]),
      );
      emit(deps, globals(), text, service.job.packages.count());
    });

  program
    .command('progress <valuation>')
    .description('the progress report for a valuation')
    .action(async (valuation: string) => {
      const service = await serviceFor(deps, globals());
      const report = progressFor(service, Number(valuation));
      emit(deps, globals(), renderProgressReport(report), report.period.number);
    });

  program
    .command('certificate <valuation>')
    .description('the payment certificate for a valuation')
    .action(async (valuation: string) => {
      const service = await serviceFor(deps, globals());
      const certificate = certificateFor(service, Number(valuation));
      emit(deps, globals(), renderCertificate(certificate), certificate.period.number);
    });

  program
    .command('strip <package> <valuation>')
    .description('where each step of a package has got to')
    .action(async (packageId: string, valuation: string) => {
      const service = await serviceFor(deps, globals());
      const map = stripFor(service, packageId, Number(valuation));
      const heading = `${map.packageId}, ${describePeriod(periodOf(service, Number(valuation)))}`;
      emit(deps, globals(), `${heading}\n${renderStrip(map)}`, map.packageId);
    });

  program
    .command('curve')
    .description('planned against earned, period by period')
    .option('-n, --periods <count>', 'how many periods', '12')
    .action(async (options: { periods: string }) => {
      const service = await serviceFor(deps, globals());
      const points = curveFor(service, Number(options.periods));
      emit(deps, globals(), renderCurve(points), points.length);
    });

  program
    .command('reconcile')
    .description('the packages against the bill')
    .action(async () => {
      const service = await serviceFor(deps, globals());
      const report = progressFor(service, 1);
      emit(deps, globals(), renderReconciliation(report), report.reconciliation.length);
    });

  return program;
}

/** Run one command line and give back the exit code. */
export async function runCli(argv: readonly string[], deps: CliDeps): Promise<number> {
  const program = buildProgram(deps);
  try {
    await program.parseAsync([...argv], { from: 'user' });
    return 0;
  } catch (error) {
    const commanderCode = (error as { code?: string }).code;
    if (commanderCode !== undefined && commanderCode.startsWith('commander.')) {
      // Commander has already written the help or the complaint through the
      // output hooks above, so there is nothing more to say. Anything other
      // than help or a version is a misuse and gets a one.
      const informational =
        commanderCode === 'commander.helpDisplayed' ||
        commanderCode === 'commander.help' ||
        commanderCode === 'commander.version';
      return informational ? 0 : 1;
    }
    const problem = asCwError(error);
    deps.err(`${problem.code}: ${problem.message}`);
    return problem.code === 'internal' ? 2 : 1;
  }
}
