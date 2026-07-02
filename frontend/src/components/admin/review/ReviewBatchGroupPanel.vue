<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue';
import { ElMessage } from 'element-plus';
import { getErrorMessage } from '../../../api/http';
import { listAdminUsers } from '../../../api/adminUsers';
import {
  createReviewGroup,
  listReviewGroupMembers,
  listReviewGroups,
  replaceReviewGroupMembers,
  updateReviewGroup,
} from '../../../api/adminReviews';
import type {
  AdminUser,
  ReviewGroup,
  ReviewGroupMember,
  ReviewGroupPayload,
} from '../../../types';

type DialogMode = 'create' | 'edit';

const loading = ref(false);
const saving = ref(false);
const groups = ref<ReviewGroup[]>([]);
const members = ref<ReviewGroupMember[]>([]);
const reviewerCandidates = ref<AdminUser[]>([]);
const selectedGroup = ref<ReviewGroup | null>(null);
const groupDialogVisible = ref(false);
const memberDrawerVisible = ref(false);
const groupDialogMode = ref<DialogMode>('create');
const editingGroup = ref<ReviewGroup | null>(null);
const groupKeyword = ref('');
const groupStatusFilter = ref('');

const groupForm = reactive({
  name: '',
  leaderUserId: '',
  status: 'ACTIVE',
});

const memberForm = reactive({
  leaderUserId: '',
  memberUserIds: [] as string[],
});

const activeMemberCount = computed(() => members.value.filter((member) => member.status === 'ACTIVE').length);
const filteredGroups = computed(() => {
  const keyword = groupKeyword.value.trim().toLowerCase();
  return groups.value.filter((group) => {
    const leader = group.leaderDisplayName || group.leaderUsername || displayUser(group.leaderUserId);
    const matchesKeyword = !keyword || `${group.name} ${leader}`.toLowerCase().includes(keyword);
    const matchesStatus = !groupStatusFilter.value || group.status === groupStatusFilter.value;
    return matchesKeyword && matchesStatus;
  });
});
const reviewerOptions = computed(() =>
  reviewerCandidates.value.filter((user) => user.status === 'ACTIVE' && user.roles.includes('REVIEWER')),
);
const groupDialogTitle = computed(() => (groupDialogMode.value === 'create' ? '新建评审小组' : '编辑评审小组'));

onMounted(async () => {
  await Promise.all([loadGroups(), loadReviewerCandidates()]);
});

async function loadGroups() {
  loading.value = true;
  try {
    groups.value = await listReviewGroups();
    if (selectedGroup.value && !groups.value.some((group) => group.id === selectedGroup.value?.id)) {
      selectedGroup.value = null;
      members.value = [];
    }
  } catch (error) {
    ElMessage.error(getErrorMessage(error));
  } finally {
    loading.value = false;
  }
}

async function loadReviewerCandidates() {
  try {
    const result = await listAdminUsers({ page: 0, size: 100, status: 'ACTIVE' });
    reviewerCandidates.value = result.items;
  } catch (error) {
    ElMessage.error(getErrorMessage(error));
  }
}

function openCreateGroupDialog() {
  groupDialogMode.value = 'create';
  editingGroup.value = null;
  Object.assign(groupForm, { name: '', leaderUserId: '', status: 'ACTIVE' });
  groupDialogVisible.value = true;
}

function openEditGroupDialog(group: ReviewGroup) {
  groupDialogMode.value = 'edit';
  editingGroup.value = group;
  Object.assign(groupForm, {
    name: group.name,
    leaderUserId: group.leaderUserId,
    status: group.status || 'ACTIVE',
  });
  groupDialogVisible.value = true;
}

async function saveGroup() {
  if (!groupForm.name.trim()) {
    ElMessage.warning('请输入小组名称');
    return;
  }
  if (!groupForm.leaderUserId) {
    ElMessage.warning('请选择组长');
    return;
  }
  saving.value = true;
  try {
    const payload: ReviewGroupPayload = {
      name: groupForm.name.trim(),
      leaderUserId: groupForm.leaderUserId,
      status: groupForm.status,
    };
    const group = groupDialogMode.value === 'create'
      ? await createReviewGroup(payload)
      : await updateReviewGroup(editingGroup.value!.id, payload);
    groupDialogVisible.value = false;
    selectedGroup.value = group;
    ElMessage.success(groupDialogMode.value === 'create' ? '评审小组已创建' : '评审小组已更新');
    await loadGroups();
    await openMemberDrawer(group);
  } catch (error) {
    ElMessage.error(getErrorMessage(error));
  } finally {
    saving.value = false;
  }
}

async function openMemberDrawer(group: ReviewGroup) {
  selectedGroup.value = group;
  memberDrawerVisible.value = true;
  memberForm.leaderUserId = group.leaderUserId;
  try {
    members.value = await listReviewGroupMembers(group.id);
    memberForm.leaderUserId = group.leaderUserId;
    memberForm.memberUserIds = members.value.map((member) => member.userId);
  } catch (error) {
    ElMessage.error(getErrorMessage(error));
  }
}

