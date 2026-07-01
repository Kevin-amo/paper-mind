import fs from 'node:fs';
import path from 'node:path';

const root = process.cwd();

const requiredFiles = [
  'src/styles/claude-workspace.css',
  'src/style.css',
  'src/layouts/AuthLayout.vue',
  'src/views/LoginView.vue',
  'src/views/UserWorkspaceView.vue',
  'src/components/admin/AdminShell.vue',
  'src/views/review/ReviewWorkspaceView.vue',
  'src/views/review-leader/ReviewLeaderWorkspaceView.vue',
];

for (const file of requiredFiles) {
  const absolute = path.join(root, file);
  if (!fs.existsSync(absolute)) {
    throw new Error(`Missing required Claude workspace theme file: ${file}`);
  }
}

const styleEntry = fs.readFileSync(path.join(root, 'src/style.css'), 'utf8');
if (!styleEntry.includes("@import './styles/claude-workspace.css';")) {
  throw new Error('src/style.css must import the scoped Claude workspace theme.');
}

const theme = fs.readFileSync(path.join(root, 'src/styles/claude-workspace.css'), 'utf8');
const requiredTokens = ['#faf9f5', '#efe9de', '#181715', '#cc785c', '#141413', '#e6dfd8'];
for (const token of requiredTokens) {
  if (!theme.toLowerCase().includes(token)) {
    throw new Error(`Claude workspace theme is missing DESIGN.md token ${token}`);
  }
}

if (/:root\s*\{/.test(theme)) {
  throw new Error('Claude workspace theme must not define global :root tokens.');
}

for (const selector of ['.admin-layout', '.review-page', '.leader-page', '.auth-layout', '.login-shell', '.user-app-shell']) {
  if (!theme.includes(selector)) {
    throw new Error(`Claude workspace theme must scope styles to ${selector}`);
  }
}

for (const selector of ['.el-message', '.el-select__popper', '.el-dropdown__popper', '.el-picker__popper', '.el-message-box']) {
  if (!theme.includes(selector)) {
    throw new Error(`Claude workspace theme must include teleport overlay styles for ${selector}`);
  }
}

function findRuleBlock(selector) {
  const rulePattern = /(?<selectors>[^{}]+)\{(?<body>[^{}]*)\}/g;
  let match;
  while ((match = rulePattern.exec(theme)) !== null) {
    const selectors = match.groups.selectors
      .split(',')
      .map((item) => item.trim());
    if (selectors.includes(selector)) {
      return match.groups.body;
    }
  }
  return '';
}

const defaultButtonSelectors = [
  '.el-dialog.claude-workspace-dialog .el-button',
  '.el-drawer.claude-workspace-drawer .el-button',
  '.el-message-box .el-button',
];

for (const selector of defaultButtonSelectors) {
  const block = findRuleBlock(selector);
  if (!block) {
    throw new Error(`Claude workspace theme must define neutral default button styles for ${selector}`);
  }

  for (const token of [
    '--el-button-bg-color: var(--app-surface);',
    '--el-button-border-color: var(--app-border);',
    '--el-button-text-color: var(--app-text);',
    '--el-button-hover-bg-color: var(--app-surface-soft);',
    '--el-button-hover-border-color: var(--app-border-strong);',
    '--el-button-hover-text-color: var(--app-text);',
    '--el-button-active-bg-color: var(--app-surface-muted);',
    '--el-button-active-border-color: var(--app-border-strong);',
  ]) {
    if (!block.includes(token)) {
      throw new Error(`Claude workspace default button ${selector} is missing ${token}`);
    }
  }
}

for (const selector of [
  '.el-dialog.claude-workspace-dialog .el-button:not(.is-disabled):hover',
  '.el-drawer.claude-workspace-drawer .el-button:not(.is-disabled):hover',
  '.el-message-box .el-button:not(.is-disabled):hover',
]) {
  const block = findRuleBlock(selector);
  if (!block.includes('transform: scale(1.02);')) {
    throw new Error(`Claude workspace default button hover should subtly scale for ${selector}`);
  }
}

for (const selector of [
  '.el-dialog.claude-workspace-dialog .el-button:not(.is-disabled):active',
  '.el-drawer.claude-workspace-drawer .el-button:not(.is-disabled):active',
  '.el-message-box .el-button:not(.is-disabled):active',
]) {
  const block = findRuleBlock(selector);
  if (!block.includes('transform: scale(0.98);')) {
    throw new Error(`Claude workspace default button active state should press down for ${selector}`);
  }
}

for (const selector of [
  '.el-dialog.claude-workspace-dialog .el-button--primary',
  '.el-drawer.claude-workspace-drawer .el-button--primary',
  '.el-message-box .el-button--primary',
]) {
  const block = findRuleBlock(selector);
  if (!block) {
    throw new Error(`Claude workspace theme must define primary button styles for ${selector}`);
  }

  for (const token of [
    '--el-button-text-color: var(--app-text-on-primary);',
    '--el-button-hover-text-color: var(--app-text-on-primary);',
    '--el-button-active-text-color: var(--app-text-on-primary);',
  ]) {
    if (!block.includes(token)) {
      throw new Error(`Claude workspace primary button ${selector} is missing readable text token ${token}`);
    }
  }
}

const themedScreens = ['src/views/LoginView.vue', 'src/views/UserWorkspaceView.vue'];
for (const file of themedScreens) {
  const content = fs.readFileSync(path.join(root, file), 'utf8');
  if (!content.includes('claude-workspace') && !content.includes('login-shell') && !content.includes('user-app-shell')) {
    throw new Error(`${file} must opt into the Claude workspace theme.`);
  }
}

console.log('Claude workspace theme covers admin, review, login, user, and teleport overlays.');
