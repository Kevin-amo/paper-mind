import { readFileSync } from 'node:fs';

const reviewView = readFileSync(new URL('../src/views/review/ReviewWorkspaceView.vue', import.meta.url), 'utf8');
const leaderView = readFileSync(new URL('../src/views/review-leader/ReviewLeaderWorkspaceView.vue', import.meta.url), 'utf8');
const taskList = readFileSync(new URL('../src/views/review/components/ReviewTaskList.vue', import.meta.url), 'utf8');
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

function requireMatch(source, pattern, message) {
  if (!pattern.test(source)) {
    issues.push(message);
  }
}

requireContains(pkg, 'check:review-workbench-two-zone', 'package.json must wire the two-zone review workbench check.');

requireMatch(
  leaderView,
  /\.leader-top-nav,\s*[\s\S]*?\.leader-shell,[\s\S]*?\{[\s\S]*?width:\s*min\(100%,\s*1360px\);[\s\S]*?\}/,
  'Leader workspace width source must remain 1360px.',
);

requireMatch(
  reviewView,
  /\.review-page\s*\{[\s\S]*?--review-list-width:\s*360px;[\s\S]*?--review-layout-gap:\s*26px;[\s\S]*?--review-shell-width:\s*min\(calc\(100vw - \(var\(--review-page-gutter\) \* 2\)\),\s*1360px\);[\s\S]*?--review-workbench-width:\s*calc\(var\(--review-shell-width\) - var\(--review-list-width\) - var\(--review-layout-gap\)\);[\s\S]*?\}/,
  'Review page shell width must match the leader workspace 1360px width.',
);

requireMatch(
  reviewView,
  /\.review-layout\s*\{[\s\S]*?grid-template-columns:\s*var\(--review-list-width\)\s+minmax\(0,\s*1fr\);[\s\S]*?\}/,
  'Review workspace must use a fixed 360px paper list and one flexible workbench column.',
);

requireNotContains(
  reviewView,
  'review-assist-rail',
  'Review workspace must not keep the separate right-side assist rail.',
);

requireContains(
  reviewView,
  'review-summary-strip',
  'Review workbench must move AI summary, score, and risk hints into the main workbench summary strip.',
);

requireMatch(
  reviewView,
  /\.review-work-area\s*\{[\s\S]*?grid-template-columns:\s*1fr;[\s\S]*?\}/,
  'Review work area must render as a single main workbench column.',
);

requireMatch(
  reviewView,
  /\.review-detail\s*\{[\s\S]*?height:\s*calc\(100vh - 250px\);[\s\S]*?min-height:\s*650px;[\s\S]*?overflow-y:\s*auto;[\s\S]*?border:\s*1px solid var\(--app-border\);[\s\S]*?border-radius:\s*var\(--app-radius-md\);[\s\S]*?background:\s*var\(--app-surface\);[\s\S]*?\}/,
  'Right review workbench must be one fixed-height bordered panel with internal scrolling.',
);

requireMatch(
  reviewView,
  /\.review-bottom-bar\s*\{[\s\S]*?right:\s*auto;[\s\S]*?left:\s*var\(--review-shell-left\);[\s\S]*?width:\s*var\(--review-shell-width\);[\s\S]*?\}/,
  'Bottom review action bar must align left to the paper list and right to the review workbench edge.',
);

requireMatch(
  reviewView,
  /\.review-paper-header\s*\{[\s\S]*?padding:\s*0 0 18px;[\s\S]*?border-bottom:\s*1px solid var\(--app-border\);[\s\S]*?\}/,
  'Paper header must be an internal top region of the right workbench, not a separate card.',
);

requireMatch(
  reviewView,
  /\.review-tab-surface\s*\{[\s\S]*?border:\s*0;[\s\S]*?border-radius:\s*0;[\s\S]*?background:\s*transparent;[\s\S]*?\}/,
  'Review tab surface must read as internal workbench content rather than a nested outer card.',
);

requireMatch(
  taskList,
  /\.review-task-inbox\s*\{[\s\S]*?height:\s*calc\(100vh - 250px\);[\s\S]*?overflow:\s*hidden;[\s\S]*?\}/,
  'Review task inbox must keep a stable fixed-height shell.',
);

requireMatch(
  taskList,
  /\.task-list\s*\{[\s\S]*?flex:\s*1;[\s\S]*?min-height:\s*0;[\s\S]*?overflow-y:\s*auto;[\s\S]*?\}/,
  'Review task list must scroll internally when many papers are present.',
);

if (issues.length) {
  console.error(issues.join('\n'));
  process.exit(1);
}

console.log('Review workbench keeps the approved two-zone layout.');
