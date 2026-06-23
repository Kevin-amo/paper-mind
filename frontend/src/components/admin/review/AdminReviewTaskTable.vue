<script setup lang="ts">
import type { AdminReviewTaskSummary } from '../../../types';

defineProps<{
  tasks: AdminReviewTaskSummary[];
  loading: boolean;
}>();

const emit = defineEmits<{
  open: [task: AdminReviewTaskSummary];
  dispatch: [task: AdminReviewTaskSummary];
}>();

function formatDate(value: string | null) {
  return value ? new Date(value).toLocaleString() : '-';
}

function progress(task: AdminReviewTaskSummary) {
  if (!task.assignmentCount) {
    return 0;
  }
  return Math.round((task.submittedCount / task.assignmentCount) * 100);
}

function statusType(status: string) {
  if (status === 'COMPLETED') return 'success';
  if (status === 'FAILED') return 'danger';
  if (status === 'REVIEWING') return 'warning';
  return 'info';
}

function reviewerName(task: AdminReviewTaskSummary) {
  return task.leadReviewerDisplayName || task.leadReviewerUsername || task.leadReviewerUserId || '-';
}

function canDispatch(task: AdminReviewTaskSummary) {
  return task.status === 'PENDING_ASSIGNMENT' && task.assignmentCount === 0;
}
</script>

<template>
  <el-table :data="tasks" v-loading="loading" class="review-task-table">
    <el-table-column prop="title" label="标题" min-width="240" show-overflow-tooltip />
    <el-table-column label="状态" width="120">
      <template #default="{ row }">
        <el-tag :type="statusType(row.status)" size="small">{{ row.status }}</el-tag>
      </template>
    </el-table-column>
    <el-table-column label="进度" min-width="160">
      <template #default="{ row }">
        <div class="progress-cell">
          <el-progress :percentage="progress(row)" :stroke-width="8" />
          <span>{{ row.submittedCount }}/{{ row.assignmentCount }}</span>
        </div>
      </template>
    </el-table-column>
    <el-table-column label="负责人" min-width="140" show-overflow-tooltip>
      <template #default="{ row }">{{ reviewerName(row) }}</template>
    </el-table-column>
    <el-table-column label="截止时间" min-width="160">
      <template #default="{ row }">{{ formatDate(row.dueAt) }}</template>
    </el-table-column>
    <el-table-column label="共识" width="130">
      <template #default="{ row }">
        <el-tag :type="row.consensusStatus === 'CONFIRMED' ? 'success' : 'info'" size="small">
          {{ row.consensusStatus || '-' }}
        </el-tag>
      </template>
    </el-table-column>
    <el-table-column label="操作" width="120" fixed="right" align="center">
      <template #default="{ row }">
        <div class="task-actions">
          <el-button size="small" class="task-action-button detail-button" @click="emit('open', row)">详情</el-button>
          <el-button v-if="canDispatch(row)" size="small" class="task-action-button dispatch-button" @click="emit('dispatch', row)">分配</el-button>
        </div>
      </template>
    </el-table-column>
  </el-table>
</template>

<style scoped>
.review-task-table {
  border: 1px solid var(--app-border);
  border-radius: var(--app-radius-sm);
  overflow: hidden;
}

.progress-cell {
  display: grid;
  grid-template-columns: minmax(80px, 1fr) auto;
  align-items: center;
  gap: 10px;
}

.progress-cell span {
  color: var(--app-text-muted);
  font-size: 12px;
  font-weight: 500;
}

.task-actions {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex-wrap: nowrap;
  gap: 6px;
  width: 100%;
}

.task-action-button {
  min-width: 52px;
  height: 32px;
  padding: 0 10px;
  font-size: 12px;
  line-height: 1;
  white-space: nowrap;
}

.detail-button {
  --el-button-bg-color: var(--app-surface);
  --el-button-border-color: var(--app-border);
  --el-button-text-color: var(--app-text);
}

.dispatch-button {
  --el-button-bg-color: var(--app-primary-soft);
  --el-button-border-color: rgba(204, 120, 92, 0.26);
  --el-button-text-color: var(--app-primary);
  --el-button-hover-bg-color: rgba(204, 120, 92, 0.16);
  --el-button-hover-border-color: rgba(204, 120, 92, 0.4);
  --el-button-hover-text-color: var(--app-primary-active);
}
</style>
