import { readFileSync } from 'node:fs';

const files = {
  packageJson: readFileSync(new URL('../package.json', import.meta.url), 'utf8'),
  userView: readFileSync(new URL('../src/views/UserWorkspaceView.vue', import.meta.url), 'utf8'),
  sidebar: readFileSync(new URL('../src/components/chat/ChatSidebar.vue', import.meta.url), 'utf8'),
  workspace: readFileSync(new URL('../src/components/chat/RagChatWorkspace.vue', import.meta.url), 'utf8'),
  messages: readFileSync(new URL('../src/components/chat/ChatMessageList.vue', import.meta.url), 'utf8'),
  composer: readFileSync(new URL('../src/components/chat/ChatComposer.vue', import.meta.url), 'utf8'),
  adminShell: readFileSync(new URL('../src/components/admin/AdminShell.vue', import.meta.url), 'utf8'),
  adminReviews: readFileSync(new URL('../src/views/admin/AdminReviewDashboardView.vue', import.meta.url), 'utf8'),
  adminUsers: readFileSync(new URL('../src/components/admin/AdminUsersPanel.vue', import.meta.url), 'utf8'),
  review: readFileSync(new URL('../src/views/review/ReviewWorkspaceView.vue', import.meta.url), 'utf8'),
  reviewTaskList: readFileSync(new URL('../src/views/review/components/ReviewTaskList.vue', import.meta.url), 'utf8'),
  reviewScores: readFileSync(new URL('../src/views/review/components/ReviewScoresTab.vue', import.meta.url), 'utf8'),
  leader: readFileSync(new URL('../src/views/review-leader/ReviewLeaderWorkspaceView.vue', import.meta.url), 'utf8'),
  theme: readFileSync(new URL('../src/styles/claude-workspace.css', import.meta.url), 'utf8'),
};

const missing = [];

function requireIncludes(fileKey, marker, label = marker) {
  if (!files[fileKey].includes(marker)) {
    missing.push(`${fileKey} missing ${label}`);
  }
}

function requireNotIncludes(fileKey, marker, label = marker) {
  if (files[fileKey].includes(marker)) {
    missing.push(`${fileKey} must not include ${label}`);
  }
}

function findRuleBlock(content, selector) {
  const rulePattern = /(?<selectors>[^{}]+)\{(?<body>[^{}]*)\}/g;
  let match;
  while ((match = rulePattern.exec(content)) !== null) {
    const selectors = match.groups.selectors
      .split(',')
      .map((item) => item.trim());
    if (selectors.includes(selector)) {
      return match.groups.body;
    }
  }
  return '';
}

requireIncludes('packageJson', 'check:paper-mind-visual-rework', 'build hook');

for (const token of ['--claude-canvas', '--claude-coral', '--claude-dark', '--claude-serif', '--claude-sans']) {
  requireIncludes('theme', token, `DESIGN token ${token}`);
}
requireIncludes('theme', '.paper-mind-workspace-table', 'shared table surface class');
requireIncludes('theme', '.paper-mind-workspace-card', 'shared card surface class');

for (const [fileKey, marker] of [
  ['userView', 'user-app-shell claude-chat-shell'],
  ['sidebar', 'claude-chat-sidebar'],
  ['workspace', 'claude-chat-main'],
  ['messages', 'claude-message-column'],
  ['composer', 'claude-floating-composer'],
]) {
  requireIncludes(fileKey, marker);
}

for (const [fileKey, marker] of [
  ['adminShell', 'paper-mind-admin-shell'],
  ['adminUsers', 'paper-mind-workspace-card users-panel'],
  ['adminReviews', 'paper-mind-workspace-card review-dashboard-panel'],
  ['review', 'paper-mind-review-shell'],
  ['leader', 'paper-mind-leader-shell'],
]) {
  requireIncludes(fileKey, marker);
}

for (const fileKey of ['adminShell', 'adminUsers', 'adminReviews', 'review', 'leader']) {
  requireIncludes(fileKey, 'var(--claude-canvas)', `${fileKey} uses DESIGN canvas token`);
}

requireIncludes('adminUsers', 'users-panel animate fade-in reused-management-panel', 'reused fade-in panel motion');
requireIncludes('adminUsers', 'admin-reused-summary-grid', 'reused summary grid');
requireNotIncludes('adminUsers', 'class="admin-summary"', 'old compact user summary layout');
for (const delay of ['0ms', '80ms', '160ms', '240ms']) {
  requireIncludes('adminUsers', `delay: '${delay}'`, `summary slide-up delay ${delay}`);
}
requireIncludes('adminUsers', 'layout="total, sizes, prev, pager, next"', 'global-progress pagination layout');
requireIncludes('adminUsers', ':page-sizes="[10, 20, 50]"', 'global-progress page-size options');
requireIncludes('adminUsers', 'function handlePageSizeChange(nextSize: number)', 'user page-size change handler');
requireIncludes('adminUsers', '@size-change="handlePageSizeChange"', 'user pagination size-change binding');
requireIncludes('adminUsers', 'v-model="admin.formDialogVisible.value" :title="admin.dialogTitle.value" width="480px" class="claude-workspace-dialog" append-to-body modal-class="admin-users-dialog-overlay"', 'user form dialog uses body-level custom overlay');
requireIncludes('adminUsers', 'v-model="admin.passwordDialogVisible.value" title="重置密码" width="400px" class="claude-workspace-dialog" append-to-body modal-class="admin-users-dialog-overlay"', 'password reset dialog uses body-level custom overlay');
requireIncludes('adminUsers', ':global(.admin-users-dialog-overlay)', 'user dialogs custom overlay style');
requireIncludes('adminUsers', 'background: rgba(250, 249, 245, 0.18);', 'user dialogs avoid gray default overlay');

