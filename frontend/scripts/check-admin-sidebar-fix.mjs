/**
 * 检查管理后台侧边栏固定定位修复
 */

import fs from 'fs';
import path from 'path';
import { fileURLToPath } from 'url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

const adminShellPath = path.join(__dirname, '../src/components/admin/AdminShell.vue');

console.log(' 检查 AdminShell.vue 侧边栏定位修复...\n');

const content = fs.readFileSync(adminShellPath, 'utf-8');

// 检查关键修复点
const checks = [
  {
    name: '.admin-layout 设置 height: 100vh',
    pattern: /\.admin-layout\s*{[^}]*height:\s*100vh/,
    found: false,
  },
  {
    name: '.admin-sidebar 设置 align-self: start',
    pattern: /\.admin-sidebar\s*{[^}]*align-self:\s*start/,
    found: false,
  },
  {
    name: '.admin-sidebar 设置 max-height: 100vh',
    pattern: /\.admin-sidebar\s*{[^}]*max-height:\s*100vh/,
    found: false,
  },
  {
    name: '.admin-main 设置 overflow: hidden',
    pattern: /\.admin-main\s*{[^}]*overflow:\s*hidden/,
    found: false,
  },
  {
    name: '.admin-content 设置 overflow-y: auto',
    pattern: /\.admin-content\s*{[^}]*overflow-y:\s*auto/,
    found: false,
  },
  {
    name: '.admin-content 移除 overflow-x: auto',
    pattern: /\.admin-content\s*{[^}]*overflow-x:\s*auto/,
    found: true, // 应该找不到
  },
  {
    name: '.admin-content 设置 flex: 1',
    pattern: /\.admin-content\s*{[^}]*flex:\s*1/,
    found: false,
  },
];

checks.forEach((check) => {
  check.found = check.pattern.test(content);
});

let allPassed = true;

checks.forEach((check, index) => {
  const status = check.name.includes('移除') ? !check.found : check.found;
  const icon = status ? '✅' : '';
  console.log(`${icon} ${index + 1}. ${check.name}`);
  if (!status) allPassed = false;
});

console.log('\n' + '='.repeat(60));

if (allPassed) {
  console.log('✅ 所有检查通过！侧边栏定位修复已完成。');
  console.log('\n修复要点：');
  console.log('  • .admin-layout 限制高度为 100vh，防止整体页面滚动');
  console.log('  • .admin-sidebar 使用 position: sticky + align-self: start 保持固定');
  console.log('  • .admin-main 设置 overflow: hidden，隔离内部滚动');
  console.log('  • .admin-content 独立处理垂直滚动（overflow-y: auto）');
  console.log('  • 移除 .admin-content 的 overflow-x: auto，避免横向滚动条');
  process.exit(0);
} else {
  console.log('❌ 部分检查未通过，请复查代码。');
  process.exit(1);
}
