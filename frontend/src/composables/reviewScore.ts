import type { ReviewCriterion, ReviewScoreItem } from '../types';

function normalizeCode(code: unknown) {
  return typeof code === 'string' ? code.trim().toUpperCase() : '';
}

export function calculateWeightedScore(scores: unknown, criteriaList: ReviewCriterion[]) {
  if (!Array.isArray(scores) || scores.length === 0) {
    return 0;
  }

  const weightMap = new Map<string, number>();
  if (Array.isArray(criteriaList)) {
    for (const criterion of criteriaList) {
      if (criterion.code && criterion.weight != null) {
        weightMap.set(normalizeCode(criterion.code), criterion.weight);
      }
    }
  }

  let weightedSum = 0;
  let totalWeight = 0;
  for (const item of scores) {
    const scoreItem = item as ReviewScoreItem;
    const score = Number(scoreItem.score);
    if (!Number.isFinite(score)) continue;
    const code = normalizeCode(scoreItem.code);
    const weight = code ? (weightMap.get(code) ?? 1) : 1;
    weightedSum += score * weight;
    totalWeight += weight;
  }

  if (totalWeight === 0) {
    return 0;
  }

  return Math.round(weightedSum / totalWeight);
}
