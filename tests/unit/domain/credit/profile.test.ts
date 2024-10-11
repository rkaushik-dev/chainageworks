import { describe, expect, it } from 'vitest';
import { NotFoundError } from '../../../../src/errors.js';
import type { StepProgress } from '../../../../src/domain/credit/profile.js';
import {
  blockedSteps,
  buildCreditProfile,
  cumulativeWeight,
  describeProfile,
  earnedFraction,
  findStep,
  isProfileComplete,
  nextStep,
  requireStep,
  stepIndex,
} from '../../../../src/domain/credit/profile.js';
import { percent, percentValue } from '../../../../src/domain/money/percent.js';

const pipeRun = buildCreditProfile({
  id: 'pipe-run',
  name: 'pipe laying',
  steps: [
    { id: 'excavate', name: 'excavate trench', weight: percent(25) },
    { id: 'bed', name: 'lay bedding', weight: percent(10), requiresPrevious: true },
    { id: 'lay', name: 'lay pipe', weight: percent(30), requiresPrevious: true },
    {
      id: 'backfill',
      name: 'backfill and compact',
      weight: percent(25),
      requiresPrevious: true,
    },
    { id: 'test', name: 'pressure test', weight: percent(10), requiresPrevious: true },
  ],
});

const loose = buildCreditProfile({
  id: 'loose',
  name: 'independent steps',
  steps: [
    { id: 'a', name: 'a', weight: percent(40) },
    { id: 'b', name: 'b', weight: percent(60) },
  ],
});

const done = (...ids: string[]): StepProgress[] =>
  ids.map((stepId) => ({ stepId, fraction: percent(100) }));

describe('buildCreditProfile', () => {
  it('keeps the steps in the order given', () => {
    expect(pipeRun.steps.map((s) => s.id)).toEqual([
      'excavate',
      'bed',
      'lay',
      'backfill',
      'test',
    ]);
  });

  it('defaults requiresPrevious to false', () => {
    expect(pipeRun.steps[0]?.requiresPrevious).toBe(false);
    expect(pipeRun.steps[1]?.requiresPrevious).toBe(true);
  });

  it('refuses a profile with no steps', () => {
    expect(() => buildCreditProfile({ id: 'x', name: 'x', steps: [] })).toThrow(
      /at least one step/,
    );
  });

  it('refuses weights that do not add to a hundred', () => {
    expect(() =>
      buildCreditProfile({
        id: 'x',
        name: 'x',
        steps: [
          { id: 'a', name: 'a', weight: percent(40) },
          { id: 'b', name: 'b', weight: percent(40) },
        ],
      }),
    ).toThrow(/add to 100 percent/);
  });

  it('refuses weights that add to more than a hundred', () => {
    expect(() =>
      buildCreditProfile({
        id: 'x',
        name: 'x',
        steps: [
          { id: 'a', name: 'a', weight: percent(60) },
          { id: 'b', name: 'b', weight: percent(60) },
        ],
      }),
    ).toThrow(/add to 100 percent/);
  });

  it('accepts awkward weights that still add up exactly', () => {
    const thirds = buildCreditProfile({
      id: 'thirds',
      name: 'thirds',
      steps: [
        { id: 'a', name: 'a', weight: percent(33.33) },
        { id: 'b', name: 'b', weight: percent(33.33) },
        { id: 'c', name: 'c', weight: percent(33.34) },
      ],
    });
    expect(thirds.steps).toHaveLength(3);
  });

  it('refuses two steps sharing an id', () => {
    expect(() =>
      buildCreditProfile({
        id: 'x',
        name: 'x',
        steps: [
          { id: 'a', name: 'first', weight: percent(50) },
          { id: 'a', name: 'second', weight: percent(50) },
        ],
      }),
    ).toThrow(/share an id/);
  });

  it('refuses a negative weight', () => {
    expect(() =>
      buildCreditProfile({
        id: 'x',
        name: 'x',
        steps: [
          { id: 'a', name: 'a', weight: percent(-10) },
          { id: 'b', name: 'b', weight: percent(110) },
        ],
      }),
    ).toThrow(/cannot be negative/);
  });
});

