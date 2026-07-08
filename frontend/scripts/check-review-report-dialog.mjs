import { readFileSync } from 'node:fs';

const drawer = readFileSync(new URL('../src/components/review-submissions/ReviewSubmissionDrawer.vue', import.meta.url), 'utf8');
const packageJson = readFileSync(new URL('../package.json', import.meta.url), 'utf8');
const issues = [];

function requireIncludes(token, message) {
  if (!drawer.includes(token)) {
    issues.push(message);
  }
}

function requirePattern(pattern, message) {
  if (!pattern.test(drawer)) {
    issues.push(message);
  }
}

if (!packageJson.includes('check:review-report-dialog')) {
  issues.push('Build should include the review report dialog layout check.');
}

requireIncludes('class="review-report-dialog paper-report-dialog"', 'Report dialog should use the dedicated warm paper dialog class.');
requireIncludes('class="report-dialog-header"', 'Report dialog should render a custom header.');
requireIncludes('class="report-meta-pill"', 'Confirmed time should be shown as a compact header meta pill.');
requireIncludes('class="report-summary-grid"', 'Report summary should use the demo two-column summary grid.');
requireIncludes('class="report-recommendation-card"', 'Final recommendation should live in its own card.');
requireIncludes('class="report-recommendation-text"', 'Long final recommendation should use the scrollable text container.');
requireIncludes('class="report-score-row"', 'Criterion scores should render as custom score rows.');
requireIncludes('class="score-bar-fill"', 'Criterion scores should include proportional score bars.');

requirePattern(
  /:global\(\[class~="paper-report-dialog"\]\)\s*\{[\s\S]*?--report-surface:\s*#faf9f5;[\s\S]*?--report-card:\s*#fffaf3;[\s\S]*?\}/,
  'Dialog theme variables must be global so Element Plus dialog DOM can use them.',
);
requirePattern(
  /:global\(\[class~="paper-report-dialog"\]\[class~="el-dialog"\]\)\s*\{[\s\S]*?background:\s*var\(--report-surface\);[\s\S]*?\}/,
  'Dialog surface should use a global warm report theme selector.',
);
requirePattern(
  /--report-surface:\s*#faf9f5;/,
  'Large report dialog surfaces should use the Claude canvas #faf9f5.',
);
requirePattern(
  /--report-card:\s*#fffaf3;/,
  'Report cards should use the warm paper card color #fffaf3.',
);
requirePattern(
  /\.report-recommendation-text\s*\{[\s\S]*?max-height:\s*170px;[\s\S]*?overflow:\s*auto;[\s\S]*?white-space:\s*pre-wrap;[\s\S]*?\}/,
  'Final recommendation should support long text with bounded internal scrolling.',
);
requirePattern(
  /const scorePercent\s*=\s*\([^)]*\)\s*=>/,
  'Score bars should compute a score percentage from score and maxScore.',
);

for (const forbidden of ['报告状态', '共识已确认']) {
  if (drawer.includes(forbidden)) {
    issues.push(`User-facing report dialog should not expose internal status text: ${forbidden}`);
  }
}

for (const tooWhite of ['#fffdf8']) {
  if (drawer.includes(tooWhite)) {
    issues.push(`Report dialog should not use near-white large-surface color ${tooWhite}; use Claude beige tokens instead.`);
  }
}

if (issues.length) {
  console.error(issues.join('\n'));
  process.exit(1);
}

console.log('用户侧评审报告弹窗使用暖色报告主题并隐藏内部流程状态。');
