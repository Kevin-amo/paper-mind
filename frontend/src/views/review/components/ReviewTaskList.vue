<script setup lang="ts">
import { computed } from 'vue';
import { statusLabel } from '../../../constants/review';
import { formatDate, textValue } from '../../../utils/format';
import type { ReviewTask } from '../../../types';

const props = defineProps<{
  tasks: ReviewTask[];
  selectedTaskId: string | null;
  loading: boolean;
  keyword: string;
  pagination: { page: number; size: number; total: number };
}>();

defineEmits<{
  'update:keyword': [value: string];
  search: [];
  select: [taskId: string];
  'page-change': [page: number];
}>();

const activeTasksCount = computed(() => props.tasks.length);

function taskStatus(task: ReviewTask) {
  return task.currentAssignment?.status ?? task.status;
}

function taskKeywords(task: ReviewTask) {
  return textValue(task.document?.keywords, '');
}
</script>

<template>
  <aside class="review-task-inbox">
    <div class="inbox-header">
      <div>
        <p>Inbox</p>
        <h2>任务收件箱</h2>
      </div>
      <el-button size="small" :loading="loading" @click="$emit('search')">刷新</el-button>
    </div>

    <div class="task-toolbar">
      <el-input
        :model-value="keyword"
        clearable
        placeholder="搜索论文标题 / 编号"
        @update:model-value="$emit('update:keyword', $event)"
        @keyup.enter="$emit('search')"
      />
    </div>

    <div v-loading="loading" class="task-list">
      <button
        v-for="task in tasks"
        :key="task.id"
        class="task-card"
        :class="{ active: selectedTaskId === task.id }"
        type="button"
        @click="$emit('select', task.id)"
      >
        <span class="task-status-badge" :class="taskStatus(task)">
          {{ statusLabel(taskStatus(task)) }}
        </span>
        <h3>{{ task.title }}</h3>
        <div class="task-meta">
          <span>{{ task.sourceId }}</span>
          <span v-if="taskKeywords(task)">{{ taskKeywords(task) }}</span>
        </div>
        <div class="task-footer">
          <span>{{ task.dueAt ? `截止 ${formatDate(task.dueAt)}` : `更新 ${formatDate(task.updatedAt)}` }}</span>
          <strong v-if="task.report?.totalScore != null">{{ Math.round(Number(task.report.totalScore)) }} 分</strong>
        </div>
      </button>

      <section v-if="!loading && !tasks.length" class="review-empty-state">
        <div class="review-doc-icon" aria-hidden="true"></div>
        <strong>暂无匹配任务</strong>
        <p>调整筛选条件，或刷新后查看新分配的论文评审任务。</p>
      </section>
    </div>

    <div v-if="pagination.total > pagination.size" class="inbox-pagination">
      <span>共 {{ pagination.total }} 项</span>
      <el-pagination
        small
        background
        layout="prev, pager, next"
        :total="pagination.total"
        :page-size="pagination.size"
        :current-page="pagination.page + 1"
        @current-change="(page: number) => $emit('page-change', page)"
      />
    </div>
    <div v-else class="inbox-footer-note">当前列表 {{ activeTasksCount }} 项</div>
  </aside>
</template>

<style scoped>
.review-task-inbox {
  display: flex;
  flex-direction: column;
  gap: 14px;
  border: 1px solid var(--app-border);
  border-radius: var(--app-radius-lg);
  background: var(--app-surface);
  padding: 22px;
}

.inbox-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding-bottom: 14px;
  border-bottom: 1px solid var(--app-border);
}

.inbox-header p {
  margin: 0 0 4px;
  color: var(--app-primary);
  font-size: 12px;
  font-weight: 500;
  letter-spacing: 0;
  text-transform: uppercase;
}

.inbox-header h2 {
  margin: 0;
  color: var(--app-text);
  font-family: var(--claude-serif);
  font-size: 28px;
  font-weight: 500;
  letter-spacing: 0;
  line-height: 1.15;
}