describe('looking steps up', () => {
  it('finds by id and reports the position', () => {
    expect(findStep(pipeRun, 'lay')?.name).toBe('lay pipe');
    expect(stepIndex(pipeRun, 'lay')).toBe(2);
    expect(stepIndex(pipeRun, 'nope')).toBe(-1);
    expect(findStep(pipeRun, 'nope')).toBeNull();
  });

  it('throws from the strict lookup', () => {
    expect(() => requireStep(pipeRun, 'nope')).toThrow(NotFoundError);
  });

  it('adds up the weight to a step', () => {
    expect(percentValue(cumulativeWeight(pipeRun, 'excavate'))).toBe(25);
    expect(percentValue(cumulativeWeight(pipeRun, 'lay'))).toBe(65);
    expect(percentValue(cumulativeWeight(pipeRun, 'test'))).toBe(100);
    expect(() => cumulativeWeight(pipeRun, 'nope')).toThrow(NotFoundError);
  });
});

describe('earnedFraction', () => {
  it('earns nothing for nothing done', () => {
    expect(percentValue(earnedFraction(pipeRun, []))).toBe(0);
  });

  it('earns the first step on its own', () => {
    expect(percentValue(earnedFraction(pipeRun, done('excavate')))).toBe(25);
  });

  it('adds the steps as they are finished in order', () => {
    expect(percentValue(earnedFraction(pipeRun, done('excavate', 'bed')))).toBe(35);
    expect(percentValue(earnedFraction(pipeRun, done('excavate', 'bed', 'lay')))).toBe(65);
  });

  it('earns everything when every step is finished', () => {
    expect(
      percentValue(earnedFraction(pipeRun, done('excavate', 'bed', 'lay', 'backfill', 'test'))),
    ).toBe(100);
  });

  it('credits part of a step', () => {
    const progress: StepProgress[] = [{ stepId: 'excavate', fraction: percent(60) }];
    expect(percentValue(earnedFraction(pipeRun, progress))).toBe(15);
  });

  it('holds back a step whose predecessors are short', () => {
    const progress: StepProgress[] = [
      { stepId: 'excavate', fraction: percent(50) },
      { stepId: 'backfill', fraction: percent(100) },
    ];
    expect(percentValue(earnedFraction(pipeRun, progress))).toBe(12.5);
    expect(blockedSteps(pipeRun, progress).map((s) => s.id)).toEqual(['backfill']);
  });

  it('lets an independent step earn out of order', () => {
    expect(percentValue(earnedFraction(loose, done('b')))).toBe(60);
    expect(blockedSteps(loose, done('b'))).toEqual([]);
  });

  it('clamps a fraction over a hundred rather than overpaying', () => {
    const progress: StepProgress[] = [{ stepId: 'excavate', fraction: percent(150) }];
    expect(percentValue(earnedFraction(pipeRun, progress))).toBe(25);
  });

  it('treats a negative fraction as nothing', () => {
    const progress: StepProgress[] = [{ stepId: 'excavate', fraction: percent(-20) }];
    expect(percentValue(earnedFraction(pipeRun, progress))).toBe(0);
  });

  it('ignores progress against a step the profile does not have', () => {
    const progress: StepProgress[] = [{ stepId: 'paint', fraction: percent(100) }];
    expect(percentValue(earnedFraction(pipeRun, progress))).toBe(0);
  });
});

describe('nextStep and isProfileComplete', () => {
  it('names the first unfinished step', () => {
    expect(nextStep(pipeRun, [])?.id).toBe('excavate');
    expect(nextStep(pipeRun, done('excavate'))?.id).toBe('bed');
  });

  it('names a partly done step as the next one', () => {
    expect(nextStep(pipeRun, [{ stepId: 'excavate', fraction: percent(90) }])?.id).toBe(
      'excavate',
    );
  });

  it('reports nothing left when everything is done', () => {
    const all = done('excavate', 'bed', 'lay', 'backfill', 'test');
    expect(nextStep(pipeRun, all)).toBeNull();
    expect(isProfileComplete(pipeRun, all)).toBe(true);
    expect(isProfileComplete(pipeRun, done('excavate'))).toBe(false);
  });
});

describe('describeProfile', () => {
  it('lists the steps and their weights', () => {
    expect(describeProfile(loose)).toBe('independent steps: a 40%, b 60%');
  });
});
