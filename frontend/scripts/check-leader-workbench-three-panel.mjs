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

function requireMatch(source, pattern, message) {
  if (!pattern.test(source)) {
    issues.push(message);
  }
}

requireContains(pkg, 'check:leader-workbench-three-panel', 'package.json must wire the leader three-panel workbench check.');

requireContains(
  leaderView,
  '<section class="leader-workbench"',
  'Leader workspace must use the approved three-panel workbench container.',
);

requireNotContains(
  leaderView,
  '<section class="detail-grid"',
  'Leader workspace must not render scoring and consensus as separate lower-page cards.',
);

requireMatch(
  leaderView,
  /\.leader-workbench\s*\{[\s\S]*?grid-template-columns:\s*300px\s+minmax\(520px,\s*1fr\)\s+420px;[\s\S]*?\}/,
  'Leader workbench must use fixed side/detail columns and one flexible task column.',
);

requireMatch(
  leaderView,
  /\.group-panel,\s*[\s\S]*?\.task-panel,\s*[\s\S]*?\.leader-detail-panel\s*\{[\s\S]*?height:\s*calc\(100vh - 250px\);[\s\S]*?min-height:\s*560px;[\s\S]*?overflow:\s*hidden;[\s\S]*?\}/,
  'The three leader panels must keep a fixed height and hide outer overflow.',
);

requireMatch(
  leaderView,
  /\.group-panel-body\s*\{[\s\S]*?flex:\s*1;[\s\S]*?min-height:\s*0;[\s\S]*?overflow-y:\s*auto;[\s\S]*?\}/,
  'Group panel content must scroll internally.',
);

requireMatch(
  leaderView,
  /\.task-table-wrap\s*\{[\s\S]*?flex:\s*1;[\s\S]*?min-height:\s*0;[\s\S]*?overflow:\s*auto;[\s\S]*?\}/,
  'Task panel table must scroll internally.',
);

requireMatch(
  leaderView,
  /\.leader-detail-scroll\s*\{[\s\S]*?flex:\s*1;[\s\S]*?min-height:\s*0;[\s\S]*?overflow-y:\s*auto;[\s\S]*?\}/,
  'Detail panel content must scroll internally.',
);

requireContains(
  leaderView,
  '<el-tabs v-model="detailTab"',
  'Leader detail panel must use tabs for reports, consensus, and risk hints.',
);

requireContains(
  leaderView,
  'name="reports"',
  'Leader detail tabs must include scoring reports.',
);

requireContains(
  leaderView,
  'name="consensus"',
  'Leader detail tabs must include final consensus.',
);

requireContains(
  leaderView,
  'name="risks"',
  'Leader detail tabs must include risk hints.',
);

if (issues.length) {
  console.error(issues.join('\n'));
  process.exit(1);
}

console.log('负责人工作台保持已批准的固定三面板布局。');
