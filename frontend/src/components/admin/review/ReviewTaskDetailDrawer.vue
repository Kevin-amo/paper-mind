<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import { listAdminTaskAuditLogs } from '../../../api/adminReviews';
import { getErrorMessage } from '../../../api/http';
import { reviewAuditActionLabel } from '../../../constants/reviewAudit';
import { ElMessage } from 'element-plus';
import type { AdminReviewTaskDetail, ReviewAssignment, ReviewAuditLog, ReviewReport } from '../../../types';

const props = defineProps<{
  modelValue: boolean;
  taskDetail: AdminReviewTaskDetail | null;
  loading?: boolean;
}>();

const emit = defineEmits<{
  'update:modelValue': [value: boolean];
}>();

const visible = computed({
  get: () => props.modelValue,
  set: (value: boolean) => emit('update:modelValue', value),
});

const auditLogs = ref<ReviewAuditLog[]>([]);
const auditLoading = ref(false);

function formatDate(value: string | null | undefined) {
  return value ? new Date(value).toLocaleString() : '-';
}

function assignmentReviewer(assignment: ReviewAssignment) {
  return assignment.reviewerDisplayName || assignment.reviewerUsername || assignment.reviewerUserId || '-';
}

function reportReviewer(report: ReviewReport) {
  return report.reviewerDisplayName || report.reviewerUsername || report.reviewerUserId || '-';
}

function operatorLabel(log: ReviewAuditLog) {
  return log.operatorDisplayName || log.operatorUsername || log.operatorUserId || '系统';
}

async function loadAuditLogs(taskId: string | null | undefined) {
  if (!taskId) {
    auditLogs.value = [];
    return;
  }
  auditLoading.value = true;
  try {
    auditLogs.value = await listAdminTaskAuditLogs(taskId);
  } catch (error) {
    ElMessage.error(getErrorMessage(error));
    auditLogs.value = [];
  } finally {
    auditLoading.value = false;
  }
}

watch(
  () => props.taskDetail?.task.id ?? null,
  (taskId) => {
    loadAuditLogs(taskId);
  },
  { immediate: true },
);
</script>

