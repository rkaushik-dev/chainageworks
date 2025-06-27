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
import type { Service } from '../app/service.js';
import {
  cashFlowFor,
  certificateFor,
  contextOf,
  createService,
  curveFor,
  defectsFor,
  delayFor,
  loadApplications,
  loadDiary,
  loadHolidays,
  loadIndices,
  loadNcrs,
  loadNotices,
  loadPayLessNotices,
  outputFor,
  loadRecords,
  loadResourceReturns,
  loadSignOffs,
  paymentsFor,
  periodOf,
  progressFor,
  scheduleOf,
  stripFor,
} from '../app/service.js';
import type { ImportReport } from '../ingest/report.js';
import { formatImportReport, hasErrors, rowsAccepted, rowsRejected } from '../ingest/report.js';
import {
  certificateMarkdown,
  progressMarkdown,
  subcontractMarkdown,
} from '../reporting/format/markdown.js';
import {
  renderCashFlow,
  renderCertificate,
  renderCurve,
  renderProgressReport,
  renderReconciliation,
} from '../reporting/format/text.js';
import { renderDefectsReport } from '../reporting/defectsReport.js';
import { renderDelayReport } from '../reporting/delayReport.js';
import { renderSubcontractReport } from '../reporting/subcontractReport.js';
import { renderOutputReport } from '../reporting/outputReport.js';
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
  readonly readText: (path: string) => Promise<string>;
  readonly writeText: (path: string, text: string) => Promise<void>;
  readonly out: (text: string) => void;
  readonly err: (text: string) => void;
  readonly env: Record<string, string | undefined>;
}

