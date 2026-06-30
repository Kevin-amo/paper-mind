import { readFileSync } from 'node:fs';

const drawer = readFileSync(new URL('../src/components/review-submissions/ReviewSubmissionDrawer.vue', import.meta.url), 'utf8');
const packageJson = readFileSync(new URL('../package.json', import.meta.url), 'utf8');
const issues = [];

if (!packageJson.includes('check:submission-report-button')) {
  issues.push('Build should include the submission report button contrast check.');
}

const buttonMatch = drawer.match(/<el-button[\s\S]*?class="report-link"[\s\S]*?>/);
if (!buttonMatch) {
  issues.push('Missing report action button with class="report-link".');
} else {
  const buttonMarkup = buttonMatch[0];
  if (/(^|\s)link(?:\s|>|=)/.test(buttonMarkup)) {
    issues.push('Report action must not use Element Plus link style because it blends into the table background.');
  }
  if (!buttonMarkup.includes('type="primary"')) {
    issues.push('Report action should use the primary Claude action color.');
  }
  if (!buttonMarkup.includes('size="small"')) {
    issues.push('Report action should remain compact inside the table action column.');
  }
}

const styleMatch = drawer.match(/\.report-link\s*\{(?<body>[\s\S]*?)\n\}/)?.groups?.body ?? '';
if (!styleMatch) {
  issues.push('Missing .report-link style block.');
}

const requiredTokens = [
  '--el-button-bg-color: var(--app-primary);',
  '--el-button-border-color: var(--app-primary);',
  '--el-button-text-color: var(--app-text-on-primary);',
  '--el-button-hover-bg-color: var(--app-primary-hover);',
  '--el-button-hover-text-color: var(--app-text-on-primary);',
  'font-weight: 600;',
];

for (const token of requiredTokens) {
  if (!styleMatch.includes(token)) {
    issues.push(`Report action style should include ${token}`);
  }
}

if (!/\.review-submission-drawer\s+:deep\(\.report-link:focus-visible\)\s*\{[\s\S]*?box-shadow:\s*var\(--app-shadow-focus\);[\s\S]*?\}/.test(drawer)) {
  issues.push('Report action should expose the Claude focus ring for keyboard users.');
}

if (issues.length) {
  console.error(issues.join('\n'));
  process.exit(1);
}

console.log('Submission report button uses a visible Claude-style primary action.');
