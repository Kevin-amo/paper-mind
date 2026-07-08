import { readFileSync } from 'node:fs';

const reviewsComposable = readFileSync(new URL('../src/composables/useReviews.ts', import.meta.url), 'utf8');
const reviewTypes = readFileSync(new URL('../src/types/review.ts', import.meta.url), 'utf8');
const pkg = readFileSync(new URL('../package.json', import.meta.url), 'utf8');

const issues = [];

function requireContains(source, token, message) {
  if (!source.includes(token)) {
    issues.push(message);
  }
}

requireContains(pkg, 'check:review-risk-confirmation', 'package.json must wire the review risk confirmation check.');
requireContains(reviewTypes, 'riskReviewStatus', 'Review report types must expose riskReviewStatus.');
requireContains(reviewsComposable, 'NO_RISK_CONFIRMED', 'Reviewer submission must use an explicit no-risk confirmation status.');
requireContains(reviewsComposable, 'ElMessageBox.confirm', 'Empty risks must require an explicit confirmation dialog before submitting.');
requireContains(
  reviewsComposable,
  '当前报告暂无风险项。请确认已完成风险检查，并确认本稿暂无需要提示的风险项。',
  'The empty-risk confirmation dialog must use the approved copy.',
);
requireContains(reviewsComposable, 'report.riskReviewStatus = NO_RISK_CONFIRMED', 'Confirmed no-risk reports must mark the selected report.');
requireContains(reviewsComposable, 'riskReviewStatus: report.riskReviewStatus ?? null', 'Persisting the report must include riskReviewStatus.');
requireContains(reviewsComposable, 'ensureRiskReviewConfirmed(report)', 'submitCurrentAssignment must check risk review completion before persisting and submitting.');

const submitBody = reviewsComposable.match(/async function submitCurrentAssignment\(\) \{[\s\S]*?\n  \}/)?.[0] ?? '';
const confirmIndex = submitBody.indexOf('ensureRiskReviewConfirmed(report)');
const persistIndex = submitBody.indexOf('persistCurrentReport(');
const submitIndex = submitBody.indexOf('submitReviewAssignment(');

if (confirmIndex === -1 || persistIndex === -1 || submitIndex === -1) {
  issues.push('submitCurrentAssignment must confirm risk review, persist the report, and submit the assignment.');
} else if (!(confirmIndex < persistIndex && persistIndex < submitIndex)) {
  issues.push('Risk review confirmation must happen before saving, and saving must happen before assignment submission.');
}

if (issues.length) {
  console.error(issues.join('\n'));
  process.exit(1);
}

console.log('Review submission requires persisted no-risk confirmation before submitting.');
