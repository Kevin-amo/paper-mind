<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { Document, List, TrendCharts, UserFilled } from '@element-plus/icons-vue';
import AdminShell from '../../components/admin/AdminShell.vue';
import AdminReviewTaskTable from '../../components/admin/review/AdminReviewTaskTable.vue';
import ReviewBatchGroupPanel from '../../components/admin/review/ReviewBatchGroupPanel.vue';
import ReviewCriteriaPanel from '../../components/admin/review/ReviewCriteriaPanel.vue';
import ReviewDispatchDrawer from '../../components/admin/review/ReviewDispatchDrawer.vue';
import ReviewTaskDetailDrawer from '../../components/admin/review/ReviewTaskDetailDrawer.vue';
import { useAdminReviews } from '../../composables/useAdminReviews';
import type { AdminReviewTaskSummary, DispatchReviewTaskPayload } from '../../types';

const adminReviews = useAdminReviews();
const route = useRoute();
const router = useRouter();
const validTabs = ['config', 'tasks', 'criteria'] as const;
type ReviewAdminTab = (typeof validTabs)[number];

const activeTab = ref<ReviewAdminTab>(normalizeTab(route.query.tab));
const detailVisible = ref(false);
const dispatchVisible = ref(false);

const submittedTotal = computed(() => adminReviews.tasks.value.reduce((sum, task) => sum + task.submittedCount, 0));
const assignmentTotal = computed(() => adminReviews.tasks.value.reduce((sum, task) => sum + task.assignmentCount, 0));
const activeSectionTitle = computed(() => {
  const titles: Record<ReviewAdminTab, string> = {
    config: '批次与小组',
    tasks: '全局进度',
    criteria: '评审指标',
  };
  return titles[activeTab.value];
});

watch(
  () => route.query.tab,
  (tab) => {
    activeTab.value = normalizeTab(tab);
  },
);

watch(activeTab, async (tab) => {
  if (route.query.tab === tab) return;
  await router.replace({ path: '/admin/reviews', query: { ...route.query, tab } });
});

function normalizeTab(tab: unknown): ReviewAdminTab {
  return typeof tab === 'string' && validTabs.includes(tab as ReviewAdminTab) ? (tab as ReviewAdminTab) : 'config';
}

async function openTask(task: AdminReviewTaskSummary) {
  const detail = await adminReviews.openTask(task.id);
  detailVisible.value = Boolean(detail);
}

async function openDispatch(task: AdminReviewTaskSummary) {
  const [detail] = await Promise.all([adminReviews.openTask(task.id), adminReviews.loadGroups()]);
  dispatchVisible.value = Boolean(detail);
}

async function saveDispatch(taskId: string, payload: DispatchReviewTaskPayload) {
  await adminReviews.dispatchTask(taskId, payload);
  dispatchVisible.value = false;
}

function handlePageSizeChange(nextSize: number) {
  adminReviews.size.value = nextSize;
  adminReviews.loadTasks(0);
}

function handlePageChange(nextPage: number) {
  adminReviews.loadTasks(nextPage - 1);
}

onMounted(async () => {
  await Promise.all([adminReviews.loadTasks(0), adminReviews.loadReviewerLoads(), adminReviews.loadGroups()]);
});
</script>

