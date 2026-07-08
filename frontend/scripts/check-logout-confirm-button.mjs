import fs from 'node:fs';
import path from 'node:path';

const root = process.cwd();
const requiredToken = '--el-button-text-color: var(--app-text-on-primary);';
const requiredHoverToken = '--el-button-hover-text-color: var(--app-text-on-primary);';
const requiredActiveToken = '--el-button-active-text-color: var(--app-text-on-primary);';

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

for (const file of [
  'src/components/common/LogoutConfirmDialog.vue',
  'src/components/chat/ChatSidebar.vue',
]) {
  const absolute = path.join(root, file);
  const content = fs.readFileSync(absolute, 'utf8');
  const block = findRuleBlock(content, '.logout-confirm-footer .logout-confirm-button');

  if (!block) {
    throw new Error(`${file} must define logout confirm button styles.`);
  }

  for (const token of [requiredToken, requiredHoverToken, requiredActiveToken]) {
    if (!block.includes(token)) {
      throw new Error(`${file} logout confirm button must keep text white with ${token}`);
    }
  }
}

console.log('退出确认按钮保持可读的白色文本。');
