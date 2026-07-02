<script lang="ts">
export default {
  name: 'AdminUsersPanel',
};
</script>

<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import { Avatar, CircleClose, EditPen, MoreFilled, Refresh, User, UserFilled } from '@element-plus/icons-vue';
import StatusTag from '../common/StatusTag.vue';
import RoleTag from '../common/RoleTag.vue';
import { useAdminUsers } from '../../composables/useAdminUsers';
import type { AdminUser, UserRole, UserStatus } from '../../types';

type UserActionCommand = 'edit' | 'reset-password' | 'delete';

const admin = useAdminUsers();
const roleOptions: Array<{ label: string; value: UserRole }> = [
  { label: '管理员', value: 'ADMIN' },
  { label: '评审员', value: 'REVIEWER' },
  { label: '普通用户', value: 'USER' },
];
const statusOptions: Array<{ label: string; value: UserStatus }> = [
  { label: '启用', value: 'ACTIVE' },
  { label: '禁用', value: 'DISABLED' },
];
const roleEditorUserId = ref<string | null>(null);
const statusEditorUserId = ref<string | null>(null);
const roleDraft = ref<UserRole[]>([]);
const statusDraft = ref<UserStatus>('ACTIVE');
const inlineSavingUserId = ref<string | null>(null);

onMounted(() => {
  if (!admin.loaded.value) {
    void admin.loadUsers(0);
  }
  window.addEventListener('click', closeInlineEditors);
});

onBeforeUnmount(() => {
  window.removeEventListener('click', closeInlineEditors);
});

function formatDate(value: string | null) {
  return value ? new Date(value).toLocaleString() : '-';
}

function closeInlineEditors() {
  closeRoleEditor();
  closeStatusEditor();
}

function openRoleEditor(user: AdminUser) {
  statusEditorUserId.value = null;
  roleDraft.value = [...user.roles];
  roleEditorUserId.value = user.id;
}

function closeRoleEditor() {
  roleEditorUserId.value = null;
  roleDraft.value = [];
}

async function saveRoleEditor(user: AdminUser) {
  if (!roleDraft.value.length) {
    ElMessage.warning('请至少保留一个角色');
    return;
  }
  inlineSavingUserId.value = user.id;
  try {
    await admin.changeRoles(user, [...roleDraft.value]);
    closeRoleEditor();
  } finally {
    inlineSavingUserId.value = null;
  }
}

function openStatusEditor(user: AdminUser) {
  roleEditorUserId.value = null;
  statusDraft.value = user.status;
  statusEditorUserId.value = user.id;
}

function closeStatusEditor() {
  statusEditorUserId.value = null;
}

async function saveStatusEditor(user: AdminUser) {
  inlineSavingUserId.value = user.id;
  try {
    await admin.changeStatus(user, statusDraft.value);
    closeStatusEditor();
  } finally {
    inlineSavingUserId.value = null;
  }
}

async function handleUserAction(command: UserActionCommand, user: AdminUser) {
  closeInlineEditors();
  if (command === 'edit') {
    admin.openEditDialog(user);
    return;
  }
  if (command === 'reset-password') {
    admin.openPasswordDialog(user);
    return;
  }

  try {
    await ElMessageBox.confirm('确认删除这个用户吗？此操作不可恢复。', '删除用户', {
      confirmButtonText: '删除',
      cancelButtonText: '取消',
      confirmButtonClass: 'danger-confirm-button',
      type: 'warning',
    });
    await admin.removeUser(user);
  } catch {
    // User cancelled the confirmation dialog.
  }
}

function handlePageSizeChange(nextSize: number) {
  admin.pagination.size = nextSize;
  void admin.loadUsers(0);
}
</script>

