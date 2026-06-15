<script setup lang="ts">
import { onMounted, ref, watch } from 'vue';
import { listAuditLogs } from '../../../api/reviews';
import { getErrorMessage } from '../../../api/http';
import { ElMessage } from 'element-plus';
import type { ReviewAuditLog, ReviewReport } from '../../../types';

const props = defineProps<{
  selectedReport: ReviewReport | null;
  taskId: string | null;
}>();

const auditLogs = ref<ReviewAuditLog[]>([]);
const auditLoading = ref(false);

const actionLabels: Record<string, string> = {
  CREATE_TASK: '创建任务',
  AI_REVIEW: '生成 AI 评审',
  ADJUST_REPORT: '人工调整报告',
  ASSIGN: '分配评审人',
  ASSIGN_BY_ADMIN_OVERRIDE: '管理员兜底分配',
  ASSIGN_BY_LEADER: '组长分配本组评审任务',
  JOIN_REVIEW_BY_LEADER: '组长加入本组评审任务',
  DISPATCH_TO_GROUP: '管理员派发评审任务到小组',
  RETURN: '退回评审',
  CANCEL_ASSIGNMENT: '取消分配',
  SUBMIT: '提交评审',
  SUBMIT_ASSIGNMENT: '提交个人评审任务',
  UPDATE_CONSENSUS: '更新共识',
  CONFIRM_CONSENSUS: '确认共识',
  RECALCULATE_CONSENSUS: '重新计算共识',
};

function actionLabel(action: string): string {
  return actionLabels[action] ?? action;
}

function formatTime(iso: string): string {
  try {
    return new Date(iso).toLocaleString('zh-CN', {
      year: 'numeric',
      month: '2-digit',
      day: '2-digit',
      hour: '2-digit',
      minute: '2-digit',
      second: '2-digit',
    });
  } catch {
    return iso;
  }
}

async function loadAuditLogs() {
  if (!props.taskId) {
    auditLogs.value = [];
    return;
  }
  auditLoading.value = true;
  try {
    auditLogs.value = await listAuditLogs(props.taskId);
  } catch (error) {
    ElMessage.error(getErrorMessage(error));
  } finally {
    auditLoading.value = false;
  }
}

onMounted(loadAuditLogs);
watch(() => props.taskId, loadAuditLogs);
</script>

<template>
  <section class="detail-section">
    <div class="section-header">
      <h3>评审留档信息</h3>
      <p>模型与评审操作记录</p>
    </div>
    <div v-if="selectedReport" class="audit-grid">
      <article>
        <span>模型版本</span>
        <strong>{{ selectedReport.modelVersion || '-' }}</strong>
      </article>
    </div>
    <el-empty v-if="!selectedReport" description="生成辅助评审后展示留档信息" />

    <div class="audit-timeline-section">
      <h4>操作审计日志</h4>
      <div v-if="auditLoading" class="audit-loading">加载中...</div>
      <div v-else-if="auditLogs.length === 0" class="audit-empty">暂无操作记录</div>
      <ul v-else class="audit-timeline">
        <li v-for="log in auditLogs" :key="log.id" class="timeline-item">
          <div class="timeline-dot"></div>
          <div class="timeline-content">
            <div class="timeline-header">
              <span class="timeline-action">{{ actionLabel(log.action) }}</span>
              <span v-if="log.operatorDisplayName" class="timeline-operator">{{ log.operatorDisplayName }}</span>
              <span v-else-if="log.operatorUsername" class="timeline-operator">{{ log.operatorUsername }}</span>
            </div>
            <p v-if="log.note" class="timeline-note">{{ log.note }}</p>
            <time class="timeline-time">{{ formatTime(log.createdAt) }}</time>
          </div>
        </li>
      </ul>
    </div>
  </section>
</template>

<style scoped>
.detail-section {
  margin-top: 20px;
}

.section-header {
  margin-bottom: 14px;
  padding-bottom: 12px;
  border-bottom: 1px solid var(--app-border);
}

.section-header h3 {
  margin: 0;
  color: var(--app-text);
  font-size: 16px;
  font-weight: 700;
}

.section-header p {
  margin: 4px 0 0;
  color: var(--app-text-muted);
  font-size: 13px;
}

.audit-grid {
  display: grid;
  grid-template-columns: minmax(220px, 360px);
  gap: 12px;
}

.audit-grid article {
  border: 1px solid var(--app-border);
  border-radius: var(--app-radius-sm);
  padding: 14px;
  background: var(--app-surface);
}

.audit-grid span {
  display: block;
  color: var(--app-text-muted);
  font-size: 12px;
  font-weight: 500;
}

.audit-grid strong {
  display: block;
  margin-top: 6px;
  color: var(--app-text);
  font-size: 14px;
  line-height: 1.5;
}

.audit-timeline-section {
  margin-top: 20px;
  padding-top: 16px;
  border-top: 1px solid var(--app-border);
}

.audit-timeline-section h4 {
  margin: 0 0 12px;
  color: var(--app-text);
  font-size: 14px;
  font-weight: 600;
}

.audit-loading,
.audit-empty {
  color: var(--app-text-muted);
  font-size: 13px;
  padding: 8px 0;
}

.audit-timeline {
  list-style: none;
  margin: 0;
  padding: 0;
}

.timeline-item {
  display: flex;
  gap: 12px;
  padding-bottom: 16px;
  position: relative;
}

.timeline-item:not(:last-child)::before {
  content: '';
  position: absolute;
  left: 5px;
  top: 14px;
  bottom: 0;
  width: 1px;
  background: var(--app-border);
}

.timeline-dot {
  flex-shrink: 0;
  width: 11px;
  height: 11px;
  margin-top: 5px;
  border-radius: 50%;
  border: 2px solid var(--app-primary);
  background: var(--app-surface);
}

.timeline-content {
  flex: 1;
  min-width: 0;
}

.timeline-header {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.timeline-action {
  color: var(--app-text);
  font-size: 13px;
  font-weight: 600;
}

.timeline-operator {
  color: var(--app-text-muted);
  font-size: 12px;
}

.timeline-operator::before {
  content: 'by ';
}

.timeline-note {
  margin: 4px 0 0;
  color: var(--app-text-muted);
  font-size: 12px;
}

.timeline-time {
  display: block;
  margin-top: 2px;
  color: var(--app-text-muted);
  font-size: 11px;
}

@media (max-width: 1180px) {
  .audit-grid {
    grid-template-columns: minmax(220px, 360px);
  }
}

@media (max-width: 720px) {
  .audit-grid {
    grid-template-columns: 1fr;
  }
}
</style>
