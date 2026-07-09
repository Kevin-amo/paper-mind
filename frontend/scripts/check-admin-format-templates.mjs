import { existsSync, readFileSync } from 'node:fs';

function read(path) {
  return readFileSync(new URL(path, import.meta.url), 'utf8');
}

const missing = [];
const router = read('../src/router/index.ts');
const shell = read('../src/components/admin/AdminShell.vue');
const reviewWorkspace = read('../src/views/review/ReviewWorkspaceView.vue');
const pkg = read('../package.json');

const adminApiPath = new URL('../src/api/adminPaperFormat.ts', import.meta.url);
const adminViewPath = new URL('../src/views/admin/AdminPaperFormatTemplatesView.vue', import.meta.url);

if (!existsSync(adminApiPath)) {
  missing.push('admin paper format API file must exist');
}
if (!existsSync(adminViewPath)) {
  missing.push('admin paper format templates view must exist');
}

const adminApi = existsSync(adminApiPath) ? read('../src/api/adminPaperFormat.ts') : '';
const adminView = existsSync(adminViewPath) ? read('../src/views/admin/AdminPaperFormatTemplatesView.vue') : '';

for (const token of [
  "path: '/admin/paper-format-templates'",
  "name: 'admin-paper-format-templates'",
  "meta: { roles: ['ADMIN'] }",
  "AdminPaperFormatTemplatesView.vue",
]) {
  if (!router.includes(token)) {
    missing.push(`router missing ${token}`);
  }
}

for (const token of [
  "type AdminSection = 'users' | 'config' | 'tasks' | 'criteria' | 'templates' | 'audit-logs'",
  "key: 'templates'",
  "path: '/admin/paper-format-templates'",
]) {
  if (!shell.includes(token)) {
    missing.push(`AdminShell missing ${token}`);
  }
}

for (const token of [
  '/admin/paper-format/templates',
  'listAdminFormatTemplates',
  'uploadAdminFormatTemplate',
  'updateAdminFormatTemplate',
  'confirmAdminFormatTemplate',
  'unpublishAdminFormatTemplate',
  'multipart/form-data',
]) {
  if (!adminApi.includes(token)) {
    missing.push(`adminPaperFormat API missing ${token}`);
  }
}

for (const token of [
  'AdminShell',
  'templateTable',
  'uploadDialogVisible',
  'editDialogVisible',
  'detailDrawerVisible',
  'confirmTemplate',
  'unpublishTemplate',
  'formatSpec',
  'extractionReport',
]) {
  if (!adminView.includes(token)) {
    missing.push(`AdminPaperFormatTemplatesView missing ${token}`);
  }
}

for (const token of [
  'callableFormatTemplates',
  'template.status === \'READY\'',
  'template.confirmed',
  'template.publicTemplate',
]) {
  if (!reviewWorkspace.includes(token)) {
    missing.push(`ReviewWorkspaceView missing callable template filter token: ${token}`);
  }
}

if (!pkg.includes('check:admin-format-templates')) {
  missing.push('package.json must wire check:admin-format-templates');
}

if (missing.length) {
  console.error(missing.join('\n'));
  process.exit(1);
}

console.log('Admin paper format template management wiring is present.');
