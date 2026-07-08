import { readFileSync } from 'node:fs';

const reviewsComposable = readFileSync(new URL('../src/composables/useReviews.ts', import.meta.url), 'utf8');
const issues = [];

const submitBody = reviewsComposable.match(/async function submitCurrentAssignment\(\) \{[\s\S]*?\n  \}/)?.[0] ?? '';
const persistIndex = submitBody.indexOf('persistCurrentReport(');
const submitIndex = submitBody.indexOf('submitReviewAssignment(');
const persistBody = reviewsComposable.match(/async function persistCurrentReport\([\s\S]*?\n  \}/)?.[0] ?? '';

if (persistIndex === -1) {
  issues.push('Submitting a review assignment must save the current report first.');
}

if (submitIndex === -1) {
  issues.push('Submitting a review assignment must still call the assignment submit endpoint.');
}

if (persistIndex !== -1 && submitIndex !== -1 && persistIndex > submitIndex) {
  issues.push('The current report must be saved before the assignment is submitted.');
}

if (!persistBody.includes('updateReviewReport(')) {
  issues.push('Persisting the current report must call the report update endpoint.');
}

if (!persistBody.includes('finalRecommendation: reportForm.finalRecommendation.trim() || null')) {
  issues.push('Submitting a review assignment must save the current final recommendation.');
}

if (!persistBody.includes("status: 'ADJUSTED'")) {
  issues.push('Submitting a review assignment must persist the report as an adjusted report.');
}

if (issues.length) {
  console.error(issues.join('\n'));
  process.exit(1);
}

console.log('Review assignment submission saves the latest report before submitting.');
