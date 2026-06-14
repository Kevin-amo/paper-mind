<script setup lang="ts">
import { onMounted, ref, watch } from 'vue';
import { formatJson } from '../../../utils/format';
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
  RETURN: '退回评审',
  CANCEL_ASSIGNMENT: '取消分配',
  SUBMIT: '提交评审',
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
      <p>模型、提示词、指标版本与人工调整记录</p>
    </div>
    <div v-if="selectedReport" class="audit-grid">
      <article>
        <span>模型版本</span>
        <strong>{{ selectedReport.modelVersion || '-' }}</strong>
      </article>
      <article>
        <span>Prompt 版本</span>
        <strong>{{ selectedReport.promptVersion || '-' }}</strong>
      </article>
      <article>
        <span>指标版本</span>
        <strong>{{ selectedReport.criterionVersion ?? '-' }}</strong>
      </article>
      <article>
        <span>置信度</span>
        <strong>{{ selectedReport.confidence != null ? (selectedReport.confidence * 100).toFixed(1) + '%' : '-' }}</strong>
      </article>
    </div>
    <div v-if="selectedReport?.manualDelta && Object.keys(selectedReport.manualDelta).length > 0" class="manual-delta-section">
      <h4>人工调整记录</h4>
      <pre class="manual-delta">{{ formatJson(selectedReport.manualDelta) }}</pre>
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
            <details v-if="log.diff && Object.keys(log.diff).length > 0" class="timeline-diff">
              <summary>变更详情</summary>
              <pre>{{ formatJson(log.diff) }}</pre>
            </details>
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
  grid-template-columns: repeat(4, minmax(0, 1fr));
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

.manual-delta-section {
  margin-top: 14px;
}

.manual-delta-section h4 {
  margin: 0 0 8px;
  color: var(--app-text);
  font-size: 14px;
  font-weight: 600;
}

.manual-delta {
  border: 1px solid var(--app-border);
  border-radius: var(--app-radius-sm);
  padding: 14px;
  background: var(--app-surface-soft);
  color: var(--app-text-muted);
  font-size: 13px;
  line-height: 1.6;
  white-space: pre-wrap;
  overflow-wrap: anywhere;
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

.timeline-diff {
  margin-top: 6px;
}

.timeline-diff summary {
  color: var(--app-primary);
  font-size: 12px;
  cursor: pointer;
  user-select: none;
}

.timeline-diff pre {
  margin: 6px 0 0;
  border: 1px solid var(--app-border);
  border-radius: var(--app-radius-sm);
  padding: 10px;
  background: var(--app-surface-soft);
  color: var(--app-text-muted);
  font-size: 12px;
  line-height: 1.5;
  white-space: pre-wrap;
  overflow-wrap: anywhere;
}

@media (max-width: 1180px) {
  .audit-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 720px) {
  .audit-grid {
    grid-template-columns: 1fr;
  }
}
</style>
