import { describe, expect, it } from 'vitest';
import { formatItemCode } from '../../../src/domain/bill/itemCode.js';
import { moneyMajor } from '../../../src/domain/money/money.js';
import { percent } from '../../../src/domain/money/percent.js';
import {
  buildFinalAccount,
  DEFAULT_TREND_THRESHOLD,
  describeLine,
  movingLines,
} from '../../../src/valuation/finalAccount.js';
import { d, range, sampleContext, sampleRecords } from '../../support/contextFixtures.js';

const context = sampleContext();
const cutOff = d('2025-03-31');

const lineFor = (code: string, account = buildFinalAccount(context, cutOff)) =>
  account.lines.find((l) => formatItemCode(l.code) === code)!;

describe('buildFinalAccount', () => {
  const account = buildFinalAccount(context, cutOff);

  it('gives a line per bill item', () => {
    expect(account.lines).toHaveLength(8);
    expect(moneyMajor(account.billTotal)).toBe(312_190);
  });

  it('forecasts an item nobody has started at its billed value', () => {
    const line = lineFor('F624.1');
    expect(line.percentComplete).toBe(0);
    expect(line.extrapolated).toBe(false);
    expect(moneyMajor(line.forecast)).toBe(moneyMajor(line.billed));
    expect(line.movement.minor).toBe(0);
  });

  it('reports what has been measured against each item', () => {
    const line = lineFor('E423.1');
    expect(moneyMajor(line.measured)).toBe(28_324);
  });

  it('weights an item across every package that carries it', () => {
    // WP-01 is a third through and WP-02 has not started, so the item as a
    // whole is well short of either figure.
    const line = lineFor('E423.1');
    expect(line.percentComplete).toBe(18.96);
    expect(line.extrapolated).toBe(false);
  });

  it('forecasts on the trend once an item is far enough through', () => {
    const account = buildFinalAccount(context, cutOff, { trendThreshold: percent(10) });
    const line = lineFor('E423.1', account);
    expect(line.extrapolated).toBe(true);
    expect(moneyMajor(line.forecast)).toBe(149_401.32);
  });

  it('never forecasts below what has already been measured', () => {
    for (const line of buildFinalAccount(context, cutOff).lines) {
      expect(line.forecast.minor).toBeGreaterThanOrEqual(line.measured.minor);
    }
  });

  it('adds the lines to the outturn and reports the movement', () => {
    const sum = account.lines.reduce((total, l) => total + l.forecast.minor, 0);
    expect(account.forecastOutturn.minor).toBe(sum);
    expect(account.movement.minor).toBe(
      account.forecastOutturn.minor - account.billTotal.minor,
    );
  });

  it('leaves a provisional item on the bill however much was measured', () => {
    const line = lineFor('E423.9');
    expect(line.extrapolated).toBe(false);
    expect(moneyMajor(line.forecast)).toBe(14_000);
  });
});

describe('the trend threshold', () => {
  it('is a quarter by default', () => {
    expect(DEFAULT_TREND_THRESHOLD).toEqual(percent(25));
  });

  it('stops extrapolating when the threshold is above the progress', () => {
    const account = buildFinalAccount(context, cutOff, { trendThreshold: percent(60) });
    expect(lineFor('E423.1', account).extrapolated).toBe(false);
    expect(moneyMajor(lineFor('E423.1', account).forecast)).toBe(60_140);
  });

  it('starts extrapolating when the threshold is lowered', () => {
    const early = buildFinalAccount(context, d('2025-03-10'), { trendThreshold: percent(1) });
    expect(lineFor('E423.1', early).extrapolated).toBe(true);
  });
});

describe('the account moves as the job does', () => {
  it('forecasts nothing measured before anything happened', () => {
    const account = buildFinalAccount(context, d('2025-02-28'));
    expect(account.measuredToDate.minor).toBe(0);
    expect(account.movement.minor).toBe(0);
    expect(moneyMajor(account.forecastOutturn)).toBe(312_190);
  });

  it('does not move at all while every line is still on the bill', () => {
    expect(movingLines(buildFinalAccount(context, cutOff))).toEqual([]);
  });

  it('moves the outturn up when a heavily measured item starts to trend', () => {
    const heavy = sampleContext({
      records: sampleRecords().map((r) =>
        r.id === 'SR-02' ? { ...r, extent: range('12+000', '12+600') } : r,
      ),
    });
    const account = buildFinalAccount(heavy, cutOff, { trendThreshold: percent(10) });
    expect(account.forecastOutturn.minor).toBeGreaterThan(account.billTotal.minor);
  });

  it('lists the moving lines worst first', () => {
    const moving = movingLines(
      buildFinalAccount(context, cutOff, { trendThreshold: percent(10) }),
    );
    expect(moving.length).toBeGreaterThan(0);
    for (let i = 1; i < moving.length; i += 1) {
      expect(Math.abs(moving[i - 1]!.movement.minor)).toBeGreaterThanOrEqual(
        Math.abs(moving[i]!.movement.minor),
      );
    }
  });
});

describe('describeLine', () => {
  it('says which side of the threshold a line fell', () => {
    const trending = buildFinalAccount(context, cutOff, { trendThreshold: percent(10) });
    expect(describeLine(lineFor('E423.1', trending))).toBe(
      'E423.1 18.96% complete, on the trend',
    );
    expect(describeLine(lineFor('F624.1'))).toBe('F624.1 0% complete, on the bill');
  });
});
