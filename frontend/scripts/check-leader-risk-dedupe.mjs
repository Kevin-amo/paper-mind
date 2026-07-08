import { readFileSync } from 'node:fs';

const leaderView = readFileSync(new URL('../src/views/review-leader/ReviewLeaderWorkspaceView.vue', import.meta.url), 'utf8');
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

requireContains(pkg, 'check:leader-risk-dedupe', 'package.json must wire the leader risk dedupe check.');
requireContains(leaderView, 'function riskDedupeKey', 'Leader risks must use a stable normalized dedupe key.');
requireContains(leaderView, 'function dedupeRiskItems', 'Leader risks must deduplicate the flattened risk list.');
requireContains(leaderView, 'dedupeRiskItems(reports.value.flatMap', 'Leader risk summary must deduplicate risks across reports.');
requireContains(leaderView, 'risk.dedupeKey', 'Leader risk item rendering must use the stable dedupe key.');
requireNotContains(
  leaderView,
  'const reportRiskItems = computed(() => reports.value.flatMap((report) => riskItems(report)))',
  'Leader risk summary must not expose the raw flattened risk list.',
);
requireNotContains(
  leaderView,
  ':key="`${risk.type}-${index}`"',
  'Leader risk rendering must not use type plus index as its key.',
);

if (issues.length) {
  console.error(issues.join('\n'));
  process.exit(1);
}

console.log('Leader risk reminders are deduplicated with stable keys.');
