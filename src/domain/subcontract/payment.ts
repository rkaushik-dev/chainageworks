import { BadInputError } from '../../errors.js';
import type { Money } from '../money/money.js';
import { maxMoney, subtractMoney, zeroMoney } from '../money/money.js';
import type { Percent } from '../money/percent.js';
import { applyPercent, percent } from '../money/percent.js';
import type { SiteDate } from '../time/siteDate.js';
import { addDays, compareSiteDates, formatSiteDate } from '../time/siteDate.js';

/**
 * The payment terms of a subcontract.
 *
 * The two notice periods are what the whole thing turns on. The payer has so
 * many days from the due date to say what it thinks the work is worth, and so
 * many more to say it intends to pay less. Miss either and the amount applied
 * for becomes the amount payable, whatever anybody thinks the work was worth.
 */
export interface PaymentTerms {
  /** Days from the application to the payment falling due. */
  readonly dueDays: number;
  /** Days from the due date to serve the payment notice. */
  readonly noticeDays: number;
  /** Days before the final date for payment to serve a pay less notice. */
  readonly payLessDays: number;
  /** Days from the due date to the final date for payment. */
  readonly finalDays: number;
  readonly retention: Percent;
}

export const DEFAULT_TERMS: PaymentTerms = {
  dueDays: 30,
  noticeDays: 5,
  payLessDays: 7,
  finalDays: 45,
  retention: percent(5),
};

export function buildPaymentTerms(input: Partial<PaymentTerms> = {}): PaymentTerms {
  const terms = { ...DEFAULT_TERMS, ...input };
  for (const [name, value] of [
    ['dueDays', terms.dueDays],
    ['noticeDays', terms.noticeDays],
    ['payLessDays', terms.payLessDays],
    ['finalDays', terms.finalDays],
  ] as const) {
    if (!Number.isInteger(value) || value < 0) {
      throw new BadInputError(`${name} is a whole number of days`, { value });
    }
  }
  if (terms.finalDays < terms.payLessDays) {
    throw new BadInputError('the pay less window cannot start before the due date', {
      finalDays: terms.finalDays,
      payLessDays: terms.payLessDays,
    });
  }
  return terms;
}

export interface PaymentApplication {
  readonly id: string;
  readonly subcontractId: string;
  readonly periodNumber: number;
  readonly appliedOn: SiteDate;
  /** The cumulative gross the subcontractor says it has earned. */
  readonly gross: Money;
  readonly previouslyPaid: Money;
}

export interface PaymentNotice {
  readonly applicationId: string;
  readonly servedOn: SiteDate;
  /** What the payer says the work is worth, gross and cumulative. */
  readonly valued: Money;
}

export interface PayLessNotice {
  readonly applicationId: string;
  readonly servedOn: SiteDate;
  /** What the payer intends to pay, as an amount for this application. */
  readonly amount: Money;
  readonly reason: string;
}

export interface PaymentDates {
  readonly dueOn: SiteDate;
  readonly noticeBy: SiteDate;
  readonly finalDateForPayment: SiteDate;
  readonly payLessBy: SiteDate;
}

export function paymentDates(
  application: PaymentApplication,
  terms: PaymentTerms,
): PaymentDates {
  const dueOn = addDays(application.appliedOn, terms.dueDays);
  const finalDateForPayment = addDays(dueOn, terms.finalDays);
  return {
    dueOn,
    noticeBy: addDays(dueOn, terms.noticeDays),
    finalDateForPayment,
    payLessBy: addDays(finalDateForPayment, -terms.payLessDays),
  };
}

export type NotifiedBasis = 'payment_notice' | 'pay_less_notice' | 'application_by_default';

export interface PaymentPosition {
  readonly dates: PaymentDates;
  readonly basis: NotifiedBasis;
  /** The cumulative figure the notified sum is worked out from. */
  readonly notifiedGross: Money;
  readonly retention: Money;
  readonly netOfRetention: Money;
  readonly payable: Money;
  /** True when the payer lost the argument by missing a notice. */
  readonly byDefault: boolean;
}

/**
 * What is actually payable on an application.
 *
 * The order is the order the statute puts it in, and it is not the order people
 * expect. A pay less notice served in time beats a payment notice; a payment
 * notice served in time beats the application; and an application beats
 * everything when neither notice arrived, however wrong the figure is. That
 * last rule is the one that costs money, so it is the one the report names.
 */
export function paymentPosition(
  application: PaymentApplication,
  terms: PaymentTerms,
  notice: PaymentNotice | null,
  payLess: PayLessNotice | null,
): PaymentPosition {
  const dates = paymentDates(application, terms);
  const currency = application.gross.currency;

  const noticeInTime =
    notice !== null && compareSiteDates(notice.servedOn, dates.noticeBy) <= 0;
  const payLessInTime =
    payLess !== null && compareSiteDates(payLess.servedOn, dates.payLessBy) <= 0;

  const notifiedGross = noticeInTime ? notice.valued : application.gross;
  const basis: NotifiedBasis = payLessInTime
    ? 'pay_less_notice'
    : noticeInTime
      ? 'payment_notice'
      : 'application_by_default';

  const retention = applyPercent(notifiedGross, terms.retention);
  const netOfRetention = subtractMoney(notifiedGross, retention);
  const fromNotices = subtractMoney(netOfRetention, application.previouslyPaid);
  const payable = payLessInTime ? payLess.amount : maxMoney(fromNotices, zeroMoney(currency));

  return {
    dates,
    basis,
    notifiedGross,
    retention,
    netOfRetention,
    payable,
    byDefault: basis === 'application_by_default',
  };
}

/** Applications the payer has not answered and is running out of time on. */
export function noticesDue(
  applications: readonly PaymentApplication[],
  terms: PaymentTerms,
  served: readonly PaymentNotice[],
  asAt: SiteDate,
): PaymentApplication[] {
  const answered = new Set(served.map((n) => n.applicationId));
  return applications.filter((application) => {
    if (answered.has(application.id)) return false;
    const dates = paymentDates(application, terms);
    return compareSiteDates(dates.noticeBy, asAt) >= 0;
  });
}

/** Applications where the notice window has already closed unanswered. */
export function defaultedApplications(
  applications: readonly PaymentApplication[],
  terms: PaymentTerms,
  served: readonly PaymentNotice[],
  asAt: SiteDate,
): PaymentApplication[] {
  const answered = new Set(served.map((n) => n.applicationId));
  return applications.filter((application) => {
    if (answered.has(application.id)) return false;
    const dates = paymentDates(application, terms);
    return compareSiteDates(dates.noticeBy, asAt) < 0;
  });
}

export function describePosition(position: PaymentPosition): string {
  const how =
    position.basis === 'application_by_default'
      ? 'the application, no notice served in time'
      : position.basis.replace(/_/g, ' ');
  return `payable on ${how}, final date ${formatSiteDate(position.dates.finalDateForPayment)}`;
}
