import { readFileSync } from 'node:fs';

const usersPanel = readFileSync(new URL('../src/components/admin/AdminUsersPanel.vue', import.meta.url), 'utf8');

const missing = [];

for (const token of [
  'admin-users-overview',
  'admin-reused-summary-grid',
  'reused-management-panel',
  'reused-panel-heading',
  'reused-toolbar',
  'reused-table-card',
]) {
  if (!usersPanel.includes(token)) {
    missing.push(`User management panel must reuse demo layout token: ${token}`);
  }
}

const overviewIndex = usersPanel.indexOf('admin-users-overview');
const panelIndex = usersPanel.indexOf('paper-mind-workspace-card users-panel');
if (overviewIndex === -1 || panelIndex === -1 || overviewIndex > panelIndex) {
  missing.push('User management summary cards must sit above the main reused panel');
}

if (!/\.table-wrapper\s*\{[^}]*overflow-x:\s*auto/s.test(usersPanel)) {
  missing.push('User management table wrapper must own horizontal scrolling locally');
}

if (!/\.users-panel\s*\{[^}]*overflow-x:\s*hidden/s.test(usersPanel)) {
  missing.push('User management panel must contain table overflow so the page content does not shift on refresh');
}

if (!/\.users-table\s*\{[^}]*min-width:\s*1160px/s.test(usersPanel)) {
  missing.push('User management table must keep its minimum column layout inside the local scroll area');
}

if (!/\.summary-card\s*\{[^}]*min-height:\s*124px/s.test(usersPanel)) {
  missing.push('User management summary cards must match the approved demo card height');
}

if (!/\.reused-toolbar\s*\{[^}]*grid-template-columns:\s*minmax\(260px,\s*1fr\)\s+200px\s+auto\s+auto/s.test(usersPanel)) {
  missing.push('User management toolbar must match the approved demo filter layout');
}

if (missing.length) {
  console.error(missing.join('\n'));
  process.exit(1);
}

console.log('Admin users reused demo layout static checks passed.');
