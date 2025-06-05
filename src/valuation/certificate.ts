import { BadInputError } from '../errors.js';
import { billAsAt, revisionAsAt } from '../domain/bill/revision.js';
import type { Money } from '../domain/money/money.js';
import { addMoney, subtractMoney, sumMoney, zeroMoney } from '../domain/money/money.js';
import type { Percent } from '../domain/money/percent.js';
import type { ProgressContext } from '../progress/context.js';
import type { PackageEarning } from '../progress/earn.js';
import { earnAll } from '../progress/earn.js';
import type { AdjustmentTerms, PriceAdjustment } from '../domain/fluctuation/adjustment.js';
import { noAdjustment, priceAdjustment } from '../domain/fluctuation/adjustment.js';
import type { IndexSeries } from '../domain/fluctuation/index.js';
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
  /** The fluctuation clause, when the contract carries one. */
  readonly adjustment?: AdjustmentTerms | null;
  readonly indices?: readonly IndexSeries[];
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
  /** What the fluctuation clause added to, or took off, the permanent work. */
  readonly priceAdjustment: PriceAdjustment;
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
  /**
   * The formula moves the permanent work and nothing else.
   *
   * Materials on site are deliberately left out. They are paid at what the
   * supplier actually invoiced, which already carries whatever the market did,
   * and running them through an index as well pays the same movement twice.
   */
  const permanentWork = addMoney(measuredWork, daywork);
  const terms = input.adjustment ?? null;
  const adjustment =
    terms === null
      ? noAdjustment(permanentWork, asAt)
      : priceAdjustment(terms, input.indices ?? [], permanentWork, asAt);

  const gross = addMoney(
    addMoney(addMoney(measuredWork, materialsOnSite), daywork),
    adjustment.amount,
  );

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
    priceAdjustment: adjustment,
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

/** True when the certificate rests on an index figure that may still move. */
export function restsOnProvisionalIndex(certificate: Certificate): boolean {
  return certificate.priceAdjustment.provisional;
}

/** Certificates in a series that will have to be reworked when indices firm up. */
export function provisionalCertificates(series: readonly Certificate[]): Certificate[] {
  return series.filter(restsOnProvisionalIndex);
}
