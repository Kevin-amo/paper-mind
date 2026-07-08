<script setup lang="ts">
import { riskTypeMap, riskStatusLabel } from '../../../constants/review';
import type { ReviewRiskRecord } from '../../../types';

defineProps<{
  riskRecords: ReviewRiskRecord[];
  riskLoading: boolean;
  riskStatusUpdatingIds: string[];
}>();

defineEmits<{
  'set-risk-status': [riskId: string, status: 'CONFIRMED' | 'IGNORED'];
}>();

function isRiskUpdating(riskId: string, updatingIds: string[]) {
  return updatingIds.includes(riskId);
}

function isRiskActionDisabled(
  risk: ReviewRiskRecord,
  status: 'CONFIRMED' | 'IGNORED',
  updatingIds: string[],
) {
  return isRiskUpdating(risk.id, updatingIds) || risk.status === status;
}
</script>

<template>
  <section class="detail-section">
    <div class="section-header">
      <h3>风险提示</h3>
      <p>政治表述、参考文献、结构与语言风险的规范化记录</p>
    </div>
    <div v-loading="riskLoading">
      <div v-if="riskRecords.length" class="risk-list">
        <article v-for="risk in riskRecords" :key="risk.id" class="risk-card">
          <div class="risk-card-header">
            <div class="risk-type">
              <span class="risk-level-dot" :class="risk.riskLevel"></span>
              <strong>{{ risk.riskType }}</strong>
            </div>
            <el-tag size="small" effect="plain">{{ riskStatusLabel(risk.status) }}</el-tag>
          </div>
          <p class="risk-evidence">{{ risk.evidence || '未给出证据' }}</p>
          <p class="risk-suggestion">{{ risk.suggestion || '建议人工复核' }}</p>
          <div class="risk-actions">
            <el-button
              size="small"
              type="primary"
              plain
              :loading="isRiskUpdating(risk.id, riskStatusUpdatingIds)"
              :disabled="isRiskActionDisabled(risk, 'CONFIRMED', riskStatusUpdatingIds)"
              @click="$emit('set-risk-status', risk.id, 'CONFIRMED')"
            >
              确认
            </el-button>
            <el-button
              size="small"
              plain
              :loading="isRiskUpdating(risk.id, riskStatusUpdatingIds)"
              :disabled="isRiskActionDisabled(risk, 'IGNORED', riskStatusUpdatingIds)"
              @click="$emit('set-risk-status', risk.id, 'IGNORED')"
            >
              忽略
            </el-button>
          </div>
        </article>
      </div>
      <section v-else class="review-empty-state">
        <div class="review-doc-icon" aria-hidden="true"></div>
        <strong>暂无风险提示</strong>
        <p>生成辅助评审后，这里会展示需要人工确认的风险线索。</p>
      </section>
    </div>
  </section>
</template>

<style scoped>
.detail-section {
  margin-top: 20px;
}

.section-header {
  margin-bottom: 16px;
  padding-bottom: 12px;
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
}

.risk-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.risk-card {
  border: 1px solid var(--app-border);
  border-radius: var(--app-radius-lg);
  padding: 16px;
  background: var(--app-surface-soft);
  transition: border-color 0.15s ease;
}

.risk-card:hover {
  border-color: var(--app-border-strong);
}

.risk-card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}

.risk-type {
  display: flex;
  align-items: center;
  gap: 8px;
}

.risk-level-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  flex-shrink: 0;
}

.risk-level-dot.HIGH,
.risk-level-dot.CRITICAL {
  background: var(--app-danger);
}

.risk-level-dot.MEDIUM {
  background: var(--app-warning);
}

.risk-level-dot.LOW {
  background: var(--app-success);
}

.risk-type strong {
  color: var(--app-text);
  font-size: 14px;
  font-weight: 600;
}

.risk-evidence {
  margin: 10px 0 0;
  color: var(--app-text);
  font-size: 13px;
  line-height: 1.65;
}

.risk-suggestion {
  margin: 6px 0 0;
  color: var(--app-text-muted);
  font-size: 13px;
  line-height: 1.6;
}

.risk-actions {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-top: 12px;
  padding-top: 12px;
  border-top: 1px solid var(--app-border);
}

.review-empty-state {
  display: grid;
  justify-items: center;
  border: 1px dashed var(--app-border);
  border-radius: var(--app-radius-lg);
  background: linear-gradient(180deg, rgba(245, 240, 232, 0.58), rgba(250, 249, 245, 0));
  padding: 30px 18px;
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
</style>
