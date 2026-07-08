import { readFileSync } from 'node:fs';

const dashboard = readFileSync(new URL('../src/views/admin/AdminReviewDashboardView.vue', import.meta.url), 'utf8');
const shell = readFileSync(new URL('../src/components/admin/AdminShell.vue', import.meta.url), 'utf8');
const pkg = readFileSync(new URL('../package.json', import.meta.url), 'utf8');

const missing = [];

if (!dashboard.includes('align-self: start;')) {
  missing.push('Global progress summary grid should opt out of parent grid stretching');
}

if (!dashboard.includes('align-items: start;')) {
  missing.push('Global progress summary grid should keep cards at their intrinsic compact height');
}

if (!shell.includes('align-content: start;')) {
  missing.push('Admin content grid should not stretch rows apart vertically');
}

if (!dashboard.includes('height: 128px;')) {
  missing.push('Global progress summary cards should use a fixed compact height of 128px');
}

if (!dashboard.includes('padding: 12px 18px;')) {
  missing.push('Global progress summary cards should use compact vertical padding');
}

if (!dashboard.includes('width: 36px;') || !dashboard.includes('height: 36px;')) {
  missing.push('Global progress summary icons should be smaller in compact cards');
}

if (!dashboard.includes('font-size: 24px;')) {
  missing.push('Global progress summary values should be scaled down for compact cards');
}

if (!pkg.includes('check:admin-review-summary-density')) {
  missing.push('package.json must wire check:admin-review-summary-density');
}

if (missing.length) {
  console.error(missing.join('\n'));
  process.exit(1);
}

console.log('管理后台审阅摘要卡片使用紧凑的半高尺寸。');