<template>
  <section class="admin-users-overview admin-reused-summary-grid" aria-label="用户概览">
    <div
      class="summary-card animate slide-up"
      v-animate="{ type: 'slide-up', delay: '0ms', duration: '0.6s' }"
    >
      <div class="summary-icon coral">
        <el-icon :size="20"><User /></el-icon>
      </div>
      <div class="summary-body">
        <span class="summary-label">当前页用户</span>
        <strong class="summary-value">{{ admin.users.value.length }}</strong>
      </div>
    </div>
    <div
      class="summary-card animate slide-up"
      v-animate="{ type: 'slide-up', delay: '80ms', duration: '0.6s' }"
    >
      <div class="summary-icon green">
        <el-icon :size="20"><UserFilled /></el-icon>
      </div>
      <div class="summary-body">
        <span class="summary-label">启用账号</span>
        <strong class="summary-value">{{ admin.activeCount.value }}</strong>
      </div>
    </div>
    <div
      class="summary-card animate slide-up"
      v-animate="{ type: 'slide-up', delay: '160ms', duration: '0.6s' }"
    >
      <div class="summary-icon teal">
        <el-icon :size="20"><Avatar /></el-icon>
      </div>
      <div class="summary-body">
        <span class="summary-label">管理员</span>
        <strong class="summary-value">{{ admin.adminCount.value }}</strong>
      </div>
    </div>
    <div
      class="summary-card animate slide-up"
      v-animate="{ type: 'slide-up', delay: '240ms', duration: '0.6s' }"
    >
      <div class="summary-icon red">
        <el-icon :size="20"><CircleClose /></el-icon>
      </div>
      <div class="summary-body">
        <span class="summary-label">禁用账号</span>
        <strong class="summary-value">{{ admin.disabledCount.value }}</strong>
      </div>
    </div>
  </section>

  <section
    class="paper-mind-workspace-card users-panel animate fade-in reused-management-panel"
    v-animate="{ type: 'fade-in', delay: '0.1s', duration: '0.6s' }"
  >
    <div class="reused-panel-heading">
      <div>
        <h2>用户列表</h2>
        <p>管理账号、角色、状态和密码重置；复用全局进度页的卡片、筛选栏、表格和分页结构。</p>
      </div>
      <el-button type="primary" class="primary-action" @click="admin.openCreateDialog">新建用户</el-button>
    </div>

    <div class="reused-toolbar">
      <el-input
        v-model="admin.keyword.value"
        clearable
        placeholder="搜索用户名 / 昵称 / 邮箱"
        @keyup.enter="admin.loadUsers(0)"
      />
      <el-select v-model="admin.statusFilter.value" clearable placeholder="状态" class="status-filter">
        <el-option label="启用" value="ACTIVE" />
        <el-option label="禁用" value="DISABLED" />
      </el-select>
      <el-button @click="admin.loadUsers(0)">搜索</el-button>
      <el-button @click="admin.loadUsers(admin.pagination.page)">
        <el-icon :class="{ 'is-rotating': admin.loading.value }"><Refresh /></el-icon>
        刷新
      </el-button>
    </div>

    <div class="reused-table-card table-wrapper" :class="{ 'is-loading': admin.loading.value }">
      <div v-if="admin.loading.value && admin.users.value.length === 0" class="skeleton-container">
        <div v-for="i in 5" :key="i" class="skeleton-row">
          <div class="skeleton-cell skeleton-user"></div>
          <div class="skeleton-cell skeleton-email"></div>
          <div class="skeleton-cell skeleton-roles"></div>
          <div class="skeleton-cell skeleton-status"></div>
          <div class="skeleton-cell skeleton-date"></div>
          <div class="skeleton-cell skeleton-date"></div>
          <div class="skeleton-cell skeleton-actions"></div>
        </div>
      </div>

      <transition name="fade-content">
        <el-table
          v-show="!admin.loading.value || admin.users.value.length > 0"
          :data="admin.users.value"
          class="users-table"
        >
          <el-table-column label="用户" min-width="200">
            <template #default="{ row }">
              <div class="user-cell">
                <strong>{{ row.displayName || row.username }}</strong>
                <span>{{ row.username }}</span>
              </div>
            </template>
          </el-table-column>
          <el-table-column prop="email" label="邮箱" min-width="180" show-overflow-tooltip>
            <template #default="{ row }">{{ row.email || '-' }}</template>
          </el-table-column>
          <el-table-column label="角色" min-width="220">
            <template #default="{ row }">
              <div class="inline-edit-cell">
                <div class="tag-list">
                  <RoleTag v-for="role in row.roles" :key="role" :role="role" />
                </div>
                <el-popover
                  :visible="roleEditorUserId === row.id"
                  placement="bottom-start"
                  trigger="manual"
                  width="280"
                  popper-class="admin-inline-popover"
                >
                  <template #reference>
                    <el-button
                      circle
                      text
                      class="cell-edit-button"
                      :icon="EditPen"
                      aria-label="编辑角色"
                      title="编辑角色"
                      @click.stop="openRoleEditor(row)"
                    />
                  </template>
                  <div class="inline-editor" @click.stop @keydown.esc.stop="closeRoleEditor">
                    <div class="inline-editor-title">调整角色</div>
                    <el-checkbox-group v-model="roleDraft" class="role-choice-list">
                      <el-checkbox-button v-for="option in roleOptions" :key="option.value" :value="option.value">
                        {{ option.label }}
                      </el-checkbox-button>
                    </el-checkbox-group>
                    <div class="inline-editor-actions">
                      <el-button size="small" @click="closeRoleEditor">取消</el-button>
                      <el-button
                        size="small"
                        type="primary"
                        :loading="inlineSavingUserId === row.id"
                        @click="saveRoleEditor(row)"
                      >
                        保存
                      </el-button>
                    </div>
                  </div>
                </el-popover>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="状态" width="140">
            <template #default="{ row }">
              <div class="inline-edit-cell compact">
                <StatusTag :status="row.status" />
                <el-popover
                  :visible="statusEditorUserId === row.id"
                  placement="bottom-start"
                  trigger="manual"
                  width="220"
                  popper-class="admin-inline-popover"
                >
                  <template #reference>
                    <el-button
                      circle
                      text
                      class="cell-edit-button"
                      :icon="EditPen"
                      aria-label="编辑状态"
                      title="编辑状态"
                      @click.stop="openStatusEditor(row)"
                    />
                  </template>
                  <div class="inline-editor" @click.stop @keydown.esc.stop="closeStatusEditor">
                    <div class="inline-editor-title">调整状态</div>
                    <el-radio-group v-model="statusDraft" class="status-choice-list">
                      <el-radio-button v-for="option in statusOptions" :key="option.value" :value="option.value">
                        {{ option.label }}
                      </el-radio-button>
                    </el-radio-group>
                    <div class="inline-editor-actions">
                      <el-button size="small" @click="closeStatusEditor">取消</el-button>
                      <el-button
                        size="small"
                        type="primary"
                        :loading="inlineSavingUserId === row.id"
                        @click="saveStatusEditor(row)"
                      >
                        保存
                      </el-button>
                    </div>
                  </div>
                </el-popover>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="最近登录" min-width="160">
            <template #default="{ row }">{{ formatDate(row.lastLoginAt) }}</template>
          </el-table-column>
          <el-table-column label="创建时间" min-width="160">
            <template #default="{ row }">{{ formatDate(row.createdAt) }}</template>
          </el-table-column>
          <el-table-column label="操作" width="100" fixed="right" align="center">
            <template #default="{ row }">
              <el-dropdown
                trigger="click"
                placement="bottom-end"
                popper-class="user-actions-menu"
                @command="(command: UserActionCommand) => handleUserAction(command, row)"
              >
                <button class="action-menu-trigger" type="button" aria-label="更多操作">
                  <el-icon><MoreFilled /></el-icon>
                </button>
                <template #dropdown>
                  <el-dropdown-menu>
                    <el-dropdown-item command="edit">编辑</el-dropdown-item>
                    <el-dropdown-item command="reset-password">重置密码</el-dropdown-item>
                    <el-dropdown-item
                      command="delete"
                      divided
                      class="danger-menu-item"
                      :disabled="admin.deletingUserId.value === row.id"
                    >
                      删除
                    </el-dropdown-item>
                  </el-dropdown-menu>
                </template>
              </el-dropdown>
            </template>
          </el-table-column>
        </el-table>
      </transition>
    </div>

    <div class="pagination-wrap">
      <el-pagination
        background
        layout="total, sizes, prev, pager, next"
        :total="admin.pagination.total"
        :page-size="admin.pagination.size"
        :current-page="admin.pagination.page + 1"
        :page-sizes="[10, 20, 50]"
        @size-change="handlePageSizeChange"
        @current-change="(page: number) => admin.loadUsers(page - 1)"
      />
    </div>

    <el-dialog v-model="admin.formDialogVisible.value" :title="admin.dialogTitle.value" width="480px" class="claude-workspace-dialog" append-to-body modal-class="admin-users-dialog-overlay">
      <el-form label-position="top">
        <el-form-item label="用户名" required>
          <el-input v-model="admin.userForm.username" :disabled="admin.formMode.value === 'edit'" placeholder="例如 alice" />
        </el-form-item>
        <el-form-item v-if="admin.formMode.value === 'create'" label="初始密码" required>
          <el-input v-model="admin.userForm.password" type="password" show-password placeholder="请输入初始密码" />
        </el-form-item>
        <el-form-item label="昵称">
          <el-input v-model="admin.userForm.displayName" placeholder="展示名称" />
        </el-form-item>
        <el-form-item label="邮箱">
          <el-input v-model="admin.userForm.email" placeholder="name@example.com" />
        </el-form-item>
        <el-form-item label="角色" required>
          <el-select v-model="admin.userForm.roles" multiple class="full-select">
            <el-option label="管理员" value="ADMIN" />
            <el-option label="评审员" value="REVIEWER" />
            <el-option label="普通用户" value="USER" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="admin.formDialogVisible.value = false">取消</el-button>
        <el-button type="primary" :loading="admin.saving.value" @click="admin.saveUser">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="admin.passwordDialogVisible.value" title="重置密码" width="400px" class="claude-workspace-dialog" append-to-body modal-class="admin-users-dialog-overlay">
      <el-form label-position="top">
        <el-form-item :label="`新密码${admin.selectedUser.value ? ` · ${admin.selectedUser.value.username}` : ''}`" required>
          <el-input v-model="admin.passwordForm.password" type="password" show-password placeholder="请输入新密码" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="admin.passwordDialogVisible.value = false">取消</el-button>
        <el-button type="primary" :loading="admin.saving.value" @click="admin.resetPassword">确认重置</el-button>
      </template>
    </el-dialog>
  </section>
