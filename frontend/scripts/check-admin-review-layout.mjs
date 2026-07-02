import { readFileSync } from 'node:fs';

const panel = readFileSync(new URL('../src/components/admin/review/ReviewBatchGroupPanel.vue', import.meta.url), 'utf8');
const pkg = readFileSync(new URL('../package.json', import.meta.url), 'utf8');

const missing = [];

for (const token of [
  'activeConfigTab',
  'filteredBatches',
  'filteredGroups',
  'review-config-overview',
  'admin-reused-summary-grid',
  'reused-management-panel',
  'reused-panel-heading',
  'config-tabs',
  'config-tab-pane',
  'reused-toolbar',
  'reused-table-card',
]) {
  if (!panel.includes(token)) {
    missing.push(`ReviewBatchGroupPanel missing ${token}`);
  }
}

if (!panel.includes(':data="filteredBatches"')) {
  missing.push('Batch table must render filteredBatches in the tab layout');
}

if (!panel.includes(':data="filteredGroups"')) {
  missing.push('Group table must render filteredGroups in the tab layout');
}

if (!pkg.includes('check:admin-review-layout')) {
  missing.push('package.json must wire check:admin-review-layout');
}

if (!/review-config-overview[\s\S]*<section class="config-section reused-management-panel"/.test(panel)) {
  missing.push('Review batch/group summary cards must sit above the main reused panel');
}

if (!/\.config-card\s*\{[^}]*min-height:\s*124px/s.test(panel)) {
  missing.push('Review batch/group summary cards must match the approved demo card height');
}

if (!/\.reused-toolbar\s*\{[^}]*grid-template-columns:\s*minmax\(260px,\s*1fr\)\s+200px\s+auto/s.test(panel)) {
  missing.push('Review batch/group toolbar must match the approved demo filter layout');
}

if (panel.includes('listReviewGroups(selectedBatchId.value || undefined)')) {
  missing.push('Group tab must not be implicitly filtered by selectedBatchId');
}

if (!panel.includes('当前展示：全部小组')) {
  missing.push('Group tab must tell admins it is showing all groups');
}

for (const token of [
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

console.log('Admin review batch/group layout uses the reused demo tab layout.');