.task-toolbar {
  display: grid;
  gap: 10px;
}

.task-list {
  display: grid;
  gap: 10px;
  min-height: 420px;
  align-content: start;
}

.task-card {
  position: relative;
  display: grid;
  gap: 10px;
  width: 100%;
  border: 1px solid var(--app-border);
  border-radius: var(--app-radius-lg);
  background: #fffefa;
  color: inherit;
  padding: 14px;
  cursor: pointer;
  text-align: left;
  transition:
    background-color 0.2s ease,
    border-color 0.2s ease,
    box-shadow 0.2s ease;
}

.task-card:hover {
  border-color: var(--app-border-strong);
  background: var(--app-surface-strong);
}

.task-card.active {
  border-color: rgba(204, 120, 92, 0.36);
  background: var(--app-surface-soft);
  box-shadow: inset 4px 0 0 var(--app-primary);
}

.task-card h3 {
  margin: 0;
  padding-right: 88px;
  color: var(--app-text);
  font-family: Inter, -apple-system, BlinkMacSystemFont, "Segoe UI", "Microsoft YaHei", sans-serif;
  font-size: 14px;
  font-weight: 600;
  letter-spacing: 0;
  line-height: 1.45;
}

.task-status-badge {
  position: absolute;
  top: 14px;
  right: 14px;
  border-radius: 999px;
  background: var(--app-surface-soft);
  color: var(--app-text-muted);
  padding: 4px 9px;
  font-size: 12px;
  font-weight: 500;
  white-space: nowrap;
}

.task-status-badge.ASSIGNED {
  background: var(--app-primary-soft);
  color: var(--app-primary);
}

.task-status-badge.REVIEWING {
  background: var(--app-warning-soft);
  color: var(--app-warning-hover);
}

.task-status-badge.SUBMITTED {
  background: var(--app-success-soft);
  color: var(--app-success-hover);
}

.task-status-badge.RETURNED {
  background: var(--app-danger-soft);
  color: var(--app-danger);
}

.task-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  color: var(--app-text-muted);
  font-size: 12px;
}

.task-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  color: var(--app-text-subtle);
  font-size: 12px;
}

.task-footer strong {
  color: var(--app-primary);
  font-size: 12px;
  font-weight: 600;
}

.review-empty-state {
  display: grid;
  justify-items: center;
  border: 1px dashed var(--app-border);
  border-radius: var(--app-radius-lg);
  background: linear-gradient(180deg, rgba(245, 240, 232, 0.58), rgba(250, 249, 245, 0));
  padding: 26px 18px;
  text-align: center;
}

.review-doc-icon {
  position: relative;
  width: 32px;
  height: 42px;
  margin-bottom: 12px;
  border: 1.5px solid var(--app-border);
  border-radius: 7px;
  background: var(--app-surface);
}

.review-doc-icon::before {
  position: absolute;
  top: -1.5px;
  right: -1.5px;
  width: 13px;
  height: 13px;
  border-bottom: 1.5px solid var(--app-border);
  border-left: 1.5px solid var(--app-border);
  border-radius: 0 7px 0 4px;
  background: var(--app-surface-strong);
  content: "";
}

.review-doc-icon::after {
  position: absolute;
  top: 20px;
  left: 9px;
  right: 9px;
  height: 1.5px;
  background: var(--app-border);
  box-shadow: 0 7px 0 var(--app-border);
  content: "";
}

.review-empty-state strong {
  color: var(--app-text);
  font-size: 14px;
  font-weight: 600;
}

.review-empty-state p {
  margin: 6px 0 0;
  color: var(--app-text-muted);
  font-size: 13px;
  line-height: 1.65;
}

.inbox-pagination {
  display: grid;
  gap: 8px;
  padding-top: 10px;
  border-top: 1px solid var(--app-border);
}

.inbox-pagination span,
.inbox-footer-note {
  color: var(--app-text-subtle);
  font-size: 12px;
}
</style>
