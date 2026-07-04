<script lang="ts">
export default {
  name: 'ChatSidebar',
};
</script>

<script setup lang="ts">
import { computed, nextTick, ref } from 'vue';
import {
  ChatDotRound,
  Collection,
  DocumentAdd,
  Delete,
  EditPen,
  MoreFilled,
  Plus,
  SwitchButton,
  Star,
  User,
  Setting,
  Warning,
} from '@element-plus/icons-vue';
import { ElMessage } from 'element-plus';
import type { Conversation } from '../../types';

const PINNED_CONVERSATIONS_STORAGE_KEY = 'paper-mind:pinned-conversations';

type ConversationMenuCommand = 'pin' | 'unpin' | 'rename' | 'delete';

const props = defineProps<{
  conversations: Conversation[];
  activeConversationId: string | null;
  conversationsLoading?: boolean;
  currentUserName: string;
  currentUserAvatarUrl?: string | null;
  isAdmin?: boolean;
}>();

const emit = defineEmits<{
  createConversation: [];
  selectConversation: [conversationId: string];
  deleteConversation: [conversationId: string];
  renameConversation: [conversationId: string, title: string];
  openDocuments: [];
  openReviewSubmissions: [];
  goAdmin: [];
  openAccountManagement: [];
  logout: [];
}>();

const pinnedConversationIds = ref<string[]>(loadPinnedConversationIds());
const deleteDialogVisible = ref(false);
const logoutDialogVisible = ref(false);
const operatingConversation = ref<Conversation | null>(null);
const editingConversationId = ref<string | null>(null);
const renameTitle = ref('');
const renameInputRef = ref<HTMLInputElement | null>(null);

const sortedConversations = computed(() => [...props.conversations].sort((a, b) => {
  const aPinned = isPinned(a.id);
  const bPinned = isPinned(b.id);

  if (aPinned !== bPinned) {
    return aPinned ? -1 : 1;
  }

  return new Date(b.updatedAt).getTime() - new Date(a.updatedAt).getTime();
}));

function loadPinnedConversationIds() {
  try {
    const rawValue = localStorage.getItem(PINNED_CONVERSATIONS_STORAGE_KEY);
    if (!rawValue) {
      return [];
    }

    const parsedValue = JSON.parse(rawValue);
    return Array.isArray(parsedValue)
      ? parsedValue.filter((item): item is string => typeof item === 'string')
      : [];
  } catch {
    return [];
  }
}

function savePinnedConversationIds() {
  try {
    localStorage.setItem(PINNED_CONVERSATIONS_STORAGE_KEY, JSON.stringify(pinnedConversationIds.value));
  } catch {
    // ignore storage failures
  }
}

function isPinned(conversationId: string) {
  return pinnedConversationIds.value.includes(conversationId);
}

function togglePinned(conversationId: string) {
  if (isPinned(conversationId)) {
    pinnedConversationIds.value = pinnedConversationIds.value.filter((id) => id !== conversationId);
  } else {
    pinnedConversationIds.value = [conversationId, ...pinnedConversationIds.value.filter((id) => id !== conversationId)];
  }
  savePinnedConversationIds();
}

function handleDropdownCommand(conversation: Conversation, command: string | number | object) {
  if (command === 'pin' || command === 'unpin' || command === 'rename' || command === 'delete') {
    handleMenuCommand(conversation, command);
  }
}

function handleMenuCommand(conversation: Conversation, command: ConversationMenuCommand) {
  if (command === 'pin' || command === 'unpin') {
    togglePinned(conversation.id);
    return;
  }

  if (command === 'rename') {
    startInlineRename(conversation);
    return;
  }

  cancelInlineRename();
  operatingConversation.value = conversation;
  deleteDialogVisible.value = true;
}

async function startInlineRename(conversation: Conversation) {
  operatingConversation.value = conversation;
  editingConversationId.value = conversation.id;
  renameTitle.value = conversationTitle(conversation);
  await nextTick();
  renameInputRef.value?.focus();
  renameInputRef.value?.select();
}

