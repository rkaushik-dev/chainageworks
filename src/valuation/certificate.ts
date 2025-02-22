import { BadInputError } from '../errors.js';
import { billAsAt, revisionAsAt } from '../domain/bill/revision.js';
import type { Money } from '../domain/money/money.js';
import { addMoney, subtractMoney, sumMoney, zeroMoney } from '../domain/money/money.js';
import type { Percent } from '../domain/money/percent.js';
import type { ProgressContext } from '../progress/context.js';
import type { PackageEarning } from '../progress/earn.js';
import { earnAll } from '../progress/earn.js';
import type { DayworkSchedule } from '../domain/daywork/schedule.js';
import type { DayworkSheet } from '../domain/daywork/sheet.js';
import { dayworkValue } from '../domain/daywork/sheet.js';
import type { MaterialsOnSite } from './materials.js';
import { claimableMaterials, materialsValue } from './materials.js';
import type { ValuationPeriod } from './period.js';
import type { RetentionState, RetentionTerms } from './retention.js';
import { computeRetention } from './retention.js';
import type { SiteDate } from '../domain/time/siteDate.js';

export interface CertificateInput {
  readonly context: ProgressContext;
  readonly period: ValuationPeriod;
  readonly contractSum: Money;
  readonly retention: RetentionTerms;
  readonly materials?: readonly MaterialsOnSite[];
  readonly materialsAllowance?: Percent;
  readonly dayworkSchedule?: DayworkSchedule | null;
  readonly dayworkSheets?: readonly DayworkSheet[];
  /** The cumulative gross certified by the previous certificate. */
  readonly previouslyCertified: Money;
  readonly substantialCompletionOn?: SiteDate | null;
  readonly defectsCertificateOn?: SiteDate | null;
}

/**
 * An interim payment certificate.
 *
 * Everything on it is cumulative except the amount due, which is the only
 * figure that is a difference. That is the whole shape of interim payment: each
 * certificate values the works from the start and pays the movement, so a
 * mistake in one month corrects itself in the next rather than compounding.
 */
export interface Certificate {
  readonly period: ValuationPeriod;
  readonly billRevision: string;
  readonly earnings: readonly PackageEarning[];
  readonly measuredWork: Money;
  readonly materialsOnSite: Money;
  readonly materialsClaimed: readonly MaterialsOnSite[];
  /** Signed daywork, which is measured work that could not be measured. */
  readonly daywork: Money;
  readonly gross: Money;
  readonly retention: RetentionState;
  readonly netValuation: Money;
  readonly previouslyCertified: Money;
  readonly amountDue: Money;
}

export function buildCertificate(input: CertificateInput): Certificate {
  const asAt = input.period.cutOff;
  const bill = billAsAt(input.context.billHistory, asAt);
  if (input.previouslyCertified.currency !== bill.currency) {
    throw new BadInputError('the previous certificate is in another currency', {
      bill: bill.currency,
      previous: input.previouslyCertified.currency,
    });
  }

  const earnings = earnAll(input.context, asAt);
  const measuredWork = sumMoney(
    earnings.map((e) => e.earnedValue),
    bill.currency,
  );
  const materials = input.materials ?? [];
  const materialsOnSite = materialsValue(
    materials,
    asAt,
    bill.currency,
    input.materialsAllowance,
  );
  const schedule = input.dayworkSchedule ?? null;
  const daywork =
    schedule === null
      ? zeroMoney(bill.currency)
      : dayworkValue(schedule, input.dayworkSheets ?? [], asAt);
  const gross = addMoney(addMoney(measuredWork, materialsOnSite), daywork);

  const retention = computeRetention({
    grossValuation: gross,
    contractSum: input.contractSum,
    terms: input.retention,
    substantialCompletionOn: input.substantialCompletionOn ?? null,
    defectsCertificateOn: input.defectsCertificateOn ?? null,
    asAt,
  });

  const netValuation = subtractMoney(gross, retention.held);
  return {
    period: input.period,
    billRevision: revisionAsAt(input.context.billHistory, asAt).reference,
    earnings,
    measuredWork,
    materialsOnSite,
    materialsClaimed: claimableMaterials(materials, asAt),
    daywork,
    gross,
    retention,
    netValuation,
    previouslyCertified: input.previouslyCertified,
    amountDue: subtractMoney(netValuation, input.previouslyCertified),
  };
}

/** True when this month values less than the last one did. */
export function isNegativeCertificate(certificate: Certificate): boolean {
  return certificate.amountDue.minor < 0;
}

export function certifiedToDate(certificate: Certificate): Money {
  return certificate.netValuation;
}

/** Run a whole schedule, feeding each certificate the last one's net figure. */
export function certifySeries(
  periods: readonly ValuationPeriod[],
  base: Omit<CertificateInput, 'period' | 'previouslyCertified'>,
): Certificate[] {
  const out: Certificate[] = [];
  let previous = subtractMoney(base.contractSum, base.contractSum);
  for (const period of periods) {
    const certificate = buildCertificate({
      ...base,
      period,
      previouslyCertified: previous,
    });
    out.push(certificate);
    previous = certificate.netValuation;
  }
  return out;
}
