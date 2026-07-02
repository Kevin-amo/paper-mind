<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue';
import { ElMessage } from 'element-plus';
import { Check, Files, Setting, UserFilled } from '@element-plus/icons-vue';
import { getErrorMessage } from '../../../api/http';
import { listAdminUsers } from '../../../api/adminUsers';
import {
  createReviewBatch,
  createReviewGroup,
  listReviewBatches,
  listReviewGroupMembers,
  listReviewGroups,
  replaceReviewGroupMembers,
  updateReviewBatch,
  updateReviewGroup,
} from '../../../api/adminReviews';
import type {
  AdminUser,
  ReviewBatch,
  ReviewBatchPayload,
  ReviewGroup,
  ReviewGroupMember,
  ReviewGroupPayload,
} from '../../../types';

type DialogMode = 'create' | 'edit';
type ConfigTab = 'batches' | 'groups';

const loading = ref(false);
const saving = ref(false);
const batches = ref<ReviewBatch[]>([]);
const groups = ref<ReviewGroup[]>([]);
const members = ref<ReviewGroupMember[]>([]);
const reviewerCandidates = ref<AdminUser[]>([]);
const selectedBatchId = ref<string>('');
const selectedGroup = ref<ReviewGroup | null>(null);
const batchDialogVisible = ref(false);
const groupDialogVisible = ref(false);
const memberDrawerVisible = ref(false);
const batchDialogMode = ref<DialogMode>('create');
const groupDialogMode = ref<DialogMode>('create');
const editingBatch = ref<ReviewBatch | null>(null);
const editingGroup = ref<ReviewGroup | null>(null);
const activeConfigTab = ref<ConfigTab>('batches');
const batchKeyword = ref('');
const batchStatusFilter = ref('');
const groupKeyword = ref('');
const groupStatusFilter = ref('');

const batchForm = reactive({
  name: '',
  description: '',
  status: 'ACTIVE',
  startsAt: '',
  endsAt: '',
});

const groupForm = reactive({
  name: '',
  leaderUserId: '',
  status: 'ACTIVE',
});

const memberForm = reactive({
  leaderUserId: '',
  memberUserIds: [] as string[],
});

const selectedBatch = computed(() => batches.value.find((batch) => batch.id === selectedBatchId.value) || null);
const activeBatchCount = computed(() => batches.value.filter((batch) => batch.status === 'ACTIVE').length);
const activeGroupCount = computed(() => groups.value.filter((group) => group.status === 'ACTIVE').length);
const activeMemberCount = computed(() => members.value.filter((member) => member.status === 'ACTIVE').length);
const filteredBatches = computed(() => {
  const keyword = batchKeyword.value.trim().toLowerCase();
  return batches.value.filter((batch) => {
    const matchesKeyword = !keyword || `${batch.name} ${batch.description || ''}`.toLowerCase().includes(keyword);
    const matchesStatus = !batchStatusFilter.value || batch.status === batchStatusFilter.value;
    return matchesKeyword && matchesStatus;
  });
});
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
const batchDialogTitle = computed(() => (batchDialogMode.value === 'create' ? '新建评审批次' : '编辑评审批次'));
const groupDialogTitle = computed(() => (groupDialogMode.value === 'create' ? '新建评审小组' : '编辑评审小组'));

onMounted(async () => {
  await Promise.all([loadBatches(), loadReviewerCandidates()]);
});

async function loadBatches() {
  loading.value = true;
  try {
    const result = await listReviewBatches({ page: 0, size: 100 });
    batches.value = result.items;
    if (!selectedBatchId.value && batches.value.length) {
      selectedBatchId.value = batches.value[0].id;
    }
    await loadGroups();
  } catch (error) {
    ElMessage.error(getErrorMessage(error));
  } finally {
    loading.value = false;
  }
}