for (const marker of [
  'leader-top-nav',
  'leader-brand-mark',
  'leader-hero',
  'leader-brief-card',
  'leader-stats-grid',
  'leader-empty-state',
]) {
requireIncludes('leader', marker, `review leader demo marker ${marker}`);
}
requireNotIncludes('leader', '<PageHeader', 'generic PageHeader in review leader workspace');
requireNotIncludes('leader', '<el-empty', 'Element Plus default empty state in review leader workspace');
requireNotIncludes('leader', '已同步', 'ambiguous sync status copy in review leader summary');

for (const marker of [
  'review-top-nav',
  'review-brand-mark',
  'review-hero',
  'review-filter-chips',
  'review-progress-strip',
  'review-empty-state',
  'review-bottom-bar',
]) {
  requireIncludes('review', marker, `review workspace demo marker ${marker}`);
}
requireIncludes('reviewTaskList', 'review-task-inbox', 'review task inbox demo marker');
requireIncludes('reviewTaskList', 'review-empty-state', 'review task list custom empty state');
requireIncludes('review', 'class="review-submit-button"', 'review submit button has Claude brown override hook');
for (const marker of [
  'score-summary',
  'score-total',
  'score-number',
  'recommendation-box',
  'recommendation-editor',
  'metric-row',
  'submitted-panel',
  'readonly-pill',
]) {
  requireIncludes('reviewScores', marker, `review scoring table demo marker ${marker}`);
}
requireNotIncludes('review', 'currentFilterLabel', 'redundant current filter computed label');
requireNotIncludes('review', 'review-filter-note', 'redundant current filter note');
requireNotIncludes('review', '当前筛选', 'redundant current filter copy');
requireNotIncludes('review', 'review-status-pill', 'redundant submitted status pill');
requireNotIncludes('review', 'statusLabel(selectedTask.currentAssignment.status)', 'redundant submitted status label');
requireNotIncludes('reviewTaskList', 'statusFilter', 'left inbox status filter prop');
requireNotIncludes('reviewTaskList', 'update:statusFilter', 'left inbox status filter emit');
requireNotIncludes('reviewTaskList', '<el-select', 'left inbox status select');
requireNotIncludes('reviewTaskList', 'task-toolbar-row', 'left inbox status filter row');
requireNotIncludes('reviewTaskList', 'task-tabs', 'left inbox static task tabs');
requireNotIncludes('reviewTaskList', 'task-tab', 'left inbox static task tab buttons');
requireNotIncludes('review', '<PageHeader', 'generic PageHeader in review workspace');
requireNotIncludes('review', '<el-empty', 'Element Plus default empty state in review workspace');
requireNotIncludes('reviewTaskList', '<el-empty', 'Element Plus default empty state in review task inbox');
requireNotIncludes('reviewScores', 'score-grid', 'old scoring card grid layout');
requireNotIncludes('reviewScores', 'score-card', 'old large scoring cards');
requireNotIncludes('reviewScores', 'manual-section', 'old always-visible manual adjustment section');
requireNotIncludes('reviewScores', 'decision-strip', 'removed reviewer decision shortcut strip');
requireNotIncludes('reviewScores', 'edit-panel', 'removed duplicate score/recommendation edit panel');
requireNotIncludes('reviewScores', 'score-editor', 'removed editable final total score control');
requireNotIncludes('reviewScores', '<el-empty', 'Element Plus default empty state in review scores tab');

const reviewSubmitButton = findRuleBlock(files.review, '.review-submit-button');
if (!reviewSubmitButton.includes('--el-button-bg-color: var(--app-surface-muted);')) {
  missing.push('review submit button must use light Claude brown surface, not default primary blue');
}
if (!reviewSubmitButton.includes('--el-button-disabled-bg-color: var(--app-surface-muted);')) {
  missing.push('review submit button disabled state must stay light Claude brown');
}
if (!reviewSubmitButton.includes('--el-button-text-color: var(--app-text);')) {
  missing.push('review submit button text must use warm ink on light brown');
}

const leaderBriefCard = findRuleBlock(files.leader, '.leader-brief-card');
if (!leaderBriefCard.includes('background: var(--app-surface-soft);')) {
  missing.push('leader brief card must use a light cream surface');
}
if (leaderBriefCard.includes('background: var(--app-dark);') || leaderBriefCard.includes('color: var(--app-on-dark);')) {
  missing.push('leader brief card must not use a dark surface');
}

if (missing.length) {
  console.error(missing.join('\n'));
  process.exit(1);
}

console.log('PaperMind视觉重构标记与DESIGN.md范围匹配。');