async function saveMembers() {
  if (!selectedGroup.value) return;
  if (!memberForm.leaderUserId) {
    ElMessage.warning('请选择组长');
    return;
  }
  const memberIds = new Set(memberForm.memberUserIds);
  memberIds.add(memberForm.leaderUserId);
  saving.value = true;
  try {
    members.value = await replaceReviewGroupMembers(selectedGroup.value.id, {
      leaderUserId: memberForm.leaderUserId,
      memberUserIds: [...memberIds],
    });
    ElMessage.success('小组成员已更新');
    memberDrawerVisible.value = false;
    await loadGroups();
  } catch (error) {
    ElMessage.error(getErrorMessage(error));
  } finally {
    saving.value = false;
  }
}

function displayUser(userId: string | null | undefined) {
  if (!userId) return '-';
  const user = reviewerCandidates.value.find((item) => item.id === userId);
  return user?.displayName || user?.username || userId;
}

function formatDate(value: string | null) {
  return value ? new Date(value).toLocaleString() : '-';
}
</script>

<template>
  <section class="review-config-panel">
    <section class="config-section reused-management-panel">
      <div class="reused-panel-heading">
        <p>配置评审小组、组长和组内成员；小组创建后可直接管理成员清单。</p>
      </div>

      <div class="config-tab-pane" role="tabpanel">
        <div class="reused-toolbar">
          <el-input v-model="groupKeyword" clearable placeholder="搜索小组 / 组长" />
          <el-select v-model="groupStatusFilter" clearable placeholder="全部状态">
            <el-option label="启用" value="ACTIVE" />
            <el-option label="停用" value="DISABLED" />
          </el-select>
          <el-button type="primary" @click="openCreateGroupDialog">新建小组</el-button>
        </div>

        <div class="reused-table-card table-wrapper" :class="{ 'is-loading': loading }">
          <div v-if="loading && groups.length === 0" class="skeleton-container">
            <div v-for="i in 4" :key="i" class="skeleton-row">
              <div class="skeleton-cell skeleton-group"></div>
              <div class="skeleton-cell skeleton-leader"></div>
              <div class="skeleton-cell skeleton-status"></div>
              <div class="skeleton-cell skeleton-actions"></div>
            </div>
          </div>

          <transition name="fade-content">
            <el-table
              v-show="!loading || groups.length > 0"
              :data="filteredGroups"
              class="config-table"
              height="560"
            >
              <el-table-column label="小组" min-width="220" show-overflow-tooltip>
                <template #default="{ row }">
                  <div class="primary-cell">
                    <strong>{{ row.name }}</strong>
                    <span>{{ row.taskCount }} 个任务 · {{ row.memberCount }} 名成员</span>
                  </div>
                </template>
              </el-table-column>
              <el-table-column label="组长" min-width="180" show-overflow-tooltip>
                <template #default="{ row }">{{ row.leaderDisplayName || row.leaderUsername || displayUser(row.leaderUserId) }}</template>
              </el-table-column>
              <el-table-column label="任务" width="110">
                <template #default="{ row }">{{ row.taskCount }} 个</template>
              </el-table-column>
              <el-table-column label="成员" width="110">
                <template #default="{ row }">{{ row.memberCount }} 名</template>
              </el-table-column>
              <el-table-column label="状态" width="120">
                <template #default="{ row }">
                  <el-tag :type="row.status === 'ACTIVE' ? 'success' : 'info'" effect="plain">{{ row.status }}</el-tag>
                </template>
              </el-table-column>
              <el-table-column label="更新时间" width="180">
                <template #default="{ row }">{{ formatDate(row.updatedAt) }}</template>
              </el-table-column>
              <el-table-column label="操作" width="190" align="right" fixed="right">
                <template #default="{ row }">
                  <el-button size="small" text type="primary" @click="openEditGroupDialog(row)">编辑</el-button>
                  <el-button size="small" text type="primary" @click="openMemberDrawer(row)">成员</el-button>
                </template>
              </el-table-column>
            </el-table>
          </transition>
        </div>
      </div>
    </section>

    <el-dialog
      v-model="groupDialogVisible"
      :title="groupDialogTitle"
      width="min(520px, calc(100vw - 32px))"
      class="review-config-dialog claude-workspace-dialog"
      append-to-body
      destroy-on-close
      align-center
    >
      <el-form label-position="top">
        <el-form-item label="小组名称" required>
          <el-input v-model="groupForm.name" placeholder="例如 第一评审组" />
        </el-form-item>
        <el-form-item label="组长" required>
          <el-select v-model="groupForm.leaderUserId" filterable class="full-select" placeholder="选择具备 REVIEWER 角色的用户">
            <el-option
              v-for="user in reviewerOptions"
              :key="user.id"
              :label="user.displayName || user.username"
              :value="user.id"
            >
              <span>{{ user.displayName || user.username }}</span>
              <small class="option-meta">{{ user.username }}</small>
            </el-option>
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="groupForm.status" class="full-select">
            <el-option label="启用" value="ACTIVE" />
            <el-option label="停用" value="DISABLED" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="groupDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="saveGroup">保存</el-button>
      </template>
    </el-dialog>

    <el-drawer
      v-model="memberDrawerVisible"
      title="小组成员"
      size="min(480px, 92vw)"
      class="claude-workspace-drawer"
      append-to-body
      destroy-on-close
    >
      <template v-if="selectedGroup">
        <div class="drawer-heading">
          <strong>{{ selectedGroup.name }}</strong>
          <span>组长必须保留在有效成员中。</span>
        </div>
        <el-form label-position="top">
          <el-form-item label="组长" required>
            <el-select v-model="memberForm.leaderUserId" filterable class="full-select">
              <el-option
                v-for="user in reviewerOptions"
                :key="user.id"
                :label="user.displayName || user.username"
                :value="user.id"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="组内评审员">
            <el-select v-model="memberForm.memberUserIds" multiple filterable class="full-select" placeholder="选择本组成员">
              <el-option
                v-for="user in reviewerOptions"
                :key="user.id"
                :label="user.displayName || user.username"
                :value="user.id"
              />
            </el-select>
          </el-form-item>
        </el-form>
        <div class="member-list">
          <div class="member-list-title">当前有效成员：{{ activeMemberCount }}</div>
          <el-tag v-for="member in members" :key="member.id" effect="plain" class="member-tag">
            {{ member.displayName || member.username || member.userId }} · {{ member.memberRole }}
          </el-tag>
        </div>
      </template>
      <template #footer>
        <el-button @click="memberDrawerVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="saveMembers">保存成员</el-button>
      </template>
    </el-drawer>
  </section>
