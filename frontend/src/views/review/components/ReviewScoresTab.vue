<script setup lang="ts">
import { computed } from 'vue';
import type { ReviewCriterion, ReviewReport, ReviewReportStatus, ReviewScoreItem } from '../../../types';

const props = defineProps<{
  scoreItems: ReviewScoreItem[];
  selectedReport: ReviewReport | null;
  assignmentSubmitted: boolean;
  saving: boolean;
  submittingAssignment: boolean;
  reportForm: { totalScore: number; finalRecommendation: string; status: ReviewReportStatus };
  criteria: ReviewCriterion[];
}>();

const finalScoreLabel = computed(() => {
  const score = props.assignmentSubmitted
    ? props.selectedReport?.totalScore ?? props.reportForm.totalScore
    : props.reportForm.totalScore ?? props.selectedReport?.totalScore;
  return Number.isFinite(Number(score)) ? formatNumber(score) : '--';
});

const finalRecommendationText = computed(() => {
  const recommendation = props.reportForm.finalRecommendation?.trim()
    || props.selectedReport?.finalRecommendation?.trim()
    || '';
  return recommendation || '暂无最终评审意见';
});

function getWeight(code: string): number {
  return props.criteria?.find((criterion) => criterion.code === code)?.weight ?? 0;
}

function toFiniteNumber(value: unknown, fallback = 0): number {
  const numberValue = Number(value);
  return Number.isFinite(numberValue) ? numberValue : fallback;
}

function formatNumber(value: unknown): string {
  const numberValue = toFiniteNumber(value);
  const rounded = Math.round(numberValue * 10) / 10;
  return Number.isInteger(rounded) ? String(rounded) : rounded.toFixed(1);
}

function metricPercent(item: ReviewScoreItem): number {
  const maxScore = toFiniteNumber(item.maxScore, 100);
  if (maxScore <= 0) {
    return 0;
  }
  const percent = (toFiniteNumber(item.score) / maxScore) * 100;
  return Math.min(100, Math.max(0, percent));
}

defineEmits<{
  'update-score': [code: string, score: number];
}>();
</script>

<template>
  <section class="detail-section" :aria-busy="saving || submittingAssignment">
    <div class="section-header">
      <h3>维度化辅助评分</h3>
      <p>总分和最终评审意见只保留这一处。维度评分调整后，会同步更新最终总分。</p>
    </div>

    <div v-if="scoreItems.length" class="score-sheet">
      <section class="score-summary" aria-label="评分摘要">
        <article class="score-total">
          <span>最终总分</span>
          <div class="score-number">{{ finalScoreLabel }}<small>/100</small></div>
          <p>{{ assignmentSubmitted ? '已提交留档，不再接受调整。' : '由维度评分和权重自动计算。' }}</p>
        </article>

        <article class="recommendation-box">
          <span>最终评审意见</span>
          <p v-if="assignmentSubmitted">{{ finalRecommendationText }}</p>
          <el-input
            v-else
            v-model="reportForm.finalRecommendation"
            class="recommendation-editor"
            type="textarea"
            :rows="4"
            placeholder="填写或调整最终评审意见"
          />
        </article>
      </section>

      <section class="metrics" aria-label="评分维度">
        <article v-for="item in scoreItems" :key="item.code" class="metric-row">
          <div class="metric-title">
            <strong>{{ item.name }}</strong>
            <span>{{ getWeight(item.code) ? `权重 ${getWeight(item.code)}%` : '未配置权重' }}</span>
          </div>

          <div class="metric-control">
            <div v-if="assignmentSubmitted" class="metric-track" aria-hidden="true">
              <div class="metric-fill" :style="{ width: `${metricPercent(item)}%` }"></div>
            </div>
            <el-slider
              v-else
              class="metric-slider"
              :model-value="Number(item.score)"
              :min="0"
              :max="Number(item.maxScore || 100)"
              :show-tooltip="false"
              @input="$emit('update-score', item.code, Array.isArray($event) ? $event[0] : $event)"
            />
          </div>

          <div class="metric-score">{{ formatNumber(item.score) }}<small>/{{ formatNumber(item.maxScore) }}</small></div>
          <p v-if="item.reason" class="metric-reason">{{ item.reason }}</p>
        </article>
      </section>

      <section v-if="assignmentSubmitted" class="submitted-panel">
        <div class="submitted-note">
          <span class="readonly-pill">只读留档</span>
          <p>本次个人评审已提交，评分、结论和最终意见不可继续调整。</p>
        </div>
      </section>
    </div>

    <section v-else class="review-empty-state">
      <div class="review-doc-icon" aria-hidden="true"></div>
      <strong>暂无评分建议</strong>
      <p>生成辅助评审后，这里会展示各维度评分、权重和理由。</p>
    </section>
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

.score-sheet {
  display: grid;
  gap: 14px;
}

.score-summary {
  display: grid;
  grid-template-columns: 174px minmax(0, 1fr);
  gap: 14px;
}