function confirmRename() {
  if (!operatingConversation.value) {
    return;
  }

  const normalizedTitle = renameTitle.value.trim();
  if (!normalizedTitle) {
    ElMessage.warning('会话名称不能为空');
    return;
  }

  if (normalizedTitle !== conversationTitle(operatingConversation.value)) {
    emit('renameConversation', operatingConversation.value.id, normalizedTitle);
  }

  editingConversationId.value = null;
  operatingConversation.value = null;
  renameTitle.value = '';
}

function cancelInlineRename() {
  editingConversationId.value = null;
  operatingConversation.value = null;
  renameTitle.value = '';
}

function confirmDelete() {
  if (!operatingConversation.value) {
    return;
  }

  emit('deleteConversation', operatingConversation.value.id);
  deleteDialogVisible.value = false;
  operatingConversation.value = null;
}

function closeOperationDialog() {
  deleteDialogVisible.value = false;
  operatingConversation.value = null;
}

function confirmLogout() {
  logoutDialogVisible.value = false;
  emit('logout');
}

function conversationTitle(conversation: Conversation) {
  return conversation.title?.trim() || '新的论文问答';
}
</script>

<template>
  <aside class="chat-sidebar claude-chat-sidebar paper-mind-workspace-panel">
    <div class="sidebar-brand">
      <button class="brand-mark" type="button" @click="emit('createConversation')">
        <span aria-hidden="true">P</span>
      </button>
      <div class="brand-copy">
        <strong>PaperMind</strong>
        <span>论文智能助手</span>
      </div>
    </div>

    <button class="new-chat-button" type="button" @click="emit('createConversation')">
      <el-icon><Plus /></el-icon>
      <span>新对话</span>
    </button>

    <nav class="sidebar-nav">
      <button class="nav-item active" type="button">
        <el-icon><ChatDotRound /></el-icon>
        <span>Chats</span>
      </button>
      <button class="nav-item" type="button" @click="emit('openDocuments')">
        <el-icon><Collection /></el-icon>
        <span>Documents</span>
      </button>
      <button class="nav-item" type="button" @click="emit('openReviewSubmissions')">
        <el-icon><DocumentAdd /></el-icon>
        <span>Submissions</span>
      </button>
      <button v-if="props.isAdmin" class="nav-item" type="button" @click="emit('goAdmin')">
        <el-icon><Setting /></el-icon>
        <span>Admin</span>
      </button>
    </nav>

    <section class="conversation-section">
      <div class="section-title">
        <span>历史会话</span>
      </div>

      <div v-loading="props.conversationsLoading" class="conversation-list">
        <el-empty v-if="!sortedConversations.length" description="暂无会话" :image-size="72" />
        <div
          v-for="conversation in sortedConversations"
          v-else
          :key="conversation.id"
          class="conversation-item"
          :class="{ active: conversation.id === props.activeConversationId, pinned: isPinned(conversation.id) }"
        >
          <template v-if="editingConversationId === conversation.id">
            <input
              ref="renameInputRef"
              v-model="renameTitle"
              class="conversation-rename-input"
              maxlength="60"
              @click.stop
              @keydown.enter.prevent="confirmRename"
              @keydown.esc.prevent="cancelInlineRename"
              @blur="cancelInlineRename"
            >
          </template>
          <button v-else type="button" class="conversation-select" @click="emit('selectConversation', conversation.id)">
            <span class="conversation-title-row">
              <span class="conversation-title">{{ conversationTitle(conversation) }}</span>
              <span v-if="isPinned(conversation.id)" class="conversation-pin-badge" aria-label="已置顶" title="已置顶">
                <el-icon><Star /></el-icon>
              </span>
            </span>
          </button>

          <el-dropdown
            trigger="click"
            popper-class="conversation-action-popper"
            @command="handleDropdownCommand(conversation, $event)"
          >
            <el-button
              text
              circle
              class="conversation-menu-button"
              :aria-label="isPinned(conversation.id) ? '会话菜单：已置顶' : '会话菜单：未置顶'"
              @click.stop
            >
              <el-icon><MoreFilled /></el-icon>
            </el-button>
            <template #dropdown>
              <el-dropdown-menu class="conversation-action-menu">
                <el-dropdown-item :command="isPinned(conversation.id) ? 'unpin' : 'pin'" class="conversation-action-item">
                  <el-icon><Star /></el-icon>
                  <span>{{ isPinned(conversation.id) ? '取消置顶' : '置顶会话' }}</span>
                </el-dropdown-item>
                <el-dropdown-item command="rename" class="conversation-action-item">
                  <el-icon><EditPen /></el-icon>
                  <span>重命名</span>
                </el-dropdown-item>
                <el-dropdown-item command="delete" class="conversation-action-item danger">
                  <el-icon><Delete /></el-icon>
                  <span>删除会话</span>
                </el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </div>
    </section>

    <footer class="user-footer">
      <button class="user-avatar" type="button" title="账户管理" @click="emit('openAccountManagement')">
        <img v-if="props.currentUserAvatarUrl" :src="props.currentUserAvatarUrl" alt="用户头像">
        <el-icon v-else><User /></el-icon>
      </button>
      <div class="user-meta">
        <strong>{{ props.currentUserName }}</strong>
      </div>
      <el-button circle text :icon="SwitchButton" title="退出登录" @click="logoutDialogVisible = true" />
    </footer>

    <el-dialog
      v-model="logoutDialogVisible"
      title=""
      width="min(420px, 92vw)"
      class="logout-confirm-dialog claude-workspace-dialog"
      append-to-body
      align-center
      :close-on-click-modal="false"
    >
      <div class="logout-confirm-body">
        <div class="logout-confirm-icon" aria-hidden="true">
          <el-icon :size="28"><Warning /></el-icon>
        </div>
        <h3 class="logout-confirm-title">确认退出当前账号吗？</h3>
        <p class="logout-confirm-desc">退出后需要重新登录，才能继续使用论文助手。</p>
      </div>
      <template #footer>
        <div class="logout-confirm-footer">
          <el-button class="logout-cancel-button" @click="logoutDialogVisible = false">取消</el-button>
          <el-button class="logout-confirm-button" type="danger" @click="confirmLogout">确认退出</el-button>
        </div>
      </template>
    </el-dialog>

    <el-dialog
      v-model="deleteDialogVisible"
      title="删除会话"
      width="min(500px, calc(100vw - 32px))"
      class="conversation-dialog danger-dialog claude-delete-dialog claude-workspace-dialog"
      append-to-body
      align-center
      @closed="closeOperationDialog"
    >
      <div class="conversation-dialog-body">
        <strong>确认删除这个问答会话吗？</strong>
        <span class="dialog-caption">“{{ operatingConversation ? conversationTitle(operatingConversation) : '' }}” 删除后不可恢复。相关对话内容、检索上下文与当前回答记录都会从历史会话中移除。</span>
      </div>
      <template #footer>
        <el-button @click="deleteDialogVisible = false">取消</el-button>
        <el-button class="delete-confirm-button" type="danger" @click="confirmDelete">删除</el-button>
      </template>
    </el-dialog>
  </aside>
