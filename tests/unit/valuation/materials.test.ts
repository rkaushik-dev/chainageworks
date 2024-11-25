import { describe, expect, it } from 'vitest';
import { parseItemCode } from '../../../src/domain/bill/itemCode.js';
import { moneyFromMajor, moneyMajor } from '../../../src/domain/money/money.js';
import { percent } from '../../../src/domain/money/percent.js';
import { quantity } from '../../../src/domain/quantity/quantity.js';
import { parseSiteDate } from '../../../src/domain/time/siteDate.js';
import type { MaterialsInput } from '../../../src/valuation/materials.js';
import {
  buildMaterialsOnSite,
  claimableMaterials,
  describeMaterials,
  isClaimableAt,
  materialsMovement,
  materialsValue,
  reasonNotClaimable,
} from '../../../src/valuation/materials.js';

const d = parseSiteDate;

const base: MaterialsInput = {
  id: 'MOS-01',
  packageId: 'WP-02',
  code: parseItemCode('I185.2'),
  description: '900 mm concrete pipe, 240 m delivered to the compound',
  quantity: quantity(240, 'm'),
  value: moneyFromMajor(38_400, 'GBP'),
  deliveredOn: d('2025-03-10'),
  vestedOn: d('2025-03-12'),
};

const pipes = buildMaterialsOnSite(base);
const cutOff = d('2025-03-31');

describe('buildMaterialsOnSite', () => {
  it('defaults to protected and neither vested nor built in', () => {
    const bare = buildMaterialsOnSite({ ...base, vestedOn: null });
    expect(bare.protectedOnSite).toBe(true);
    expect(bare.vestedOn).toBeNull();
    expect(bare.incorporatedOn).toBeNull();
  });

  it('trims the description and refuses an empty one', () => {
    expect(buildMaterialsOnSite({ ...base, description: '  pipe  ' }).description).toBe('pipe');
    expect(() => buildMaterialsOnSite({ ...base, description: ' ' })).toThrow(
      /need a description/,
    );
  });

  it('refuses a negative value', () => {
    expect(() => buildMaterialsOnSite({ ...base, value: moneyFromMajor(-1, 'GBP') })).toThrow(
      /negative value/,
    );
  });

  it('refuses vesting or incorporation before delivery', () => {
    expect(() => buildMaterialsOnSite({ ...base, vestedOn: d('2025-03-01') })).toThrow(
      /vest before they were delivered/,
    );
    expect(() => buildMaterialsOnSite({ ...base, incorporatedOn: d('2025-03-01') })).toThrow(
      /built in before they were delivered/,
    );
  });
});

describe('reasonNotClaimable', () => {
  it('passes everything that has been delivered, vested and protected', () => {
    expect(reasonNotClaimable(pipes, cutOff)).toBeNull();
    expect(isClaimableAt(pipes, cutOff)).toBe(true);
  });

  it('rejects material that has not arrived yet', () => {
    expect(reasonNotClaimable(pipes, d('2025-03-09'))).toBe('not_delivered');
  });

  it('rejects material whose title has not passed', () => {
    expect(reasonNotClaimable(pipes, d('2025-03-11'))).toBe('not_vested');
    const unvested = buildMaterialsOnSite({ ...base, vestedOn: null });
    expect(reasonNotClaimable(unvested, cutOff)).toBe('not_vested');
  });

  it('rejects material that is not protected', () => {
    const exposed = buildMaterialsOnSite({ ...base, protectedOnSite: false });
    expect(reasonNotClaimable(exposed, cutOff)).toBe('not_protected');
  });

  it('rejects material that is already in the works, which is the double count', () => {
    const built = buildMaterialsOnSite({ ...base, incorporatedOn: d('2025-03-20') });
    expect(reasonNotClaimable(built, cutOff)).toBe('incorporated');
    expect(reasonNotClaimable(built, d('2025-03-19'))).toBeNull();
  });

  it('reports the first failing condition, not all of them', () => {
    const nothing = buildMaterialsOnSite({
      ...base,
      vestedOn: null,
      protectedOnSite: false,
    });
    expect(reasonNotClaimable(nothing, d('2025-03-01'))).toBe('not_delivered');
  });
});

describe('materialsValue', () => {
  const cement = buildMaterialsOnSite({
    ...base,
    id: 'MOS-02',
    description: 'cement, 40 tonnes in silo',
    quantity: quantity(40, 't'),
    value: moneyFromMajor(6000, 'GBP'),
    deliveredOn: d('2025-03-18'),
    vestedOn: d('2025-03-18'),
  });

  it('takes the contract allowance off the invoice value', () => {
    expect(moneyMajor(materialsValue([pipes], cutOff, 'GBP'))).toBe(34_560);
  });

  it('adds every claimable lot', () => {
    expect(moneyMajor(materialsValue([pipes, cement], cutOff, 'GBP'))).toBe(34_560 + 5400);
  });

  it('honours a different allowance', () => {
    expect(moneyMajor(materialsValue([pipes], cutOff, 'GBP', percent(100)))).toBe(38_400);
    expect(moneyMajor(materialsValue([pipes], cutOff, 'GBP', percent(0)))).toBe(0);
  });

  it('leaves out anything that cannot be claimed', () => {
    const built = buildMaterialsOnSite({ ...base, incorporatedOn: d('2025-03-20') });
    expect(moneyMajor(materialsValue([built, cement], cutOff, 'GBP'))).toBe(5400);
  });

  it('is nothing when the list is empty or nothing qualifies', () => {
    expect(materialsValue([], cutOff, 'GBP').minor).toBe(0);
    expect(materialsValue([pipes], d('2025-01-01'), 'GBP').minor).toBe(0);
  });

  it('lists what qualifies', () => {
    expect(claimableMaterials([pipes, cement], d('2025-03-15')).map((m) => m.id)).toEqual([
      'MOS-01',
    ]);
  });
});

describe('materialsMovement', () => {
  const built = buildMaterialsOnSite({ ...base, incorporatedOn: d('2025-04-10') });
  const arriving = buildMaterialsOnSite({
    ...base,
    id: 'MOS-03',
    deliveredOn: d('2025-04-05'),
    vestedOn: d('2025-04-05'),
  });

  it('reports what arrived and what was built in between two cut offs', () => {
    const movement = materialsMovement([built, arriving], cutOff, d('2025-04-30'));
    expect(movement.added.map((m) => m.id)).toEqual(['MOS-03']);
    expect(movement.removed.map((m) => m.id)).toEqual(['MOS-01']);
  });

  it('reports nothing when nothing moved', () => {
    const movement = materialsMovement([pipes], cutOff, d('2025-04-30'));
    expect(movement.added).toEqual([]);
    expect(movement.removed).toEqual([]);
  });
});

describe('describeMaterials', () => {
  it('reads like a schedule line', () => {
    expect(describeMaterials(pipes)).toBe(
      'MOS-01 900 mm concrete pipe, 240 m delivered to the compound, delivered 2025-03-10',
    );
  });
});
