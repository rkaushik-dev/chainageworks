import { describe, expect, it } from 'vitest';
import type { ResourceReturn } from '../../../src/domain/resource/return.js';
import { buildResourceReturn } from '../../../src/domain/resource/return.js';
import {
  behindAllowance,
  buildAllowances,
  describeStepOutput,
  nothingToShow,
  packageOutput,
} from '../../../src/progress/productivity.js';
import { d, sampleContext } from '../../support/contextFixtures.js';
import { samplePackages } from '../../support/packageFixtures.js';

const context = sampleContext();
const wp01 = samplePackages()[0]!;
const cutOff = d('2025-03-25');

function gang(count: number, hours: number, kind: 'working' | 'standing' | 'idle' = 'working') {
  return { resourceId: 'GANG-EARTH', description: 'earthworks gang', count, hours, kind };
}

const returns: ResourceReturn[] = [
  buildResourceReturn({
    id: 'RR-01',
    packageId: 'WP-01',
    stepId: 'strip',
    date: d('2025-03-03'),
    labour: [gang(6, 8)],
    plant: [{ resourceId: 'PL-D6', description: 'dozer', count: 1, hours: 8, kind: 'working' }],
    returnedBy: 'A Kerrigan',
  }),
  buildResourceReturn({
    id: 'RR-02',
    packageId: 'WP-01',
    stepId: 'strip',
    date: d('2025-03-04'),
    labour: [gang(6, 8)],
    returnedBy: 'A Kerrigan',
  }),
  buildResourceReturn({
    id: 'RR-03',
    packageId: 'WP-01',
    stepId: 'excavate',
    date: d('2025-03-10'),
    labour: [gang(4, 10)],
    plant: [{ resourceId: 'PL-360', description: '360', count: 1, hours: 4, kind: 'standing' }],
    returnedBy: 'A Kerrigan',
  }),
  buildResourceReturn({
    id: 'RR-04',
    packageId: 'WP-01',
    date: d('2025-03-11'),
    labour: [gang(2, 8)],
    returnedBy: 'A Kerrigan',
    note: 'general site tidy, no step named',
  }),
  buildResourceReturn({
    id: 'RR-05',
    packageId: 'WP-01',
    stepId: 'landscaping',
    date: d('2025-03-12'),
    labour: [gang(1, 6)],
    returnedBy: 'A Kerrigan',
    note: 'a step this rule of credit does not carry',
  }),
  buildResourceReturn({
    id: 'RR-06',
    packageId: 'WP-02',
    stepId: 'strip',
    date: d('2025-03-13'),
    labour: [gang(8, 8)],
    returnedBy: 'D Illingworth',
  }),
  buildResourceReturn({
    id: 'RR-07',
    packageId: 'WP-01',
    stepId: 'strip',
    date: d('2025-04-02'),
    labour: [gang(6, 8)],
    returnedBy: 'A Kerrigan',
    note: 'after the cut off',
  }),
];

const allowances = buildAllowances([
  { stepId: 'strip', metresPerHour: 10 },
  { stepId: 'excavate', metresPerHour: 12 },
]);

describe('buildAllowances', () => {
  it('takes a clean set', () => {
    expect(allowances).toHaveLength(2);
  });

  it('refuses two allowances against one step', () => {
    expect(() =>
      buildAllowances([
        { stepId: 'strip', metresPerHour: 10 },
        { stepId: 'strip', metresPerHour: 12 },
      ]),
    ).toThrow(/two output allowances/);
  });

  it('refuses an allowance of nothing an hour', () => {
    expect(() => buildAllowances([{ stepId: 'strip', metresPerHour: 0 }])).toThrow(
      /more than nothing/,
    );
  });
});

describe('packageOutput', () => {
  const output = packageOutput(context, wp01, returns, cutOff, allowances);
  const step = (id: string) => output.steps.find((s) => s.stepId === id)!;

  it('reports one line for every step of the rule of credit', () => {
    expect(output.steps.map((s) => s.stepId)).toEqual(['strip', 'excavate', 'place', 'trim']);
  });

  it('sets what was built against the hours it took', () => {
    expect(step('strip').builtMetres).toBe(800);
    expect(step('strip').labourHours).toBe(96);
    expect(step('strip').achieved).toBeCloseTo(8.3333, 4);
  });

  it('counts a step behind its allowance', () => {
    expect(step('strip').ratio).toBeCloseTo(0.83333, 5);
    expect(behindAllowance(output).map((s) => s.stepId)).toEqual(['strip']);
  });

  it('counts a step ahead of its allowance', () => {
    expect(step('excavate').achieved).toBe(15);
    expect(step('excavate').ratio).toBe(1.25);
  });

  it('leaves the ratio null where no allowance was set', () => {
    expect(step('place').allowed).toBeNull();
    expect(step('place').ratio).toBeNull();
  });

  it('leaves everything null on a step nobody booked hours to', () => {
    expect(step('place').labourHours).toBe(0);
    expect(step('place').achieved).toBeNull();
  });

  it('keeps the plant hours apart from the labour hours', () => {
    expect(step('strip').plantHours).toBe(8);
    expect(step('excavate').plantHours).toBe(4);
    expect(output.plantHours).toBe(12);
  });

  it('counts the standing plant as lost time', () => {
    expect(step('excavate').lostHours).toBe(4);
    expect(step('strip').lostHours).toBe(0);
  });

  it('holds back the hours nobody booked to a step', () => {
    expect(output.unallocatedHours).toBe(22);
  });

  it('leaves out another package and anything after the cut off', () => {
    expect(output.labourHours).toBe(158);
  });

  it('is empty of hours when no returns have been made', () => {
    const empty = packageOutput(context, wp01, [], cutOff, allowances);
    expect(empty.labourHours).toBe(0);
    expect(empty.steps.every((s) => s.achieved === null)).toBe(true);
  });
});

describe('nothingToShow', () => {
  it('names a step with hours booked and nothing built', () => {
    const output = packageOutput(
      context,
      wp01,
      [
        buildResourceReturn({
          id: 'RR-08',
          packageId: 'WP-01',
          stepId: 'trim',
          date: d('2025-03-14'),
          labour: [gang(4, 8)],
          returnedBy: 'A Kerrigan',
        }),
      ],
      cutOff,
      allowances,
    );
    expect(nothingToShow(output).map((s) => s.stepId)).toEqual(['trim']);
  });

  it('names nothing when every booked step built something', () => {
    expect(nothingToShow(packageOutput(context, wp01, [returns[0]!], cutOff))).toEqual([]);
  });
});

describe('describeStepOutput', () => {
  const output = packageOutput(context, wp01, returns, cutOff, allowances);

  it('says the rate and how it compares', () => {
    expect(describeStepOutput(output.steps[0]!)).toBe(
      'strip, 8.33 metres an hour, 83 percent of the allowance',
    );
  });

  it('says so when no allowance was set', () => {
    expect(describeStepOutput(output.steps[1]!)).toContain('125 percent');
  });

  it('says so when no hours were booked', () => {
    expect(describeStepOutput(output.steps[2]!)).toBe('place, no hours booked');
  });
});