</template>

<style scoped>
.chat-sidebar {
  display: flex;
  flex-direction: column;
  gap: 16px;
  min-width: 0;
  height: calc(100vh - 36px);
  padding: 18px 14px;
  overflow: hidden;
  background: var(--app-surface);
}

.sidebar-brand {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 4px 8px 10px;
}

.brand-mark {
  display: grid;
  place-items: center;
  width: 42px;
  height: 42px;
  border: 1px solid var(--app-border);
  border-radius: 50%;
  background:
    linear-gradient(var(--app-text), var(--app-text)) center / 2px 22px no-repeat,
    linear-gradient(90deg, var(--app-text), var(--app-text)) center / 22px 2px no-repeat,
    linear-gradient(45deg, transparent 46%, var(--app-text) 47%, var(--app-text) 53%, transparent 54%) center / 22px 22px no-repeat,
    linear-gradient(-45deg, transparent 46%, var(--app-text) 47%, var(--app-text) 53%, transparent 54%) center / 22px 22px no-repeat,
    var(--app-surface);
  color: transparent;
  cursor: pointer;
}

.brand-copy strong,
.user-meta strong {
  display: block;
  color: var(--app-text);
  font-size: 18px;
  font-weight: 500;
}

.brand-copy span {
  display: block;
  margin-top: 2px;
  color: var(--app-text-muted);
  font-size: 12px;
}

