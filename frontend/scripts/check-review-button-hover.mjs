import { readFileSync } from 'node:fs';

const reviewView = readFileSync(new URL('../src/views/review/ReviewWorkspaceView.vue', import.meta.url), 'utf8');
const issues = [];

function requireMatch(pattern, message) {
  if (!pattern.test(reviewView)) {
    issues.push(message);
  }
}

const buttonBlock = reviewView.match(/\.regenerate-review-button\s*\{(?<body>[\s\S]*?)\n\}/)?.groups?.body ?? '';

if (!buttonBlock) {
  issues.push('Missing regenerate review button style block.');
}

requireMatch(
  /\.review-page\s+:deep\(\.regenerate-review-button:not\(\.is-disabled\):not\(\.is-loading\):hover\)\s*\{[\s\S]*?transform:\s*scale\(1\.02\);[\s\S]*?\}/,
  'Review generation button hover should only use a subtle scale transform.',
);

for (const token of [
  '--el-button-hover-bg-color: var(--app-primary-soft);',
  '--el-button-hover-text-color: var(--app-primary);',
]) {
  if (!buttonBlock.includes(token)) {
    issues.push(`Hover token should preserve readable button colors: ${token}`);
  }
}

if (buttonBlock.includes('--el-button-hover-bg-color: var(--app-primary-soft-hover);')) {
  issues.push('Hover background must not switch to the darker overlay color.');
}

if (buttonBlock.includes('--el-button-hover-text-color: var(--app-primary-active);')) {
  issues.push('Hover text must not switch to the active color.');
}

if (issues.length) {
  console.error(issues.join('\n'));
  process.exit(1);
}

console.log('Review generation button hover keeps text readable and scales subtly.');
