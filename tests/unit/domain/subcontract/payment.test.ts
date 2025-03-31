import { describe, expect, it } from 'vitest';
import { moneyFromMajor, moneyMajor, zeroMoney } from '../../../../src/domain/money/money.js';
import { percent } from '../../../../src/domain/money/percent.js';
import { formatSiteDate, parseSiteDate } from '../../../../src/domain/time/siteDate.js';
import type { PaymentApplication } from '../../../../src/domain/subcontract/payment.js';
import {
  buildPaymentTerms,
  DEFAULT_TERMS,
  defaultedApplications,
  describePosition,
  noticesDue,
  paymentDates,
  paymentPosition,
} from '../../../../src/domain/subcontract/payment.js';

const d = parseSiteDate;
const gbp = (major: number) => moneyFromMajor(major, 'GBP');
const terms = buildPaymentTerms();

const application: PaymentApplication = {
  id: 'SA-03',
  subcontractId: 'SC-EARTHWORKS',
  periodNumber: 3,
  appliedOn: d('2025-03-25'),
  gross: gbp(120_000),
  previouslyPaid: gbp(60_000),
};

describe('buildPaymentTerms', () => {
  it('carries the usual statutory windows', () => {
    expect(terms).toEqual(DEFAULT_TERMS);
    expect(terms.dueDays).toBe(30);
    expect(terms.noticeDays).toBe(5);
  });

  it('takes terms of its own', () => {
    expect(buildPaymentTerms({ dueDays: 21, retention: percent(3) }).dueDays).toBe(21);
  });

  it('refuses a window that is not a whole number of days', () => {
    expect(() => buildPaymentTerms({ dueDays: -1 })).toThrow(/whole number of days/);
    expect(() => buildPaymentTerms({ noticeDays: 1.5 })).toThrow(/whole number of days/);
  });

  it('refuses a pay less window that opens before the due date', () => {
    expect(() => buildPaymentTerms({ finalDays: 3, payLessDays: 7 })).toThrow(
      /cannot start before the due date/,
    );
  });
});

describe('paymentDates', () => {
  it('counts every window off the application', () => {
    const dates = paymentDates(application, terms);
    expect(formatSiteDate(dates.dueOn)).toBe('2025-04-24');
    expect(formatSiteDate(dates.noticeBy)).toBe('2025-04-29');
    expect(formatSiteDate(dates.finalDateForPayment)).toBe('2025-06-08');
    expect(formatSiteDate(dates.payLessBy)).toBe('2025-06-01');
  });
});

describe('paymentPosition', () => {
  it('takes the payment notice when it was served in time', () => {
    const position = paymentPosition(
      application,
      terms,
      { applicationId: 'SA-03', servedOn: d('2025-04-28'), valued: gbp(100_000) },
      null,
    );
    expect(position.basis).toBe('payment_notice');
    expect(moneyMajor(position.notifiedGross)).toBe(100_000);
    expect(moneyMajor(position.retention)).toBe(5000);
    expect(moneyMajor(position.payable)).toBe(35_000);
    expect(position.byDefault).toBe(false);
  });

  it('falls back to the application when no notice was served at all', () => {
    const position = paymentPosition(application, terms, null, null);
    expect(position.basis).toBe('application_by_default');
    expect(position.byDefault).toBe(true);
    expect(moneyMajor(position.notifiedGross)).toBe(120_000);
    expect(moneyMajor(position.payable)).toBe(54_000);
  });

  it('falls back to the application when the notice was a day late', () => {
    const position = paymentPosition(
      application,
      terms,
      { applicationId: 'SA-03', servedOn: d('2025-04-30'), valued: gbp(100_000) },
      null,
    );
    expect(position.byDefault).toBe(true);
    expect(moneyMajor(position.notifiedGross)).toBe(120_000);
  });

  it('takes a notice served on the last day of the window', () => {
    const position = paymentPosition(
      application,
      terms,
      { applicationId: 'SA-03', servedOn: d('2025-04-29'), valued: gbp(100_000) },
      null,
    );
    expect(position.basis).toBe('payment_notice');
  });

  it('lets a pay less notice beat the payment notice', () => {
    const position = paymentPosition(
      application,
      terms,
      { applicationId: 'SA-03', servedOn: d('2025-04-28'), valued: gbp(100_000) },
      {
        applicationId: 'SA-03',
        servedOn: d('2025-05-30'),
        amount: gbp(20_000),
        reason: 'defective work at the headwall',
      },
    );
    expect(position.basis).toBe('pay_less_notice');
    expect(moneyMajor(position.payable)).toBe(20_000);
  });

  it('ignores a pay less notice served too late', () => {
    const position = paymentPosition(
      application,
      terms,
      { applicationId: 'SA-03', servedOn: d('2025-04-28'), valued: gbp(100_000) },
      {
        applicationId: 'SA-03',
        servedOn: d('2025-06-05'),
        amount: gbp(20_000),
        reason: 'too late',
      },
    );
    expect(position.basis).toBe('payment_notice');
    expect(moneyMajor(position.payable)).toBe(35_000);
  });

  it('never pays a negative amount when the valuation has gone backwards', () => {
    const position = paymentPosition(
      { ...application, previouslyPaid: gbp(200_000) },
      terms,
      { applicationId: 'SA-03', servedOn: d('2025-04-28'), valued: gbp(100_000) },
      null,
    );
    expect(position.payable).toEqual(zeroMoney('GBP'));
  });

  it('holds no retention when the subcontract holds none', () => {
    const position = paymentPosition(
      application,
      buildPaymentTerms({ retention: percent(0) }),
      null,
      null,
    );
    expect(position.retention.minor).toBe(0);
    expect(moneyMajor(position.payable)).toBe(60_000);
  });
});

describe('the notice diary', () => {
  const applications = [
    application,
    { ...application, id: 'SA-04', periodNumber: 4, appliedOn: d('2025-04-25') },
  ];
  const served = [{ applicationId: 'SA-03', servedOn: d('2025-04-28'), valued: gbp(100_000) }];

  it('lists what still needs a notice', () => {
    expect(noticesDue(applications, terms, served, d('2025-05-20')).map((a) => a.id)).toEqual([
      'SA-04',
    ]);
  });

  it('lists nothing once every window has closed', () => {
    expect(noticesDue(applications, terms, served, d('2025-07-01'))).toEqual([]);
  });

  it('names the applications that defaulted to the applied for sum', () => {
    expect(
      defaultedApplications(applications, terms, served, d('2025-07-01')).map((a) => a.id),
    ).toEqual(['SA-04']);
    expect(defaultedApplications(applications, terms, served, d('2025-05-01'))).toEqual([]);
  });
});

describe('describePosition', () => {
  it('says how the figure was arrived at', () => {
    const byDefault = paymentPosition(application, terms, null, null);
    expect(describePosition(byDefault)).toBe(
      'payable on the application, no notice served in time, final date 2025-06-08',
    );
  });

  it('names the notice when there was one', () => {
    const noticed = paymentPosition(
      application,
      terms,
      { applicationId: 'SA-03', servedOn: d('2025-04-28'), valued: gbp(100_000) },
      null,
    );
    expect(describePosition(noticed)).toContain('payment notice');
  });
});
