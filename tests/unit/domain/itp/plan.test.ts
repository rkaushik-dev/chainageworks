import { describe, expect, it } from 'vitest';
import { NotFoundError } from '../../../../src/errors.js';
import { PIPE_RUN, SINGLE_STEP } from '../../../../src/domain/credit/catalogue.js';
import {
  buildInspectionPlan,
  describePoint,
  disciplines,
  findPoint,
  gatedSteps,
  holdPointsForStep,
  pointsForStep,
  requirePoint,
  witnessPointsForStep,
} from '../../../../src/domain/itp/plan.js';

const plan = buildInspectionPlan(
  {
    id: 'ITP-PIPE',
    name: 'pipe laying inspection and test plan',
    profileId: 'pipe-run',
    points: [
      {
        id: 'IP-01',
        name: 'trench formation level and width',
        type: 'hold',
        stepId: 'excavate',
        discipline: 'geotech',
        reference: 'spec clause 505',
      },
      {
        id: 'IP-02',
        name: 'bedding material grading',
        type: 'witness',
        stepId: 'bed',
      },
      {
        id: 'IP-03',
        name: 'pipe line and level before backfill',
        type: 'hold',
        stepId: 'lay',
      },
      {
        id: 'IP-04',
        name: 'compaction test certificates',
        type: 'review',
        stepId: 'backfill',
      },
      {
        id: 'IP-05',
        name: 'pressure test witnessed',
        type: 'hold',
        stepId: 'test',
      },
    ],
  },
  PIPE_RUN,
);

describe('buildInspectionPlan', () => {
  it('keeps the points it was given', () => {
    expect(plan.points).toHaveLength(5);
    expect(plan.points[0]?.reference).toBe('spec clause 505');
  });

  it('defaults the discipline to civil and the reference to nothing', () => {
    expect(plan.points[1]?.discipline).toBe('civil');
    expect(plan.points[1]?.reference).toBe('');
  });

  it('refuses a plan that names a different rule of credit', () => {
    expect(() =>
      buildInspectionPlan(
        { id: 'X', name: 'x', profileId: 'pipe-run', points: [] },
        SINGLE_STEP,
      ),
    ).toThrow(/different rule of credit/);
  });

  it('refuses two points sharing an id', () => {
    expect(() =>
      buildInspectionPlan(
        {
          id: 'X',
          name: 'x',
          profileId: 'pipe-run',
          points: [
            { id: 'IP-01', name: 'a', type: 'hold', stepId: 'excavate' },
            { id: 'IP-01', name: 'b', type: 'hold', stepId: 'lay' },
          ],
        },
        PIPE_RUN,
      ),
    ).toThrow(/share an id/);
  });

  it('refuses a point hung on a step the profile does not have', () => {
    expect(() =>
      buildInspectionPlan(
        {
          id: 'X',
          name: 'x',
          profileId: 'pipe-run',
          points: [{ id: 'IP-09', name: 'paint', type: 'hold', stepId: 'paint' }],
        },
        PIPE_RUN,
      ),
    ).toThrow(/a step the profile does not have/);
  });

  it('accepts a plan with no points at all', () => {
    const bare = buildInspectionPlan(
      { id: 'ITP-NONE', name: 'nothing to inspect', profileId: 'pipe-run', points: [] },
      PIPE_RUN,
    );
    expect(gatedSteps(bare)).toEqual([]);
  });
});

describe('looking points up', () => {
  it('finds by id', () => {
    expect(findPoint(plan, 'IP-03')?.name).toMatch(/line and level/);
    expect(findPoint(plan, 'IP-99')).toBeNull();
    expect(requirePoint(plan, 'IP-01').type).toBe('hold');
  });

  it('throws for an id nothing carries', () => {
    expect(() => requirePoint(plan, 'IP-99')).toThrow(NotFoundError);
  });

  it('collects every point on a step', () => {
    expect(pointsForStep(plan, 'excavate').map((p) => p.id)).toEqual(['IP-01']);
    expect(pointsForStep(plan, 'lay').map((p) => p.id)).toEqual(['IP-03']);
    expect(pointsForStep(plan, 'nothing')).toEqual([]);
  });
});

describe('point types', () => {
  it('counts only hold points as gates', () => {
    expect(holdPointsForStep(plan, 'excavate').map((p) => p.id)).toEqual(['IP-01']);
    expect(holdPointsForStep(plan, 'bed')).toEqual([]);
    expect(holdPointsForStep(plan, 'backfill')).toEqual([]);
  });

  it('reports witness points separately', () => {
    expect(witnessPointsForStep(plan, 'bed').map((p) => p.id)).toEqual(['IP-02']);
    expect(witnessPointsForStep(plan, 'excavate')).toEqual([]);
  });

  it('lists the steps a hold point stands on', () => {
    expect(gatedSteps(plan)).toEqual(['excavate', 'lay', 'test']);
  });

  it('lists the disciplines involved', () => {
    expect(disciplines(plan)).toEqual(['civil', 'geotech']);
  });
});

describe('describePoint', () => {
  it('reads like a plan row', () => {
    expect(describePoint(requirePoint(plan, 'IP-01'))).toBe(
      'IP-01 hold point, trench formation level and width',
    );
  });
});