interface GlobalOptions {
  readonly file?: string;
  readonly json?: boolean;
  readonly markdown?: boolean;
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

function importSummary<T>(report: ImportReport<T>): Record<string, unknown> {
  return {
    rowsRead: report.rowsRead,
    accepted: rowsAccepted(report),
    rejected: rowsRejected(report),
    issues: report.issues,
  };
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
    .option('--markdown', 'print markdown rather than plain text')
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

  /**
   * Every report on a valuation is the same four steps: load the job, build the
   * report at that number, say so when the contract fixes no such term, and
   * print either the text or the summary. Written out one command at a time
   * they drifted, and the two that could answer null did it differently.
   */
  function valuationCommand<T>(
    name: string,
    description: string,
    build: (service: Service, valuation: number) => T | null,
    render: (report: T) => string,
    summarise: (report: T) => unknown,
    absent: string,
    markdown?: (report: T) => string,
  ): void {
    program
      .command(`${name} <valuation>`)
      .description(description)
      .action(async (valuation: string) => {
        const service = await serviceFor(deps, globals());
        const report = build(service, Number(valuation));
        if (report === null) {
          emit(deps, globals(), absent, null);
          return;
        }
        const wantsMarkdown = globals().markdown === true && markdown !== undefined;
        const text = wantsMarkdown ? markdown(report) : render(report);
        emit(deps, globals(), text, summarise(report));
      });
  }

  valuationCommand(
    'progress',
    'the progress report for a valuation',
    progressFor,
    renderProgressReport,
    (report) => report.period.number,
    'no such valuation',
    progressMarkdown,
  );

  valuationCommand(
    'certificate',
    'the payment certificate for a valuation',
    certificateFor,
    renderCertificate,
    (certificate) => certificate.period.number,
    'no such valuation',
    certificateMarkdown,
  );

  program
    .command('strip <package> <valuation>')
    .description('where each step of a package has got to')
    .action(async (packageId: string, valuation: string) => {
      const service = await serviceFor(deps, globals());
      const map = stripFor(service, packageId, Number(valuation));
      const heading = `${map.packageId}, ${describePeriod(periodOf(service, Number(valuation)))}`;
      emit(deps, globals(), `${heading}\n${renderStrip(map)}`, map.packageId);
    });

  valuationCommand(
    'delay',
    'the extension of time position at a valuation',
    delayFor,
    renderDelayReport,
    (report) => report.daysLate,
    'the contract fixes no completion date',
  );

  valuationCommand(
    'defects',
    'the defects position and what it blocks',
    defectsFor,
    renderDefectsReport,
    (report) => report.summary.outstanding,
    'the contract fixes no defects period',
  );

  valuationCommand(
    'output',
    'what the job produced for the hours it booked',
    outputFor,
    renderOutputReport,
    (report) => report.totals.labourHours,
    'no returns have been made',
  );

  valuationCommand(
    'payments',
    'the subcontract payment position and the notice diary',
    paymentsFor,
    renderSubcontractReport,
    (report) => report.positions.length,
    'no orders have been placed',
    subcontractMarkdown,
  );

  program
    .command('cash')
    .description('what the certificates will bring in and when')
    .option('-n, --periods <count>', 'how many periods', '12')
    .option('--late <periods>', 'periods the payer runs behind', '0')
    .action(async (options: { periods: string; late: string }) => {
      const service = await serviceFor(deps, globals());
      const points = cashFlowFor(service, Number(options.periods), Number(options.late));
      emit(deps, globals(), renderCashFlow(points), points.length);
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

  /**
   * Every import is the same three steps: read the file, hand it to the
   * service, and save if the job changed and auto save is on. Writing them out
   * one at a time invited a command that quietly forgot the save.
   */
  function importCommand<T>(
    name: string,
    description: string,
    load: (service: Service, text: string, options: { partial: boolean }) => ImportReport<T>,
    what: string,
  ): void {
    program
      .command(`import-${name} <file>`)
      .description(description)
      .option('--partial', 'take the rows that parsed even when others did not')
      .action(async (file: string, options: { partial?: boolean }) => {
        const service = await serviceFor(deps, globals());
        const report = load(service, await deps.readText(file), {
          partial: options.partial === true,
        });
        if (!hasErrors(report) || options.partial === true) {
          if (service.config.autoSave) {
            await deps.saveJob(service.job, service.config.jobFile);
          }
        }
        emit(deps, globals(), formatImportReport(report, what), importSummary(report));
      });
  }

  importCommand('records', 'load a survey book export', loadRecords, 'records');
  importCommand('sign-offs', 'load an inspection register export', loadSignOffs, 'sign offs');
  importCommand('ncrs', 'load a non conformance register export', loadNcrs, 'reports');
  importCommand('diary', 'load the standdowns out of a site diary', loadDiary, 'diary lines');
  importCommand('holidays', 'load the contract holiday list', loadHolidays, 'holidays');
  importCommand(
    'applications',
    'load a subcontract payment application schedule',
    loadApplications,
    'applications',
  );
  importCommand('notices', 'load the payment notices served', loadNotices, 'notices');
  importCommand('indices', 'load a published cost index bulletin', loadIndices, 'index series');
  importCommand(
    'returns',
    'load a labour and plant resource return export',
    loadResourceReturns,
    'resource returns',
  );
  importCommand(
    'pay-less',
    'load the pay less notices served',
    loadPayLessNotices,
    'pay less notices',
  );

  /**
   * Write the month's pack out as a set of markdown files.
   *
   * One file a report rather than one long document, because the three go to
   * three different people. The client gets the certificate, the project
   * manager gets the progress report, and only the commercial team ever wants
   * the subcontract position.
   */
  program
    .command('export <valuation> <directory>')
    .description('write the valuation pack out as markdown')
    .action(async (valuation: string, directory: string) => {
      const service = await serviceFor(deps, globals());
      const number = Number(valuation);
      const written: string[] = [];
      const files: readonly [string, string][] = [
        ['progress.md', progressMarkdown(progressFor(service, number))],
        ['certificate.md', certificateMarkdown(certificateFor(service, number))],
        ['payments.md', subcontractMarkdown(paymentsFor(service, number))],
      ];
      for (const [name, text] of files) {
        const path = `${directory}/${name}`;
        await deps.writeText(path, text);
        written.push(path);
      }
      emit(deps, globals(), written.join('\n'), written);
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