</template>

<style scoped>
.admin-reused-summary-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  align-self: start;
  align-items: start;
  gap: 16px;
}

.users-panel {
  display: grid;
  min-width: 0;
  gap: 16px;
  border: 1px solid var(--app-border);
  border-radius: var(--app-radius-lg);
  padding: 24px;
  overflow-x: hidden;
  background: var(--claude-canvas);
}

.reused-panel-heading {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 10px;
  align-items: center;
  border-bottom: 1px solid var(--app-border);
  padding-bottom: 14px;
}

.reused-panel-heading h2 {
  margin: 0;
  color: var(--app-text);
  font-size: 22px;
  font-weight: 500;
}

.reused-panel-heading p {
  margin: 4px 0 0;
  color: var(--app-text-muted);
  font-size: 13px;
  line-height: 1.6;
}

.primary-action {
  min-width: 108px;
}

.summary-card {
  display: flex;
  align-items: center;
  gap: 14px;
  height: 100px;
  border: 1px solid var(--app-border);
  border-radius: var(--app-radius-lg);
  padding: 12px 18px;
  background: var(--app-surface-soft);
}

.summary-icon {
  width: 36px;
  height: 36px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: var(--app-radius-md);
  flex-shrink: 0;
}

.summary-icon.coral {
  background: var(--app-primary-soft);
  color: var(--app-primary);
}

