import { readFileSync } from 'node:fs';

const reviewView = readFileSync(new URL('../src/views/review/ReviewWorkspaceView.vue', import.meta.url), 'utf8');

if (!/const\s+activeReviewTab\s*=\s*ref<ReviewTabKey>\('parse'\);/.test(reviewView)) {
  throw new Error('Review workspace must default to the paper overview tab.');
}

console.log('评审工作台默认显示论文概览。');