</template>

<style scoped>
.review-config-panel {
  display: grid;
  gap: 16px;
}

.config-section {
  border: 1px solid var(--app-border);
  border-radius: var(--app-radius-lg);
  background: var(--claude-canvas);
  box-shadow: none;
}

.primary-cell span,
.drawer-heading span {
  color: var(--app-text-muted);
  font-size: 13px;
}

.reused-management-panel {
  display: grid;
  gap: 0;
  overflow: hidden;
}

.reused-panel-heading {
  padding: 24px 24px 14px;
  border-bottom: 1px solid var(--app-border);
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

.config-tab-pane {
  padding: 16px 24px 24px;
}

.reused-toolbar {
  display: grid;
  grid-template-columns: minmax(200px, 1fr) 160px auto;
  gap: 10px;
  align-items: center;
  margin-bottom: 16px;
}

.reused-toolbar :deep([class~="el-input__wrapper"]),
.reused-toolbar :deep([class~="el-select__wrapper"]) {
  min-height: 36px;
  border: 1px solid var(--app-border);
  border-radius: var(--app-radius-sm) !important;
  box-shadow: none !important;
}

.reused-toolbar :deep([class~="el-input__wrapper"]:hover),
.reused-toolbar :deep([class~="el-select__wrapper"]:hover) {
  border-color: var(--app-border-strong);
}

.reused-table-card {
  overflow: hidden;
  min-height: 420px;
  border: 1px solid var(--app-border);
  border-radius: var(--app-radius-sm);
  background: var(--app-surface);
}

.config-table {
  width: 100%;
}

.table-wrapper {
  position: relative;
  min-height: 200px;
  overflow-x: auto;
}

:deep(.config-table .el-table__header th.el-table__cell) {
  background: var(--app-surface-soft);
  color: var(--app-text-muted);
  font-size: 12px;
  font-weight: 500;
  letter-spacing: 1.5px;
  text-transform: uppercase;
}

:deep([class~="el-table__body-wrapper"]) {
  scrollbar-width: thin;
}

.skeleton-container {
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding: 16px;
}

.skeleton-row {
  display: grid;
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

.skeleton-group {
  min-width: 180px;
  height: 36px;
}

.skeleton-leader {
  min-width: 160px;
}

.skeleton-status {
  width: 80px;
  height: 24px;
}

.skeleton-actions {
  width: 150px;
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

.primary-cell {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.primary-cell strong {
  color: var(--app-text);
  font-weight: 600;
}

.primary-cell span {
  font-size: 13px;
}

.full-select {
  width: 100%;
}

.option-meta {
  float: right;
  color: var(--app-text-subtle);
}

.drawer-heading {
  display: grid;
  gap: 5px;
  margin-bottom: 18px;
}

.member-list {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  border-top: 1px solid var(--app-border);
  margin-top: 16px;
  padding-top: 16px;
}

.member-list-title {
  width: 100%;
  color: var(--app-text);
  font-weight: 600;
}

.member-tag {
  max-width: 100%;
}
</style>