.new-chat-button {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  width: 100%;
  height: 46px;
  border: 1px solid var(--app-border);
  border-radius: var(--app-radius-md);
  background: var(--app-surface-soft);
  color: var(--app-text);
  cursor: pointer;
  font-size: 14px;
  font-weight: 500;
}

.sidebar-nav {
  display: grid;
  gap: 4px;
}

.nav-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 12px;
  border: 1px solid transparent;
  border-radius: var(--app-radius-md);
  background: transparent;
  color: var(--app-text-muted);
  cursor: pointer;
  text-align: left;
}

.nav-item.active,
.nav-item:hover {
  border-color: var(--app-border);
  background: var(--app-surface-soft);
  color: var(--app-text);
}

.conversation-section {
  display: flex;
  flex: 1;
  min-height: 0;
  flex-direction: column;
  gap: 8px;
}

.section-title {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 6px 8px 0;
  color: var(--app-text-muted);
  font-size: 12px;
  font-weight: 500;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.conversation-list {
  display: flex;
  flex: 1;
  flex-direction: column;
  gap: 4px;
  min-height: 0;
  overflow-y: auto;
  padding-right: 2px;
}

.conversation-list :deep(.el-empty) {
  padding: 18px 0;
}

.conversation-item {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  align-items: center;
  gap: 2px;
  min-height: 42px;
  padding: 4px 5px 4px 10px;
  border: 1px solid transparent;
  border-radius: var(--app-radius-md);
  background: transparent;
}

.conversation-item:hover {
  border-color: var(--app-border);
  background: var(--app-surface-soft);
}

.conversation-item.active {
  border-color: var(--app-primary);
  background: var(--app-primary-soft);
}

.conversation-select {
  min-width: 0;
  min-height: 30px;
  border: 0;
  background: transparent;
  color: inherit;
  text-align: left;
  cursor: pointer;
  padding: 0;
}

.conversation-rename-input {
  width: 100%;
  min-width: 0;
  height: 30px;
  border: 1px solid var(--app-primary);
  border-radius: var(--app-radius-sm);
  background: var(--app-surface);
  color: var(--app-text);
  outline: none;
  padding: 0 8px;
  font-size: 13px;
  font-weight: 500;
  box-shadow: 0 0 0 3px rgba(204, 120, 92, 0.16);
}

.conversation-title-row {
  display: flex;
  align-items: center;
  gap: 7px;
  min-width: 0;
}

.conversation-title {
  display: block;
  overflow: hidden;
  color: var(--app-text);
  font-size: 13px;
  font-weight: 500;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.conversation-pin-badge {
  flex: none;
  display: inline-grid;
  place-items: center;
  width: 20px;
  height: 20px;
  border-radius: 50%;
  background: var(--app-primary-soft);
  color: var(--app-primary);
  font-size: 12px;
  line-height: 1;
}

.conversation-menu-button {
  flex: none;
  width: 28px;
  height: 28px;
  color: var(--app-text-subtle);
  opacity: 0;
}

.conversation-item:hover .conversation-menu-button,
.conversation-item.active .conversation-menu-button {
  opacity: 1;
}

.conversation-menu-button:hover {
  color: var(--app-text);
  background: var(--app-surface-soft);
}

:global([class~="conversation-action-popper"]) {
  overflow: hidden;
  border: 1px solid var(--app-border) !important;
  border-radius: var(--app-radius-lg) !important;
  background: var(--app-surface) !important;
  box-shadow: var(--app-shadow-lg) !important;
}

:global([class~="conversation-action-popper"] [class~="el-popper__arrow"]) {
  display: none;
}

:global([class~="conversation-action-menu"]) {
  min-width: 156px;
  padding: 6px;
}

:global([class~="conversation-action-menu"] [class~="el-dropdown-menu__item"]) {
  display: flex;
  align-items: center;
  gap: 9px;
  height: 36px;
  padding: 0 10px;
  border-radius: var(--app-radius-md);
  color: var(--app-text-muted);
  font-size: 13px;
  font-weight: 500;
}

:global([class~="conversation-action-menu"] [class~="el-dropdown-menu__item"]:not([class~="is-disabled"]):focus),
:global([class~="conversation-action-menu"] [class~="el-dropdown-menu__item"]:not([class~="is-disabled"]):hover) {
  background: var(--app-surface-soft);
  color: var(--app-text);
}

:global([class~="conversation-action-menu"] [class~="el-dropdown-menu__item"][class~="danger"]) {
  color: var(--app-danger);
}

:global([class~="conversation-action-menu"] [class~="el-dropdown-menu__item"][class~="danger"]:not([class~="is-disabled"]):focus),
:global([class~="conversation-action-menu"] [class~="el-dropdown-menu__item"][class~="danger"]:not([class~="is-disabled"]):hover) {
  background: var(--app-danger-soft);
  color: var(--app-danger);
}

.conversation-dialog-body {
  display: grid;
  gap: 18px;
}

.conversation-dialog-body strong {
  color: var(--app-text);
  font-size: 18px;
  font-weight: 760;
  line-height: 1.45;
}

.dialog-caption {
  color: var(--app-text-muted);
  font-size: 15px;
  line-height: 1.75;
}

:global([class~="claude-delete-dialog"] [class~="el-dialog"]) {
  overflow: hidden;
  border: 1px solid rgba(230, 223, 216, 0.94);
  border-radius: var(--app-radius-xl);
  background: var(--app-surface);
  box-shadow: 0 26px 70px rgba(23, 22, 20, 0.24);
}

:global([class~="claude-delete-dialog"] [class~="el-dialog__header"]) {
  display: flex;
  align-items: center;
  justify-content: space-between;
  min-height: 0;
  margin-right: 0;
  padding: 20px 22px 18px 30px;
  border-bottom: 1px solid var(--app-border);
}

:global([class~="claude-delete-dialog"] [class~="el-dialog__title"]) {
  color: var(--app-text);
  font-family: Georgia, "Times New Roman", "Microsoft YaHei", serif;
  font-size: 30px;
  font-weight: 500;
  line-height: 1.15;
  letter-spacing: 0;
}

:global([class~="claude-delete-dialog"] [class~="el-dialog__headerbtn"]) {
  position: static;
  display: grid;
  place-items: center;
  width: 36px;
  height: 36px;
  border: 1px solid transparent;
  border-radius: var(--app-radius-sm);
  background: transparent;
  color: var(--app-text-subtle);
  transition: color var(--app-transition-fast), background-color var(--app-transition-fast), border-color var(--app-transition-fast);
}

:global([class~="claude-delete-dialog"] [class~="el-dialog__headerbtn"]:hover) {
  border-color: var(--app-border);
  background: var(--app-surface-strong);
  color: var(--app-text);
}

:global([class~="claude-delete-dialog"] [class~="el-dialog__body"]) {
  padding: 20px 30px 20px;
}

:global([class~="claude-delete-dialog"] [class~="el-dialog__footer"]) {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
  padding: 15px 30px 10px;
  border-top: 1px solid var(--app-border);
}

:global([class~="claude-delete-dialog"] [class~="el-button"]) {
  min-width: 76px;
  min-height: 42px;
  margin-left: 0;
  border-radius: var(--app-radius-sm);
  font-weight: 680;
  transition: background-color var(--app-transition-fast), border-color var(--app-transition-fast), color var(--app-transition-fast), box-shadow var(--app-transition-fast);
}

:global([class~="el-dialog"][class~="claude-delete-dialog"] [class~="el-button"][class~="delete-confirm-button"]) {
  --el-button-bg-color: var(--app-danger);
  --el-button-border-color: var(--app-danger);
  --el-button-text-color: var(--app-text-on-primary);
  --el-button-hover-bg-color: var(--app-danger-hover);
  --el-button-hover-border-color: var(--app-danger-hover);
  --el-button-hover-text-color: var(--app-text-on-primary);
  --el-button-active-bg-color: var(--app-danger-hover);
  --el-button-active-border-color: var(--app-danger-hover);
  --el-button-active-text-color: var(--app-text-on-primary);
}

@media (max-width: 520px) {
  :global([class~="claude-delete-dialog"] [class~="el-dialog__header"]),
  :global([class~="claude-delete-dialog"] [class~="el-dialog__body"]),
  :global([class~="claude-delete-dialog"] [class~="el-dialog__footer"]) {
    padding-right: 20px;
    padding-left: 20px;
  }

  :global([class~="claude-delete-dialog"] [class~="el-dialog__footer"]) {
    flex-direction: column-reverse;
  }

  :global([class~="claude-delete-dialog"] [class~="el-button"]) {
    width: 100%;
  }
}

.logout-confirm-dialog :deep(.el-dialog__header) {
  display: none;
}

.logout-confirm-dialog :deep(.el-dialog__body) {
  padding: 32px 28px 24px;
}

.logout-confirm-dialog :deep(.el-dialog__footer) {
  padding: 0 28px 28px;
  border-top: 0;
}

.logout-confirm-body {
  display: flex;
  flex-direction: column;
  align-items: center;
  text-align: center;
  gap: 14px;
}

.logout-confirm-icon {
  display: grid;
  place-items: center;
  width: 64px;
  height: 64px;
  border-radius: var(--app-radius-xl);
  color: var(--app-danger);
  background: var(--app-danger-soft);
  box-shadow: 0 0 0 6px rgba(209, 69, 59, 0.04);
}

.logout-confirm-title {
  margin: 0;
  color: var(--app-text);
  font-family: "Cormorant Garamond", "EB Garamond", Georgia, serif;
  font-size: 18px;
  font-weight: 500;
  line-height: 1.35;
}

.logout-confirm-desc {
  margin: 0;
  max-width: 320px;
  color: var(--app-text-muted);
  font-size: 14px;
  line-height: 1.6;
}

.logout-confirm-footer {
  display: flex;
  justify-content: center;
  gap: 12px;
  width: 100%;
}

.logout-confirm-footer :deep(.el-button) {
  min-width: 104px;
  height: 40px;
  border-radius: var(--app-radius-md);
  font-size: 14px;
  font-weight: 600;
}

.logout-confirm-footer .logout-cancel-button {
  --el-button-hover-bg-color: var(--app-surface-soft);
  --el-button-hover-border-color: var(--app-border-strong);
  --el-button-hover-text-color: var(--app-text);
}

.logout-confirm-footer .logout-confirm-button {
  --el-button-bg-color: var(--app-danger);
  --el-button-border-color: var(--app-danger);
  --el-button-text-color: var(--app-text-on-primary);
  --el-button-hover-bg-color: var(--app-danger-hover);
  --el-button-hover-border-color: var(--app-danger-hover);
  --el-button-hover-text-color: var(--app-text-on-primary);
  --el-button-active-bg-color: #a03028;
  --el-button-active-border-color: #a03028;
  --el-button-active-text-color: var(--app-text-on-primary);
  transition: background var(--app-transition-fast), border-color var(--app-transition-fast);
}

.user-footer {
  display: grid;
  grid-template-columns: 38px minmax(0, 1fr) auto;
  align-items: center;
  gap: 10px;
  margin-top: auto;
  padding: 10px;
  border: 1px solid var(--app-border);
  border-radius: var(--app-radius-lg);
  background: var(--app-surface-soft);
}

.user-avatar {
  display: grid;
  place-items: center;
  width: 38px;
  height: 38px;
  overflow: hidden;
  border: 0;
  border-radius: 50%;
  color: var(--app-primary);
  background: rgba(204, 120, 92, 0.14);
  cursor: pointer;
  padding: 0;
}

.user-avatar img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.user-meta {
  min-width: 0;
}

.user-meta strong {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

:global(.chat-sidebar .el-button.is-text) {
  color: var(--app-text-muted);
}

:global(.chat-sidebar .el-button.is-text:hover) {
  color: var(--app-text);
  background: var(--app-surface-soft);
}

@media (max-width: 900px) {
  .chat-sidebar {
    width: 100%;
    height: auto;
    max-height: 46vh;
    border-radius: 26px;
  }

  .sidebar-nav {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }
}

@media (max-width: 640px) {
  .chat-sidebar {
    padding: 12px;
    border-radius: 24px;
  }

  .sidebar-nav {
    grid-template-columns: 1fr;
  }
}
</style>