<template>
  <el-drawer v-model="visible" size="min(820px, 96vw)" destroy-on-close>
    <template #header>
      <div>
        <span class="eyebrow">Review Task</span>
        <h3>评审任务详情</h3>
      </div>
    </template>

    <el-empty v-if="!taskDetail" description="请选择评审任务" />
    <div v-else class="detail-body" v-loading="loading">
      <section class="task-hero">
        <div>
          <span>{{ taskDetail.task.sourceId }}</span>
          <h4>{{ taskDetail.task.title }}</h4>
        </div>
        <el-tag size="large" effect="plain">{{ taskDetail.task.status }}</el-tag>
      </section>

      <el-descriptions :column="2" border>
        <el-descriptions-item label="任务 ID">{{ taskDetail.task.id }}</el-descriptions-item>
        <el-descriptions-item label="文档 ID">{{ taskDetail.task.documentId }}</el-descriptions-item>
        <el-descriptions-item label="提交人">{{ taskDetail.task.submitterUserId }}</el-descriptions-item>
        <el-descriptions-item label="负责人">{{ taskDetail.task.reviewerUserId || '-' }}</el-descriptions-item>
        <el-descriptions-item label="分配时间">{{ formatDate(taskDetail.task.assignedAt) }}</el-descriptions-item>
        <el-descriptions-item label="截止时间">{{ formatDate(taskDetail.task.dueAt) }}</el-descriptions-item>
        <el-descriptions-item label="完成时间">{{ formatDate(taskDetail.task.completedAt) }}</el-descriptions-item>
        <el-descriptions-item label="更新时间">{{ formatDate(taskDetail.task.updatedAt) }}</el-descriptions-item>
      </el-descriptions>

      <section class="detail-section">
        <h4>评审分配</h4>
        <el-table :data="taskDetail.assignments" empty-text="暂无分配记录">
          <el-table-column label="评审人" min-width="220" show-overflow-tooltip>
            <template #default="{ row }">{{ assignmentReviewer(row) }}</template>
          </el-table-column>
          <el-table-column prop="role" label="角色" width="110" />
          <el-table-column prop="status" label="状态" width="130" />
          <el-table-column label="截止时间" min-width="170">
            <template #default="{ row }">{{ formatDate(row.dueAt) }}</template>
          </el-table-column>
          <el-table-column label="提交时间" min-width="170">
            <template #default="{ row }">{{ formatDate(row.submittedAt) }}</template>
          </el-table-column>
        </el-table>
      </section>

      <section class="detail-section">
        <h4>已提交报告</h4>
        <el-table :data="taskDetail.submittedReports" empty-text="暂无提交报告">
          <el-table-column label="评审人" min-width="220" show-overflow-tooltip>
            <template #default="{ row }">{{ reportReviewer(row) }}</template>
          </el-table-column>
          <el-table-column prop="status" label="状态" width="140" />
          <el-table-column prop="totalScore" label="总分" width="100" />
          <el-table-column prop="finalRecommendation" label="最终建议" min-width="180" show-overflow-tooltip />
          <el-table-column label="更新时间" min-width="170">
            <template #default="{ row }">{{ formatDate(row.updatedAt) }}</template>
          </el-table-column>
        </el-table>
      </section>

      <section class="detail-section">
        <h4>共识状态</h4>
        <el-descriptions :column="2" border>
          <el-descriptions-item label="状态">{{ taskDetail.consensus?.status || '-' }}</el-descriptions-item>
          <el-descriptions-item label="负责人">
            {{
              taskDetail.consensus?.leadReviewerDisplayName ||
              taskDetail.consensus?.leadReviewerUsername ||
              taskDetail.consensus?.leadReviewerUserId ||
              '-'
            }}
          </el-descriptions-item>
          <el-descriptions-item label="最终得分">{{ taskDetail.consensus?.finalScore ?? '-' }}</el-descriptions-item>
          <el-descriptions-item label="确认时间">{{ formatDate(taskDetail.consensus?.confirmedAt) }}</el-descriptions-item>
          <el-descriptions-item label="最终建议" :span="2">
            {{ taskDetail.consensus?.finalRecommendation || '-' }}
          </el-descriptions-item>
        </el-descriptions>
      </section>

      <section class="detail-section">
        <div class="section-title-row">
          <h4>操作历史</h4>
          <span class="section-hint">按时间倒序展示该任务的审计日志</span>
        </div>
        <div v-loading="auditLoading" class="audit-timeline-wrap">
          <el-empty v-if="!auditLoading && auditLogs.length === 0" description="暂无操作记录" :image-size="60" />
          <el-timeline v-else>
            <el-timeline-item
              v-for="log in auditLogs"
              :key="log.id"
              :timestamp="formatDate(log.createdAt)"
              placement="top"
              type="primary"
            >
              <div class="audit-item">
                <div class="audit-item-header">
                  <el-tag size="small" effect="plain">{{ reviewAuditActionLabel(log.action) }}</el-tag>
                  <span class="audit-operator">{{ operatorLabel(log) }}</span>
                </div>
                <p v-if="log.note" class="audit-note">{{ log.note }}</p>
              </div>
            </el-timeline-item>
          </el-timeline>
        </div>
      </section>
    </div>
  </el-drawer>
</template>

<style scoped>
.eyebrow {
  color: var(--app-primary);
  font-size: 12px;
  font-weight: 800;
  letter-spacing: 0.12em;
  text-transform: uppercase;
}

h3,
h4 {
  margin: 0;
}

.detail-body {
  display: flex;
  flex-direction: column;
  gap: 18px;
}

.task-hero {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  border: 1px solid var(--app-border);
  border-radius: 10px;
  padding: 16px;
  background: #f8fafc;
}

.task-hero span {
  display: block;
  margin-bottom: 6px;
  color: #667085;
  font-size: 12px;
}

.task-hero h4 {
  color: #101828;
  font-size: 18px;
  line-height: 1.4;
}

.detail-section {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.section-title-row {
  display: flex;
  align-items: baseline;
  gap: 10px;
  flex-wrap: wrap;
}

.section-hint {
  color: var(--app-text-subtle);
  font-size: 12px;
}

.audit-timeline-wrap {
  min-height: 60px;
}

.audit-item-header {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

.audit-operator {
  color: var(--app-text-muted);
  font-size: 13px;
}

.audit-note {
  margin: 6px 0 0;
  color: var(--app-text-muted);
  font-size: 12px;
  line-height: 1.6;
}

@media (max-width: 720px) {
  .task-hero {
    flex-direction: column;
  }
}
</style>
