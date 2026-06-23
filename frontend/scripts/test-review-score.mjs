import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import ts from 'typescript';

const moduleUrl = new URL('../src/composables/reviewScore.ts', import.meta.url).href;
const source = await readFile(new URL(moduleUrl), 'utf8');
const { outputText } = ts.transpileModule(source, {
  compilerOptions: {
    module: ts.ModuleKind.ESNext,
    target: ts.ScriptTarget.ES2022,
  },
});
const dataUrl = `data:text/javascript;base64,${Buffer.from(outputText).toString('base64')}`;
const { calculateWeightedScore } = await import(dataUrl);

const criteria = [
  { code: 'POLICY', weight: 20 },
  { code: 'MATCH', weight: 20 },
  { code: 'INNOVATION', weight: 20 },
  { code: 'LOGIC', weight: 15 },
  { code: 'LANGUAGE', weight: 15 },
  { code: 'REFERENCE', weight: 10 },
];

const scores = [
  { code: ' policy ', score: 100 },
  { code: 'MATCH', score: 95 },
  { code: 'Innovation', score: 75 },
  { code: 'logic', score: 90 },
  { code: 'language', score: 85 },
  { code: 'REFERENCE', score: 60 },
];

assert.equal(calculateWeightedScore(scores, criteria), 86);

const fallbackScores = [
  { code: 'unknown', score: 100 },
  { code: '', score: 50 },
];

assert.equal(calculateWeightedScore(fallbackScores, criteria), 75);

console.log('review score calculation checks passed');
