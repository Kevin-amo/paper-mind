import { readFileSync } from 'node:fs';

const files = {
  packageJson: readFileSync(new URL('../package.json', import.meta.url), 'utf8'),
  composer: readFileSync(new URL('../src/components/chat/ChatComposer.vue', import.meta.url), 'utf8'),
  workspace: readFileSync(new URL('../src/components/chat/RagChatWorkspace.vue', import.meta.url), 'utf8'),
  sidebar: readFileSync(new URL('../src/components/chat/ChatSidebar.vue', import.meta.url), 'utf8'),
  userView: readFileSync(new URL('../src/views/UserWorkspaceView.vue', import.meta.url), 'utf8'),
};

const missing = [];

function requireIncludes(fileKey, marker, label = marker) {
  if (!files[fileKey].includes(marker)) {
    missing.push(`${fileKey} missing ${label}`);
  }
}

function requireNotIncludes(fileKey, marker, label = marker) {
  if (files[fileKey].includes(marker)) {
    missing.push(`${fileKey} must not include ${label}`);
  }
}

requireIncludes('packageJson', 'check:chat-more-menu-tools', 'build hook for chat more-menu tools');

requireIncludes('composer', 'openFormatCheck: []', 'format-check emit contract');
requireIncludes('composer', 'openAigcRewrite: []', 'AIGC rewrite emit contract');
requireIncludes('composer', 'function openFormatCheck()', 'format-check menu handler');
requireIncludes('composer', "emit('openFormatCheck')", 'format-check event emission');
requireIncludes('composer', 'function openAigcRewrite()', 'AIGC rewrite menu handler');
requireIncludes('composer', "emit('openAigcRewrite')", 'AIGC rewrite event emission');
requireIncludes('composer', '论文格式校对', 'format-check menu item label');
requireIncludes('composer', 'AIGC 降重', 'AIGC rewrite menu item label');
requireIncludes('composer', '@click="openFormatCheck"', 'format-check menu click binding');
requireIncludes('composer', '@click="openAigcRewrite"', 'AIGC rewrite menu click binding');

requireIncludes('workspace', 'openFormatCheck: []', 'workspace format-check emit contract');
requireIncludes('workspace', 'openAigcRewrite: []', 'workspace AIGC rewrite emit contract');
requireIncludes('workspace', '@open-format-check="emit(\'openFormatCheck\')"', 'workspace format-check event forwarding');
requireIncludes('workspace', '@open-aigc-rewrite="emit(\'openAigcRewrite\')"', 'workspace AIGC rewrite event forwarding');

requireIncludes('userView', '@open-format-check="router.push(\'/format\')"', 'user workspace format-check handler');
requireIncludes('userView', '@open-aigc-rewrite="aigcRewriteVisible = true"', 'user workspace AIGC rewrite handler');

requireNotIncludes('sidebar', 'openFormatCheck', 'left sidebar format-check entry');
requireNotIncludes('sidebar', 'openAigcRewrite', 'left sidebar AIGC rewrite entry');
requireNotIncludes('sidebar', '<span>Format</span>', 'left sidebar Format nav item');
requireNotIncludes('sidebar', '<span>润色</span>', 'left sidebar rewrite nav item');

if (missing.length) {
  console.error(missing.join('\n'));
  process.exit(1);
}

console.log('聊天框更多菜单工具入口已接入格式校对和AIGC降重。');
