import { readFileSync } from 'node:fs';

const sidebar = readFileSync(new URL('../src/components/chat/ChatSidebar.vue', import.meta.url), 'utf8');

const requiredSnippets = [
  'class="conversation-dialog danger-dialog claude-delete-dialog claude-workspace-dialog"',
  'width="min(500px, calc(100vw - 32px))"',
  '相关对话内容、检索上下文与当前回答记录都会从历史会话中移除。',
  ':global([class~="claude-delete-dialog"] [class~="el-dialog"])',
  ':global([class~="claude-delete-dialog"] [class~="el-dialog__header"])',
  ':global([class~="el-dialog"][class~="claude-delete-dialog"] [class~="el-button"][class~="delete-confirm-button"])',
  '--el-button-bg-color: var(--app-danger);',
  '@media (max-width: 520px)',
];

const missing = requiredSnippets.filter((snippet) => !sidebar.includes(snippet));

if (missing.length) {
  console.error(`Chat delete dialog is missing Claude-style implementation details:\n${missing.join('\n')}`);
  process.exit(1);
}

console.log('聊天删除会话弹窗使用 Claude 风格视觉样式。');
