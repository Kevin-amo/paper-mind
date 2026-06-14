import { readFileSync } from 'node:fs';

const dashboard = readFileSync(new URL('../src/views/admin/AdminReviewDashboardView.vue', import.meta.url), 'utf8');
const detailDrawer = readFileSync(new URL('../src/components/admin/review/ReviewTaskDetailDrawer.vue', import.meta.url), 'utf8');

const missing = [];
if (!dashboard.includes('ReviewTaskDetailDrawer')) {
  missing.push('Admin review task detail drawer is not mounted');
}
if (!dashboard.includes('detailVisible')) {
  missing.push('Admin review detail button has no visible state to open');
}
if (!dashboard.includes('@open="openTask"')) {
  missing.push('Admin review task table open event is not wired');
}
if (detailDrawer.includes('论文信息') || detailDrawer.includes('摘要') || detailDrawer.includes('abstractText')) {
  missing.push('Admin review task detail drawer must not render paper info or abstract sections');
}

const removedDrawerTokens = ['Review' + 'AssignmentDrawer', 'Review' + 'ConsensusDrawer'];
if (removedDrawerTokens.some((token) => dashboard.includes(token))) {
  missing.push('Admin review dashboard must not mount admin fallback assignment or consensus drawers');
}

const removedEventTokens = ['@' + 'assign=', '@' + 'consensus='];
if (removedEventTokens.some((token) => dashboard.includes(token))) {
  missing.push('Admin review task table must not wire fallback assignment or consensus events');
}

if (dashboard.includes('adminReviews.' + 'recalc')) {
  missing.push('Admin review dashboard must not expose admin consensus recalculation');
}

if (dashboard.includes('旧状态：')) {
  missing.push('Admin review dashboard must not expose legacy task statuses in the status filter');
}

if (missing.length) {
  console.error(missing.join('\n'));
  process.exit(1);
}

console.log('Admin review task detail opens a visible drawer.');
