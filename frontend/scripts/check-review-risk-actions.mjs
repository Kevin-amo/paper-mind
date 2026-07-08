import { readFileSync } from 'node:fs';

const riskTab = readFileSync(new URL('../src/views/review/components/ReviewRisksTab.vue', import.meta.url), 'utf8');
const pkg = readFileSync(new URL('../package.json', import.meta.url), 'utf8');

const issues = [];

function requireContains(source, token, message) {
  if (!source.includes(token)) {
    issues.push(message);
  }
}

function requireNotContains(source, token, message) {
  if (source.includes(token)) {
    issues.push(message);
  }
}

requireContains(pkg, 'check:review-risk-actions', 'package.json must wire the review risk actions check.');
requireContains(riskTab, "status: 'CONFIRMED' | 'IGNORED'", 'Risk actions should only emit confirm or ignore statuses.');
requireNotContains(riskTab, "'RESOLVED'", 'Risk cards must not expose the resolved action status.');
requireNotContains(riskTab, '标记解决', 'Risk cards must not show the resolved action label.');
requireNotContains(riskTab, 'type="success"', 'Risk cards should not keep the former resolved success button.');

if (issues.length) {
  console.error(issues.join('\n'));
  process.exit(1);
}

console.log('Review risk actions only expose confirm and ignore.');
