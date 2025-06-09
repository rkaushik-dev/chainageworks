import type { AdjustmentTerms } from '../../domain/fluctuation/adjustment.js';
import { buildAdjustmentTerms } from '../../domain/fluctuation/adjustment.js';
import type { IndexSeries } from '../../domain/fluctuation/index.js';
import { buildIndexSeries } from '../../domain/fluctuation/index.js';
import {
  arrayFromJson,
  booleanFromJson,
  dateFromJson,
  dateToJson,
  needString,
  objectFromJson,
  optionalString,
  percentFromJson,
  percentToJson,
} from './values.js';

export function indexSeriesToJson(series: IndexSeries): Record<string, unknown> {
  return {
    id: series.id,
    name: series.name,
    readings: series.readings.map((reading) => ({
      year: reading.year,
      month: reading.month,
      value: reading.value,
      provisional: reading.provisional,
    })),
  };
}

export function indexSeriesFromJson(value: unknown): IndexSeries {
  const raw = objectFromJson(value, 'index series');
  return buildIndexSeries({
    id: needString(raw['id'], 'index id'),
    name: optionalString(raw['name']),
    readings: arrayFromJson(raw['readings'] ?? [], 'index readings').map((entry) => {
      const reading = objectFromJson(entry, 'index reading');
      return {
        year: Number(reading['year']),
        month: Number(reading['month']),
        value: Number(reading['value']),
        provisional: booleanFromJson(reading['provisional'] ?? false, 'provisional'),
      };
    }),
  });
}

export function adjustmentTermsToJson(terms: AdjustmentTerms): Record<string, unknown> {
  return {
    baseDate: dateToJson(terms.baseDate),
    nonAdjustable: percentToJson(terms.nonAdjustable),
    weightings: terms.weightings.map((weighting) => ({
      indexId: weighting.indexId,
      weight: percentToJson(weighting.weight),
    })),
  };
}

export function adjustmentTermsFromJson(value: unknown): AdjustmentTerms | null {
  if (value === null || value === undefined) return null;
  const raw = objectFromJson(value, 'price adjustment terms');
  return buildAdjustmentTerms({
    baseDate: dateFromJson(raw['baseDate']),
    nonAdjustable: percentFromJson(raw['nonAdjustable'] ?? 0),
    weightings: arrayFromJson(raw['weightings'] ?? [], 'weightings').map((entry) => {
      const weighting = objectFromJson(entry, 'weighting');
      return {
        indexId: needString(weighting['indexId'], 'index id'),
        weight: percentFromJson(weighting['weight']),
      };
    }),
  });
}
