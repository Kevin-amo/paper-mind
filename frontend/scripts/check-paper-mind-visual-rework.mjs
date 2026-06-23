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

if (missing.length) {
  console.error(missing.join('\n'));
  process.exit(1);
}

console.log('PaperMind visual rework markers match DESIGN.md scope.');
