import { readFileSync } from 'node:fs';

const drawer = readFileSync(new URL('../src/components/documents/DocumentDetailDrawer.vue', import.meta.url), 'utf8');
const packageJson = readFileSync(new URL('../package.json', import.meta.url), 'utf8');
const issues = [];

if (!packageJson.includes('check:document-detail-metadata-hidden')) {
  issues.push('Build should include the document detail metadata visibility check.');
}

const forbiddenTokens = [
  'chunk-extra',
  'formatValue(chunk.metadata)',
  'props.detail.metadata',
  'props.detail.ownerUserId',
  'props.detail.deletedAt',
  '<summary>片段元数据</summary>',
  '<h3>元数据</h3>',
];

for (const token of forbiddenTokens) {
  if (drawer.includes(token)) {
    issues.push(`Document detail drawer must not expose internal metadata token: ${token}`);
  }
}

if (issues.length) {
  console.error(issues.join('\n'));
  process.exit(1);
}

console.log('Document detail drawer hides internal metadata from ordinary users.');
