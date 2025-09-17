import {
  certificateMarkdown,
  progressMarkdown,
  subcontractMarkdown,
} from '../reporting/format/markdown.js';
import { renderCertificate } from '../reporting/format/text.js';
import { renderDefectsReport } from '../reporting/defectsReport.js';
import { renderDelayReport } from '../reporting/delayReport.js';
import { renderOutputReport } from '../reporting/outputReport.js';
import { renderOutturn } from '../reporting/outturnReport.js';
import { renderRiskReport } from '../reporting/riskReport.js';
import { renderVariationReport } from '../reporting/variationReport.js';
import { certificateCsv, certificateTotalsCsv, outturnCsv } from '../reporting/csvExport.js';
import { formatSiteDate } from '../domain/time/siteDate.js';
import { diagnose, isHealthy } from './doctor.js';
import type { Diagnosis } from './doctor.js';
import type { Service } from './service.js';
import {
  certificateFor,
  defectsFor,
  delayFor,
  outputFor,
  outturnFor,
  paymentsFor,
  periodOf,
  progressFor,
  risksFor,
  variationsFor,
} from './service.js';

export interface PackFile {
  readonly name: string;
  readonly text: string;
}

export interface MonthEndPack {
  readonly valuation: number;
  readonly cutOff: string;
  readonly diagnosis: Diagnosis;
  readonly files: readonly PackFile[];
  /** The amount due this month, which is the one figure everyone asks for. */
  readonly amountDue: string;
}

/**
 * Everything a month end produces, in one call.
 *
 * The order matters and it is the order a commercial team actually works in.
 * The job is checked first, because a report built on a dangling reference is
 * worse than no report. The certificate comes next because everything else is
 * read against it. The forward looking reports come last, because nobody
 * plans next month before this month's number is agreed.
 *
 * The check does not stop the pack. A job with a broken reference still has to
 * produce a valuation on the twenty fifth, and a tool that refuses would be
 * put down and the valuation done in a spreadsheet. The findings ride along so
 * the reader knows what to distrust.
 */
export function buildMonthEndPack(service: Service, valuation: number): MonthEndPack {
  const diagnosis = diagnose(service.job);
  const period = periodOf(service, valuation);
  const certificate = certificateFor(service, valuation);
  const progress = progressFor(service, valuation);
  const worst = service.config.reports.worstLines;

  const files: PackFile[] = [
    { name: 'certificate.md', text: certificateMarkdown(certificate) },
    { name: 'certificate.txt', text: renderCertificate(certificate) },
    { name: 'certificate.csv', text: certificateCsv(certificate) },
    { name: 'certificate-totals.csv', text: certificateTotalsCsv(certificate) },
    { name: 'progress.md', text: progressMarkdown(progress) },
    { name: 'payments.md', text: subcontractMarkdown(paymentsFor(service, valuation)) },
    { name: 'variations.txt', text: renderVariationReport(variationsFor(service, valuation)) },
    { name: 'outturn.txt', text: renderOutturn(outturnFor(service, valuation), worst) },
    { name: 'outturn.csv', text: outturnCsv(outturnFor(service, valuation)) },
    { name: 'output.txt', text: renderOutputReport(outputFor(service, valuation)) },
    { name: 'risks.txt', text: renderRiskReport(risksFor(service, valuation)) },
  ];

  const delay = delayFor(service, valuation);
  if (delay !== null) files.push({ name: 'delay.txt', text: renderDelayReport(delay) });

  const defects = defectsFor(service, valuation);
  if (defects !== null) {
    files.push({ name: 'defects.txt', text: renderDefectsReport(defects) });
  }

  return {
    valuation,
    cutOff: formatSiteDate(period.cutOff),
    diagnosis,
    files,
    amountDue: `${String(certificate.amountDue.minor / 100)}`,
  };
}

/** True when the pack was built on a job with nothing wrong with it. */
export function packIsSound(pack: MonthEndPack): boolean {
  return isHealthy(pack.diagnosis);
}

export function packFile(pack: MonthEndPack, name: string): PackFile | null {
  return pack.files.find((file) => file.name === name) ?? null;
}

/** A one line summary for whoever is running the month end. */
export function describePack(pack: MonthEndPack): string {
  const health = packIsSound(pack)
    ? 'the job checks out'
    : `${String(pack.diagnosis.errors)} errors on the job`;
  return (
    `valuation ${String(pack.valuation)} to ${pack.cutOff}, ` +
    `${String(pack.files.length)} files, ${health}`
  );
}