.summary-icon.green {
  background: var(--app-success-soft);
  color: var(--app-success);
}

.summary-icon.teal {
  background: var(--app-accent-soft);
  color: var(--app-accent);
}

.summary-icon.red {
  background: var(--app-danger-soft);
  color: var(--app-danger);
}

.summary-label {
  display: block;
  color: var(--app-text-muted);
  font-size: 13px;
  font-weight: 500;
}

.summary-value {
  display: block;
  margin-top: 4px;
  color: var(--app-text);
  font-family: "Cormorant Garamond", "EB Garamond", Georgia, serif;
  font-size: 24px;
  font-weight: 500;
  line-height: 1;
  letter-spacing: -0.02em;
}

.reused-toolbar {
  display: grid;
  grid-template-columns: minmax(200px, 1fr) 160px auto auto;
  min-width: 0;
  gap: 10px;
  align-items: center;
}

.reused-toolbar .el-icon.is-rotating {
  animation: rotate-icon 0.8s linear infinite;
}

@keyframes rotate-icon {
  from {
    transform: rotate(0deg);
  }
  to {
    transform: rotate(360deg);
  }
}

.status-filter {
  width: 160px;
}

.reused-toolbar :deep([class~="el-input__wrapper"]),
.reused-toolbar :deep([class~="el-select__wrapper"]) {
  min-height: 36px;
  border-radius: var(--app-radius-sm) !important;
  box-shadow: none !important;
  border: 1px solid var(--app-border);
}

