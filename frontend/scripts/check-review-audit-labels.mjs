import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { dirname, resolve } from 'node:path';

const __dirname = dirname(fileURLToPath(import.meta.url));
const componentPath = resolve(__dirname, '../src/views/review/components/ReviewAuditTab.vue');
const source = readFileSync(componentPath, 'utf8');

const requiredActions = [
  'CREATE_TASK',
  'AI_REVIEW',
  'ADJUST_REPORT',
  'ASSIGN',
  'ASSIGN_BY_ADMIN_OVERRIDE',
  'ASSIGN_BY_LEADER',
  'JOIN_REVIEW_BY_LEADER',
  'DISPATCH_TO_GROUP',
  'RETURN',
  'CANCEL_ASSIGNMENT',
  'SUBMIT',
  'SUBMIT_ASSIGNMENT',
  'UPDATE_CONSENSUS',
  'CONFIRM_CONSENSUS',
  'RECALCULATE_CONSENSUS',
];

const missing = requiredActions.filter((action) => !source.includes(`${action}:`));

if (missing.length > 0) {
  console.error(`Missing review audit action labels: ${missing.join(', ')}`);
  process.exit(1);
}

console.log('Review audit action labels cover known audit actions.');
