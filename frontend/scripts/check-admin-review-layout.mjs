import { readFileSync } from 'node:fs';

const panel = readFileSync(new URL('../src/components/admin/review/ReviewBatchGroupPanel.vue', import.meta.url), 'utf8');
const pkg = readFileSync(new URL('../package.json', import.meta.url), 'utf8');

const missing = [];

for (const token of [
  'filteredGroups',
  'reused-management-panel',
  'reused-panel-heading',
  'config-tab-pane',
  'reused-toolbar',
  'reused-table-card',
]) {
  if (!panel.includes(token)) {
    missing.push(`ReviewBatchGroupPanel missing ${token}`);
  }
}

if (!panel.includes(':data="filteredGroups"')) {
  missing.push('Group table must render filteredGroups in the management panel');
}

if (!pkg.includes('check:admin-review-layout')) {
  missing.push('package.json must wire check:admin-review-layout');
}

if (!/\.reused-toolbar\s*\{[^}]*grid-template-columns:\s*minmax\(200px,\s*1fr\)\s+160px\s+auto/s.test(panel)) {
  missing.push('Review batch/group toolbar must match the approved demo filter layout');
}

if (panel.includes('listReviewGroups(selectedBatchId.value || undefined)')) {
  missing.push('Group tab must not be implicitly filtered by selectedBatchId');
}

for (const token of [
  'activeConfigTab',
  'filteredBatches',
  'review-config-overview',
  'admin-reused-summary-grid',
  'config-card',
  'summary-icon',
  '小组总数',
  '启用小组',
  'activeGroupCount',
  'configLayoutMode',
  'ConfigLayoutMode',
  'config-view-switch',
  '双栏滚动',
  'fixed-scroll-wrapper',
  'sticky-footer',
]) {
  if (panel.includes(token)) {
    missing.push(`ReviewBatchGroupPanel must not keep removed dual layout token: ${token}`);
  }
}

if (missing.length) {
  console.error(missing.join('\n'));
  process.exit(1);
}

console.log('管理后台审阅分组布局使用复用的管理面板。');