.reused-toolbar :deep([class~="el-input__wrapper"]:hover),
.reused-toolbar :deep([class~="el-select__wrapper"]:hover) {
  border-color: var(--app-border-strong);
}

.reused-toolbar :deep([class~="el-input__wrapper"][class~="is-focus"]),
.reused-toolbar :deep([class~="el-select__wrapper"]:focus-within) {
  border-color: var(--app-primary);
}

.reused-table-card {
  overflow-x: auto;
  overflow-y: hidden;
  border: 1px solid var(--app-border);
  border-radius: var(--app-radius-sm);
  background: var(--app-surface);
}

.users-table {
  width: 100%;
  min-width: 1160px;
  border: 0;
  border-radius: 0;
  overflow: hidden;
  background: var(--app-surface);
}

.table-wrapper {
  position: relative;
  min-width: 0;
  max-width: 100%;
  min-height: 200px;
  overflow-x: auto;
  overflow-y: hidden;
}

:deep(.users-table .el-table__header th.el-table__cell) {
  background: var(--app-surface-soft);
  color: var(--app-text-muted);
  font-size: 12px;
  font-weight: 500;
  letter-spacing: 1.5px;
  text-transform: uppercase;
}

.skeleton-container {
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding: 16px;
}

.skeleton-row {
  display: grid;
  grid-template-columns: 200px 180px 220px 140px 160px 160px 100px;
  gap: 16px;
  align-items: center;
  padding: 12px 0;
}

.skeleton-cell {
  height: 20px;
  border-radius: 4px;
  background: linear-gradient(
    90deg,
    var(--app-surface-muted) 25%,
    var(--app-surface-soft) 50%,
    var(--app-surface-muted) 75%
  );
  background-size: 200% 100%;
  animation: shimmer 1.5s ease-in-out infinite;
}

.skeleton-user {
  width: 160px;
  height: 36px;
}

.skeleton-email {
  width: 140px;
}

.skeleton-roles {
  width: 180px;
}

.skeleton-status {
  width: 80px;
  height: 24px;
}

.skeleton-date {
  width: 120px;
}

.skeleton-actions {
  width: 60px;
}

@keyframes shimmer {
  0% {
    background-position: 200% 0;
  }
  100% {
    background-position: -200% 0;
  }
}

.fade-content-enter-active {
  transition: opacity 0.3s ease;
}

.fade-content-leave-active {
  transition: opacity 0.2s ease;
}

.fade-content-enter-from,
.fade-content-leave-to {
  opacity: 0;
}