<template>
  <AdminShell :active="activeTab" :title="activeSectionTitle">
    <ReviewBatchGroupPanel v-if="activeTab === 'config'" />

    <section v-if="activeTab === 'tasks'" class="summary-grid">
      <div
        class="summary-card animate slide-up"
        v-animate="{ type: 'slide-up', delay: '0ms', duration: '0.6s' }"
      >
        <div class="summary-icon blue">
          <el-icon :size="20"><Document /></el-icon>
        </div>
        <div class="summary-body">
          <span class="summary-label">任务总数</span>
          <strong class="summary-value">{{ adminReviews.total.value }}</strong>
        </div>
      </div>
      <div
        class="summary-card animate slide-up"
        v-animate="{ type: 'slide-up', delay: '80ms', duration: '0.6s' }"
      >
        <div class="summary-icon indigo">
          <el-icon :size="20"><List /></el-icon>
        </div>
        <div class="summary-body">
          <span class="summary-label">当前页任务</span>
          <strong class="summary-value">{{ adminReviews.tasks.value.length }}</strong>
        </div>
      </div>
      <div
        class="summary-card animate slide-up"
        v-animate="{ type: 'slide-up', delay: '160ms', duration: '0.6s' }"
      >
        <div class="summary-icon green">
          <el-icon :size="20"><TrendCharts /></el-icon>
        </div>
        <div class="summary-body">
          <span class="summary-label">提交进度</span>
          <strong class="summary-value">{{ submittedTotal }}<span class="summary-denom">/{{ assignmentTotal }}</span></strong>
        </div>
      </div>
      <div
        class="summary-card animate slide-up"
        v-animate="{ type: 'slide-up', delay: '240ms', duration: '0.6s' }"
      >
        <div class="summary-icon amber">
          <el-icon :size="20"><UserFilled /></el-icon>
        </div>
        <div class="summary-body">
          <span class="summary-label">评审员</span>
          <strong class="summary-value">{{ adminReviews.reviewerLoads.value.length }}</strong>
        </div>
      </div>
    </section>

    <section
      v-if="activeTab !== 'config'"
      class="paper-mind-workspace-card review-dashboard-panel animate fade-in"
      v-animate="{ type: 'fade-in', delay: '0.1s', duration: '0.6s' }"
    >
      <div v-if="activeTab === 'tasks'">
        <div class="section-header">
          <h3>全局进度</h3>
          <p>查看所有评审任务进度；普通分配与共识确认由评审组长处理。</p>
        </div>
        <div class="toolbar">
          <el-input
            v-model="adminReviews.keyword.value"
            clearable
            placeholder="搜索标题 / 任务"
            @keyup.enter="adminReviews.loadTasks(0)"
          />
          <el-select v-model="adminReviews.status.value" clearable placeholder="任务状态" class="status-select">
            <el-option label="待分配" value="PENDING_ASSIGNMENT" />
            <el-option label="已分配" value="ASSIGNED" />
            <el-option label="评审中" value="IN_REVIEW" />
            <el-option label="已提交" value="SUBMITTED" />
            <el-option label="共识已确认" value="CONSENSUS_CONFIRMED" />
          </el-select>
          <el-button @click="adminReviews.loadTasks(0)">搜索</el-button>
          <el-button type="primary" @click="adminReviews.loadTasks(adminReviews.page.value)">刷新</el-button>
        </div>

        <AdminReviewTaskTable
          :tasks="adminReviews.tasks.value"
          :loading="adminReviews.loading.value"
          @open="openTask"
          @dispatch="openDispatch"
        />

        <div class="pagination-wrap">
          <el-pagination
            background
            layout="total, sizes, prev, pager, next"
            :total="adminReviews.total.value"
            :page-size="adminReviews.size.value"
            :current-page="adminReviews.page.value + 1"
            :page-sizes="[10, 20, 50]"
            @size-change="handlePageSizeChange"
            @current-change="handlePageChange"
          />
        </div>
      </div>

      <div v-if="activeTab === 'criteria'">
        <div class="section-header">
          <h3>评审指标</h3>
          <p>查看当前评审标准、评分维度和权重说明。</p>
        </div>
        <ReviewCriteriaPanel />
      </div>
    </section>

    <ReviewDispatchDrawer
      v-model="dispatchVisible"
      :task="adminReviews.selectedTask.value"
      :groups="adminReviews.reviewGroups.value"
      @submit="saveDispatch"
    />
    <ReviewTaskDetailDrawer
      v-model="detailVisible"
      :task-detail="adminReviews.selectedTask.value"
      :loading="adminReviews.loading.value"
    />
  </AdminShell>
</template>

<style scoped>
.summary-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  align-self: start;
  align-items: start;
  gap: 16px;
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

.summary-icon.blue {
  background: var(--app-primary-soft);
  color: var(--app-primary);
}

.summary-icon.indigo {
  background: var(--app-accent-soft);
  color: var(--app-accent);
}

.summary-icon.green {
  background: var(--app-success-soft);
  color: var(--app-success);
}

.summary-icon.amber {
  background: var(--app-warning-soft);
  color: var(--app-warning);
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

.summary-denom {
  color: var(--app-text-subtle);
  font-size: 16px;
  font-weight: 500;
}

.review-dashboard-panel {
  border: 1px solid var(--app-border);
  border-radius: var(--app-radius-lg);
  padding: 24px;
  background: var(--claude-canvas);
}

.section-header {
  margin-bottom: 16px;
  padding-bottom: 14px;
  border-bottom: 1px solid var(--app-border);
}

.section-header h3 {
  margin: 0;
  color: var(--app-text);
  font-size: 22px;
  font-weight: 500;
}

.section-header p {
  margin: 4px 0 0;
  color: var(--app-text-muted);
  font-size: 13px;
  line-height: 1.6;
}

.toolbar {
  display: grid;
  grid-template-columns: minmax(200px, 1fr) 160px auto auto;
  gap: 10px;
  margin-bottom: 16px;
}

.status-select {
  width: 160px;
}

.toolbar :deep([class~="el-input__wrapper"]),
.toolbar :deep([class~="el-select__wrapper"]) {
  min-height: 36px;
  border-radius: var(--app-radius-sm) !important;
  box-shadow: none !important;
  border: 1px solid var(--app-border);
}

.toolbar :deep([class~="el-input__wrapper"]:hover),
.toolbar :deep([class~="el-select__wrapper"]:hover) {
  border-color: var(--app-border-strong);
}

.toolbar :deep([class~="el-input__wrapper"][class~="is-focus"]),
.toolbar :deep([class~="el-select__wrapper"]:focus-within) {
  border-color: var(--app-primary);
}

.pagination-wrap {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
  margin-top: 16px;
}

@media (max-width: 980px) {
  .summary-grid,
  .toolbar {
    grid-template-columns: 1fr;
  }

  .status-select {
    width: 100%;
  }

  .section-header {
    align-items: flex-start;
  }
}
</style>