.score-total,
.recommendation-box {
  border: 1px solid var(--app-border);
  border-radius: var(--app-radius-lg);
  background: var(--app-surface-strong);
  padding: 18px;
}

.score-total span,
.recommendation-box span,
.metric-title span {
  display: block;
  color: var(--app-text-muted);
  font-size: 12px;
  font-weight: 600;
}

.score-number {
  display: flex;
  align-items: baseline;
  gap: 4px;
  margin-top: 8px;
  color: var(--app-text);
  font-family: var(--claude-serif);
  font-size: 52px;
  font-weight: 500;
  letter-spacing: -0.02em;
  line-height: 1;
}

.score-number small {
  color: var(--app-text-subtle);
  font-family: var(--claude-sans);
  font-size: 14px;
  font-weight: 500;
}

.score-total p,
.recommendation-box p {
  margin: 10px 0 0;
  color: var(--app-text-muted);
  font-size: 13px;
  line-height: 1.7;
}

.recommendation-box p {
  color: var(--app-text);
}

.recommendation-editor {
  margin-top: 10px;
}

.recommendation-editor :deep(.el-textarea__inner) {
  min-height: 104px;
  border-radius: var(--app-radius-md);
  background: var(--app-surface);
  color: var(--app-text);
  line-height: 1.65;
  box-shadow: 0 0 0 1px var(--app-border) inset;
}

.recommendation-editor :deep(.el-textarea__inner:focus) {
  box-shadow:
    0 0 0 1px var(--app-primary) inset,
    var(--app-shadow-focus);
}

.metrics {
  display: grid;
  gap: 10px;
}

.metric-row {
  display: grid;
  grid-template-columns: minmax(132px, 170px) minmax(160px, 1fr) minmax(72px, 86px);
  gap: 14px;
  align-items: center;
  border: 1px solid var(--app-border);
  border-radius: 10px;
  background: var(--app-surface-strong);
  padding: 14px;
}

.metric-title {
  min-width: 0;
}

.metric-title strong {
  display: block;
  overflow: hidden;
  color: var(--app-text);
  font-size: 14px;
  font-weight: 700;
  line-height: 1.4;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.metric-title span {
  margin-top: 4px;
}

.metric-control {
  min-width: 0;
}

.metric-track {
  overflow: hidden;
  height: 8px;
  border-radius: 999px;
  background: var(--app-surface-muted);
}

.metric-fill {
  height: 100%;
  border-radius: inherit;
  background: var(--app-primary);
}

.metric-slider {
  --el-slider-main-bg-color: var(--app-primary);
  --el-slider-runway-bg-color: var(--app-surface-muted);
  --el-slider-stop-bg-color: var(--app-surface-muted);
  --el-slider-disabled-color: var(--app-primary);
  height: 20px;
}

.metric-slider :deep(.el-slider__runway) {
  height: 8px;
  margin: 6px 0;
  border-radius: 999px;
}

.metric-slider :deep(.el-slider__bar) {
  height: 8px;
  border-radius: 999px;
}

.metric-slider :deep(.el-slider__button-wrapper) {
  top: -11px;
}

.metric-slider :deep(.el-slider__button) {
  width: 14px;
  height: 14px;
  border: 2px solid var(--app-primary);
  background: var(--app-surface);
  box-shadow: var(--app-shadow-sm);
}

.metric-score {
  color: var(--app-primary-active);
  font-family: var(--claude-serif);
  font-size: 24px;
  font-weight: 500;
  letter-spacing: -0.02em;
  line-height: 1;
  text-align: right;
}

.metric-score small {
  color: var(--app-text-subtle);
  font-family: var(--claude-sans);
  font-size: 12px;
  font-weight: 500;
}

.metric-reason {
  grid-column: 1 / -1;
  margin: -2px 0 0;
  color: var(--app-text-muted);
  font-size: 13px;
  line-height: 1.6;
}

.submitted-panel {
  border-top: 1px solid var(--app-border);
  padding-top: 18px;
}

.submitted-note {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 14px;
}

.submitted-note p {
  margin: 0;
  color: var(--app-text-muted);
  font-size: 13px;
  line-height: 1.65;
}

.readonly-pill {
  display: inline-flex;
  align-items: center;
  min-height: 32px;
  border: 1px solid var(--app-border);
  border-radius: 999px;
  background: var(--app-surface-muted);
  color: var(--app-text);
  padding: 6px 12px;
  font-size: 13px;
  font-weight: 600;
  white-space: nowrap;
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

@media (max-width: 1180px) {
  .score-summary {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 720px) {
  .metric-row {
    grid-template-columns: 1fr;
    gap: 10px;
  }

  .metric-title strong {
    white-space: normal;
  }

  .metric-score {
    text-align: left;
  }

  .score-number {
    font-size: 44px;
  }

  .submitted-note {
    align-items: flex-start;
    flex-direction: column;
  }

  .section-header {
    flex-direction: column;
    align-items: flex-start;
    gap: 4px;
  }
}
</style>
