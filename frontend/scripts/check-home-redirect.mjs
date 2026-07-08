import { readFileSync } from 'node:fs';

const home = readFileSync(new URL('../src/views/HomeView.vue', import.meta.url), 'utf8');

const missing = [];

if (!home.includes("import { listLeaderGroups } from '../api/reviewLeader';")) {
  missing.push('Home redirect should import listLeaderGroups to detect reviewer leader access');
}

const adminRedirectIndex = home.indexOf("router.replace('/admin')");
const leaderGroupsIndex = home.indexOf('listLeaderGroups()');
const leaderRedirectIndex = home.indexOf("router.replace('/review-leader')");
const reviewRedirectIndex = home.indexOf("router.replace('/review')");

if (adminRedirectIndex === -1) {
  missing.push('Home redirect should keep admin users on /admin');
}

if (leaderGroupsIndex === -1) {
  missing.push('Home redirect should query leader groups before routing reviewers');
}

if (leaderRedirectIndex === -1) {
  missing.push('Home redirect should send reviewers with leader groups to /review-leader');
}

if (reviewRedirectIndex === -1) {
  missing.push('Home redirect should keep non-leader reviewers on /review');
}

if (
  adminRedirectIndex !== -1
  && leaderGroupsIndex !== -1
  && adminRedirectIndex > leaderGroupsIndex
) {
  missing.push('Admin redirect should run before leader group detection');
}

if (
  leaderGroupsIndex !== -1
  && leaderRedirectIndex !== -1
  && reviewRedirectIndex !== -1
  && !(leaderGroupsIndex < leaderRedirectIndex && leaderRedirectIndex < reviewRedirectIndex)
) {
  missing.push('Reviewer redirect should check leader groups, then /review-leader, then fallback to /review');
}

if (!/catch\s*\{[\s\S]*router\.replace\('\/review'\)/.test(home)) {
  missing.push('Leader group detection failure should fallback to /review');
}

if (home.includes('LEAD')) {
  missing.push('Home redirect must not introduce a LEAD system role');
}

if (missing.length) {
  console.error(missing.join('\n'));
  process.exit(1);
}

console.log('首页重定向将审阅负责人路由到负责人工作区。');
