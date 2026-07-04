import { readFileSync } from 'node:fs';

const leaderView = readFileSync(new URL('../src/views/review-leader/ReviewLeaderWorkspaceView.vue', import.meta.url), 'utf8');
const issues = [];

const confirmConsensusBody = leaderView.match(/async function confirmConsensus\(\) \{[\s\S]*?\n\}/)?.[0] ?? '';
const saveIndex = confirmConsensusBody.indexOf('updateLeaderTaskConsensus(');
const confirmIndex = confirmConsensusBody.indexOf('confirmLeaderTaskConsensus(');

if (saveIndex === -1) {
  issues.push('Confirming final consensus must persist the current final score and recommendation first.');
}

if (confirmIndex === -1) {
  issues.push('Confirming final consensus must still call the confirm endpoint.');
}

if (saveIndex !== -1 && confirmIndex !== -1 && saveIndex > confirmIndex) {
  issues.push('The current consensus form must be saved before the consensus is confirmed.');
}

if (!confirmConsensusBody.includes('finalScore: consensusForm.finalScore')) {
  issues.push('Confirming final consensus must save the current final score.');
}

if (!confirmConsensusBody.includes('finalRecommendation: consensusForm.finalRecommendation')) {
  issues.push('Confirming final consensus must save the current final recommendation.');
}

if (issues.length) {
  console.error(issues.join('\n'));
  process.exit(1);
}

console.log('Leader final consensus confirmation saves the latest edited score and recommendation first.');
