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

const themedScreens = ['src/views/LoginView.vue', 'src/views/UserWorkspaceView.vue'];
for (const file of themedScreens) {
  const content = fs.readFileSync(path.join(root, file), 'utf8');
  if (!content.includes('claude-workspace') && !content.includes('login-shell') && !content.includes('user-app-shell')) {
    throw new Error(`${file} must opt into the Claude workspace theme.`);
  }
}

console.log('Claude workspace theme covers admin, review, login, user, and teleport overlays.');