.user-cell {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.user-cell strong {
  color: var(--app-text);
  font-size: 14px;
  font-weight: 600;
}

.user-cell span {
  color: var(--app-text-muted);
  font-size: 13px;
}

.inline-edit-cell {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 6px;
  min-height: 32px;
}

.inline-edit-cell.compact {
  justify-content: flex-start;
}

.tag-list {
  display: flex;
  min-width: 0;
  flex-wrap: wrap;
  gap: 4px;
}

.cell-edit-button {
  width: 28px;
  height: 28px;
  min-width: 28px;
  color: var(--app-text-subtle);
  opacity: 0;
  transition: opacity 0.15s ease, color 0.15s ease;
}

.inline-edit-cell:hover .cell-edit-button,
.cell-edit-button:focus {
  opacity: 1;
}

.cell-edit-button:hover,
.cell-edit-button:focus {
  color: var(--app-primary);
}

.inline-editor {
  display: grid;
  gap: 10px;
}

.inline-editor-title {
  color: var(--app-text);
  font-size: 13px;
  font-weight: 600;
}

.role-choice-list,
.status-choice-list {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.role-choice-list :deep([class~="el-checkbox-button__inner"]),
.status-choice-list :deep([class~="el-radio-button__inner"]) {
  border: 1px solid var(--app-border);
  border-radius: var(--app-radius-xs);
  box-shadow: none;
  font-weight: 600;
}

.role-choice-list :deep([class~="el-checkbox-button"]:first-child [class~="el-checkbox-button__inner"]),
.status-choice-list :deep([class~="el-radio-button"]:first-child [class~="el-radio-button__inner"]) {
  border-left: 1px solid var(--app-border);
  border-radius: var(--app-radius-xs);
}

.role-choice-list :deep([class~="el-checkbox-button"][class~="is-checked"] [class~="el-checkbox-button__inner"]),
.status-choice-list :deep([class~="el-radio-button"][class~="is-active"] [class~="el-radio-button__inner"]) {
  border-color: var(--app-primary);
  background: var(--app-primary);
  box-shadow: none;
}

.inline-editor-actions {
  display: flex;
  justify-content: flex-end;
  gap: 6px;
}

.action-menu-trigger {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 38px;
  height: 38px;
  border: 0;
  padding: 0;
  background: transparent;
  color: var(--app-text-subtle);
  cursor: pointer;
  border-radius: var(--app-radius-md);
  transition: background-color 0.18s ease, color 0.18s ease;
}

.action-menu-trigger:hover {
  background: var(--app-primary-soft);
  color: var(--app-primary);
}

.action-menu-trigger:focus-visible {
  outline: 2px solid var(--app-primary);
  outline-offset: 2px;
}

.pagination-wrap {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
  margin-top: 16px;
}

.full-select {
  width: 100%;
}

:global([class~="admin-inline-popover"]) {
  border: 1px solid var(--app-border);
  border-radius: var(--app-radius-md);
  box-shadow: var(--app-shadow-lg);
}

:global([class~="user-actions-menu"]) {
  min-width: 120px;
}

:global([class~="user-actions-menu"] [class~="danger-menu-item"]) {
  color: var(--app-danger);
  font-weight: 600;
}

:global([class~="user-actions-menu"] [class~="danger-menu-item"]:not([class~="is-disabled"]):hover) {
  background: var(--app-danger-soft);
  color: var(--app-danger);
}

:global([class~="danger-confirm-button"]) {
  --el-button-bg-color: var(--app-danger);
  --el-button-border-color: var(--app-danger);
  --el-button-hover-bg-color: #dc2626;
  --el-button-hover-border-color: #dc2626;
}

:global(.admin-users-dialog-overlay) {
  background: rgba(250, 249, 245, 0.18);
  backdrop-filter: blur(2px);
}

@media (max-width: 1180px) {
  .admin-reused-summary-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 760px) {
  .admin-reused-summary-grid,
  .reused-toolbar,
  .reused-panel-heading {
    grid-template-columns: 1fr;
  }

  .users-panel {
    padding: 18px;
  }

  .status-filter {
    width: 100%;
  }

  .primary-action {
    justify-self: start;
  }
}
</style>