async function loadGroups() {
  try {
    groups.value = await listReviewGroups();
    if (selectedGroup.value && !groups.value.some((group) => group.id === selectedGroup.value?.id)) {
      selectedGroup.value = null;
      members.value = [];
    }
  } catch (error) {
    ElMessage.error(getErrorMessage(error));
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

async function handleBatchChange() {
  selectedGroup.value = null;
  members.value = [];
}

async function selectBatch(batch: ReviewBatch) {
  selectedBatchId.value = batch.id;
  await handleBatchChange();
}

function openCreateBatchDialog() {
  batchDialogMode.value = 'create';
  editingBatch.value = null;
  Object.assign(batchForm, { name: '', description: '', status: 'ACTIVE', startsAt: '', endsAt: '' });
  batchDialogVisible.value = true;
}

function openEditBatchDialog(batch: ReviewBatch) {
  batchDialogMode.value = 'edit';
  editingBatch.value = batch;
  Object.assign(batchForm, {
    name: batch.name,
    description: batch.description || '',
    status: batch.status || 'ACTIVE',
    startsAt: toDateTimeInput(batch.startsAt),
    endsAt: toDateTimeInput(batch.endsAt),
  });
  batchDialogVisible.value = true;
}

async function saveBatch() {
  if (!batchForm.name.trim()) {
    ElMessage.warning('请输入批次名称');
    return;
  }
  saving.value = true;
  try {
    const payload: ReviewBatchPayload = {
      name: batchForm.name.trim(),
      description: batchForm.description.trim() || null,
      status: batchForm.status,
      startsAt: toPayloadDateTime(batchForm.startsAt),
      endsAt: toPayloadDateTime(batchForm.endsAt),
    };
    const batch = batchDialogMode.value === 'create'
      ? await createReviewBatch(payload)
      : await updateReviewBatch(editingBatch.value!.id, payload);
    selectedBatchId.value = batch.id;
    batchDialogVisible.value = false;
    ElMessage.success(batchDialogMode.value === 'create' ? '评审批次已创建' : '评审批次已更新');
    await loadBatches();
  } catch (error) {
    ElMessage.error(getErrorMessage(error));
  } finally {
    saving.value = false;
  }
}

function openCreateGroupDialog() {
  if (!selectedBatchId.value) {
    ElMessage.warning('请先选择批次');
    return;
  }
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
  if (!selectedBatchId.value) {
    ElMessage.warning('请先选择批次');
    return;
  }
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
      batchId: selectedBatchId.value,
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

function toDateTimeInput(value: string | null) {
  return value ? new Date(value).toISOString().slice(0, 19) : '';
}

function toPayloadDateTime(value: string) {
  return value ? new Date(value).toISOString() : null;
}
</script>

<template>
  <section class="review-config-panel">
    <div class="review-config-overview admin-reused-summary-grid" aria-label="批次与小组概览">
      <div
        class="config-card animate slide-up"
        v-animate="{ type: 'slide-up', delay: '0ms', duration: '0.6s' }"
      >
        <div class="summary-icon coral">
          <el-icon :size="20"><Files /></el-icon>
        </div>
        <div>
          <span>批次数量</span>
          <strong>{{ batches.length }}</strong>
        </div>
      </div>
      <div
        class="config-card animate slide-up"
        v-animate="{ type: 'slide-up', delay: '80ms', duration: '0.6s' }"
      >
        <div class="summary-icon green">
          <el-icon :size="20"><Check /></el-icon>
        </div>
        <div>
          <span>启用批次</span>
          <strong>{{ activeBatchCount }}</strong>
        </div>
      </div>
      <div
        class="config-card animate slide-up"
        v-animate="{ type: 'slide-up', delay: '160ms', duration: '0.6s' }"
      >
        <div class="summary-icon teal">
          <el-icon :size="20"><Setting /></el-icon>
        </div>
        <div>
          <span>当前小组</span>
          <strong>{{ groups.length }}</strong>
        </div>
      </div>
      <div
        class="config-card animate slide-up"
        v-animate="{ type: 'slide-up', delay: '240ms', duration: '0.6s' }"
      >
        <div class="summary-icon amber">
          <el-icon :size="20"><UserFilled /></el-icon>
        </div>
        <div>
          <span>启用小组</span>
          <strong>{{ activeGroupCount }}</strong>
        </div>
      </div>
    </div>

    <section class="config-section reused-management-panel">
      <div class="reused-panel-heading">
        <h2>批次与小组</h2>
        <p>配置评审批次、评审小组、组长和组内成员；整体结构与全局进度页保持一致，只替换业务内容。</p>
      </div>

      <div class="config-tabs" role="tablist" aria-label="批次与小组">
        <button
          type="button"
          role="tab"
          :aria-selected="activeConfigTab === 'batches'"
          :class="{ active: activeConfigTab === 'batches' }"
          @click="activeConfigTab = 'batches'"
        >
          评审批次 <span>{{ batches.length }}</span>
        </button>
        <button
          type="button"
          role="tab"
          :aria-selected="activeConfigTab === 'groups'"
          :class="{ active: activeConfigTab === 'groups' }"
          @click="activeConfigTab = 'groups'"
        >
          评审小组 <span>{{ groups.length }}</span>
        </button>
      </div>

      <div v-show="activeConfigTab === 'batches'" class="config-tab-pane" role="tabpanel">
        <div class="reused-toolbar">
          <el-input v-model="batchKeyword" clearable placeholder="搜索批次名称 / 描述" />
          <el-select v-model="batchStatusFilter" clearable placeholder="全部状态">
            <el-option label="草稿" value="DRAFT" />
            <el-option label="启用" value="ACTIVE" />
            <el-option label="关闭" value="CLOSED" />
            <el-option label="归档" value="ARCHIVED" />
          </el-select>
          <el-button type="primary" @click="openCreateBatchDialog">新建批次</el-button>
        </div>

        <div class="reused-table-card table-wrapper" :class="{ 'is-loading': loading }">
          <div v-if="loading && batches.length === 0" class="skeleton-container">
            <div v-for="i in 4" :key="i" class="skeleton-row">
              <div class="skeleton-cell skeleton-batch"></div>
              <div class="skeleton-cell skeleton-status"></div>
              <div class="skeleton-cell skeleton-time"></div>
              <div class="skeleton-cell skeleton-action"></div>
            </div>
          </div>

          <transition name="fade-content">
            <el-table
              v-show="!loading || batches.length > 0"
              :data="filteredBatches"
              class="config-table"
              height="560"
              highlight-current-row
              @row-click="selectBatch"
            >
              <el-table-column label="批次" min-width="240" show-overflow-tooltip>
                <template #default="{ row }">
                  <div class="primary-cell">
                    <strong>{{ row.name }}</strong>
                    <span>{{ row.description || '无描述' }}</span>
                  </div>
                </template>
              </el-table-column>
              <el-table-column label="状态" width="120">
                <template #default="{ row }">
                  <el-tag :type="row.status === 'ACTIVE' ? 'success' : row.status === 'DRAFT' ? 'info' : 'warning'" effect="plain">
                    {{ row.status }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column label="开始时间" width="180">
                <template #default="{ row }">{{ formatDate(row.startsAt) }}</template>
              </el-table-column>
              <el-table-column label="结束时间" width="180">
                <template #default="{ row }">{{ formatDate(row.endsAt) }}</template>
              </el-table-column>
              <el-table-column label="更新时间" width="180">
                <template #default="{ row }">{{ formatDate(row.updatedAt) }}</template>
              </el-table-column>
              <el-table-column label="操作" width="110" align="right" fixed="right">
                <template #default="{ row }">
                  <el-button size="small" text type="primary" @click.stop="openEditBatchDialog(row)">编辑</el-button>
                </template>
              </el-table-column>
            </el-table>
          </transition>
        </div>
      </div>

      <div v-show="activeConfigTab === 'groups'" class="config-tab-pane" role="tabpanel">
        <div class="reused-toolbar">
          <el-input v-model="groupKeyword" clearable placeholder="搜索小组 / 组长" />
          <el-select v-model="groupStatusFilter" clearable placeholder="全部状态">
            <el-option label="启用" value="ACTIVE" />
            <el-option label="停用" value="DISABLED" />
          </el-select>
          <el-button type="primary" :disabled="!selectedBatchId" @click="openCreateGroupDialog">新建小组</el-button>
        </div>

        <div class="current-batch-hint">
          {{ selectedBatch ? `当前展示：全部小组；新建小组将归属到 ${selectedBatch.name}` : '当前展示：全部小组；请选择批次后新建小组' }}
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
      v-model="batchDialogVisible"
      :title="batchDialogTitle"
      width="min(520px, calc(100vw - 32px))"
      class="review-config-dialog claude-workspace-dialog"
      append-to-body
      destroy-on-close
      align-center
    >
      <el-form label-position="top">
        <el-form-item label="批次名称" required>
          <el-input v-model="batchForm.name" placeholder="例如 2026 春季论文评审" />
        </el-form-item>
        <el-form-item label="说明">
          <el-input v-model="batchForm.description" type="textarea" :rows="3" placeholder="批次说明" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="batchForm.status" class="full-select">
            <el-option label="草稿" value="DRAFT" />
            <el-option label="启用" value="ACTIVE" />
            <el-option label="关闭" value="CLOSED" />
            <el-option label="归档" value="ARCHIVED" />
          </el-select>
        </el-form-item>
        <div class="form-grid">
          <el-form-item label="开始时间">
            <el-input v-model="batchForm.startsAt" type="datetime-local" />
          </el-form-item>
          <el-form-item label="结束时间">
            <el-input v-model="batchForm.endsAt" type="datetime-local" />
          </el-form-item>
        </div>
      </el-form>
      <template #footer>
        <el-button @click="batchDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="saveBatch">保存</el-button>
      </template>
    </el-dialog>

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

.admin-reused-summary-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  align-self: start;
  align-items: start;
  gap: 16px;
  min-width: 0;
}

.config-card,
.config-section {
  border: 1px solid var(--app-border);
  border-radius: var(--app-radius-lg);
  background: var(--claude-canvas);
  box-shadow: none;
}

.config-card {
  display: flex;
  align-items: center;
  min-width: 0;
  height: 100px;
  gap: 14px;
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

.summary-icon.amber {
  background: var(--app-warning-soft);
  color: var(--app-warning);
}

.config-card span,
.primary-cell span,
.drawer-heading span {
  color: var(--app-text-muted);
  font-size: 13px;
}

.config-card strong {
  display: block;
  margin-top: 4px;
  color: var(--app-text);
  font-family: "Cormorant Garamond", "EB Garamond", Georgia, serif;
  font-size: 24px;
  font-weight: 500;
  line-height: 1;
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

.config-tabs button {
  border: 0;
  cursor: pointer;
  font: inherit;
}

.config-tabs {
  display: flex;
  align-items: flex-end;
  gap: 16px;
  min-height: 48px;
  border-bottom: 1px solid var(--app-border);
  padding: 0 24px;
}

.config-tabs button {
  position: relative;
  min-height: 40px;
  padding: 0 12px;
  color: var(--app-text-muted);
  background: transparent;
  font-size: 15px;
  font-weight: 600;
  transition: color 0.18s ease;
}

.config-tabs button:hover,
.config-tabs button.active {
  color: var(--app-text);
}

.config-tabs button.active::after {
  content: "";
  position: absolute;
  right: 12px;
  bottom: -1px;
  left: 12px;
  height: 3px;
  border-radius: 999px 999px 0 0;
  background: var(--app-primary);
}

.config-tabs span {
  margin-left: 6px;
  color: var(--app-text-subtle);
  font-size: 12px;
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

.current-batch-hint {
  margin-bottom: 12px;
  color: var(--app-text-muted);
  font-size: 13px;
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

:deep([class~="el-table__body"] tr.current-row > td) {
  background: var(--app-primary-soft) !important;
}

:deep([class~="el-table__row"]) {
  cursor: pointer;
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

.skeleton-batch,
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

.skeleton-time {
  width: 140px;
}

.skeleton-action {
  width: 80px;
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

.form-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
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

:global([class~="el-dialog"][class~="review-config-dialog"]) {
  max-height: calc(100vh - 32px);
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

:global([class~="el-dialog"][class~="review-config-dialog"] [class~="el-dialog__body"]) {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
}

@media (max-width: 1180px) {
  .admin-reused-summary-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 760px) {
  .admin-reused-summary-grid,
  .reused-toolbar,
  .form-grid {
    grid-template-columns: 1fr;
  }

  .reused-panel-heading,
  .config-tab-pane {
    padding-right: 18px;
    padding-left: 18px;
  }

  .config-tabs {
    overflow-x: auto;
    padding: 0 18px;
  }
}
</style>
