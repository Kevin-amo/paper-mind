<script setup lang="ts">
import { computed, ref } from 'vue';
import { UploadFilled, Refresh } from '@element-plus/icons-vue';
import StatusTag from '../common/StatusTag.vue';
import { formatDate } from '../../utils/format';
import type { ReviewSubmission } from '../../types';

const props = defineProps<{
  modelValue: boolean;
  submissions: ReviewSubmission[];
  loading: boolean;
  uploading: boolean;
  page: number;
  size: number;
  total: number;
}>();

const emit = defineEmits<{
  'update:modelValue': [value: boolean];
  upload: [file: File];
  refresh: [];
  pageChange: [page: number];
  stopPolling: [];
}>();

const selectedSubmission = ref<ReviewSubmission | null>(null);
const reportVisible = ref(false);
const selectedReport = computed(() => selectedSubmission.value?.reviewReport ?? null);
const scorePercent = (score: number | null | undefined, maxScore: number | null | undefined) => {
  const numericScore = Number(score);
  const numericMax = Number(maxScore || 100);
  if (!Number.isFinite(numericScore) || !Number.isFinite(numericMax) || numericMax <= 0) {
    return 0;
  }
  return Math.min(100, Math.max(0, (numericScore / numericMax) * 100));
};

function beforeUpload(file: File) {
  emit('upload', file);
  return false;
}

function openReport(row: ReviewSubmission) {
  if (!row.reviewReport) {
    return;
  }
  selectedSubmission.value = row;
  reportVisible.value = true;
}
</script>

<template>
  <el-drawer
    :model-value="props.modelValue"
    title="我的投稿"
    size="720px"
    destroy-on-close
    class="review-submission-drawer claude-workspace-drawer"
    @update:model-value="emit('update:modelValue', $event)"
    @opened="emit('refresh')"
    @close="emit('stopPolling')"
  >
    <div class="submission-toolbar">
      <el-upload
        :show-file-list="false"
        :before-upload="beforeUpload"
        accept=".pdf,.doc,.docx,application/pdf,application/msword,application/vnd.openxmlformats-officedocument.wordprocessingml.document"
      >
        <el-button type="primary" :icon="UploadFilled" :loading="props.uploading">上传投稿</el-button>
      </el-upload>
      <el-button @click="emit('refresh')">
        <el-icon :class="{ 'is-rotating': props.loading }"><Refresh /></el-icon>
        刷新
      </el-button>
    </div>

    <div class="submission-table-wrapper" :class="{ 'is-loading': props.loading }">
      <!-- Skeleton placeholder during loading -->
      <div v-if="props.loading && props.submissions.length === 0" class="skeleton-container">
        <div v-for="i in 5" :key="i" class="skeleton-row">
          <div class="skeleton-cell skeleton-title"></div>
          <div class="skeleton-cell skeleton-status"></div>
          <div class="skeleton-cell skeleton-status"></div>
          <div class="skeleton-cell skeleton-date"></div>
          <div class="skeleton-cell skeleton-action"></div>
        </div>
      </div>

      <!-- Actual table with fade transition -->
      <transition name="fade-content">
        <el-table
          v-show="!props.loading || props.submissions.length > 0"
          :data="props.submissions"
          row-key="sourceId"
          class="submission-table"
          empty-text="暂无评审投稿"
        >
          <el-table-column label="论文" min-width="220">
            <template #default="{ row }: { row: ReviewSubmission }">
              <div class="paper-cell">
                <strong>{{ row.title || row.fileName || row.sourceId }}</strong>
                <span>{{ row.fileName || row.sourceId }}</span>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="文档状态" width="120">
            <template #default="{ row }: { row: ReviewSubmission }">
              <StatusTag :status="row.documentStatus" />
            </template>
          </el-table-column>
          <el-table-column label="评审状态" width="150">
            <template #default="{ row }: { row: ReviewSubmission }">
              <StatusTag v-if="row.reviewStatus" :status="row.reviewStatus" />
              <span v-else class="muted-text">待生成任务</span>
            </template>
          </el-table-column>
          <el-table-column label="提交时间" width="170">
            <template #default="{ row }: { row: ReviewSubmission }">
              {{ formatDate(row.submittedAt) }}
            </template>
          </el-table-column>
          <el-table-column label="操作" width="110" fixed="right">
            <template #default="{ row }: { row: ReviewSubmission }">
              <el-button
                v-if="row.reviewReport"
                type="primary"
                size="small"
                class="report-link"
                @click="openReport(row)"
              >
                查看报告
              </el-button>
              <span v-else class="muted-text">-</span>
            </template>
          </el-table-column>
        </el-table>
      </transition>
    </div>

    <div class="drawer-pagination">
      <el-pagination
        background
        layout="prev, pager, next"
        :page-size="props.size"
        :current-page="props.page + 1"
        :total="props.total"
        @current-change="emit('pageChange', $event - 1)"
      />
    </div>

    <el-dialog
      v-model="reportVisible"
      width="820px"
      class="review-report-dialog paper-report-dialog"
      destroy-on-close
    >
      <template #header>
        <header class="report-dialog-header">
          <p class="report-eyebrow">Confirmed Review Report</p>
          <h2 id="submission-report-title">评审报告</h2>
          <div class="report-header-meta" aria-label="报告元信息">
            <span class="report-meta-pill">确认时间 <strong>{{ formatDate(selectedReport?.confirmedAt) }}</strong></span>
          </div>
        </header>
      </template>

      <div v-if="selectedSubmission && selectedReport" class="report-detail">
        <section class="report-summary-grid">
          <article class="report-paper-card" aria-label="论文">
            <span class="report-label">论文</span>
            <strong>{{ selectedSubmission.title || selectedSubmission.fileName || selectedSubmission.sourceId }}</strong>
          </article>

          <aside class="report-score-card" :aria-label="`最终分数 ${selectedReport.finalScore ?? '-'} 分`">
            <div
              class="report-score-ring"
              :style="{ '--score-degrees': `${scorePercent(selectedReport.finalScore, 100) * 3.6}deg` }"
            >
              <strong>{{ selectedReport.finalScore ?? '-' }}</strong>
              <span>/ 100</span>
            </div>
            <p>最终分数</p>
          </aside>
        </section>

        <article class="report-recommendation-card" aria-label="最终建议">
          <span class="report-label">最终建议</span>
          <div class="report-recommendation-text">
            {{ selectedReport.finalRecommendation || '暂无最终建议' }}
          </div>
        </article>

        <section class="report-score-section" aria-label="维度评分">
          <div class="report-section-title">
            <h3>维度评分</h3>
            <span>{{ selectedReport.criteriaScores.length }} 个评分维度</span>
          </div>

          <div class="report-score-table">
            <div class="report-score-row" aria-hidden="true">
              <span>维度</span>
              <span>得分占比</span>
              <span class="report-score-value">评分</span>
            </div>

            <div v-if="!selectedReport.criteriaScores.length" class="report-empty-row">
              暂无维度评分
            </div>

            <div
              v-for="item in selectedReport.criteriaScores"
              :key="item.code"
              class="report-score-row"
            >
              <div class="report-criterion">
                <strong>{{ item.name || item.code }}</strong>
                <span>{{ item.code }}</span>
              </div>
              <div class="score-bar-track">
                <div
                  class="score-bar-fill"
                  :style="{ width: `${scorePercent(item.score, item.maxScore)}%` }"
                ></div>
              </div>
              <div class="report-score-value">
                {{ item.score ?? '-' }} <span>/ {{ item.maxScore ?? 100 }}</span>
              </div>
            </div>
          </div>
        </section>
      </div>
    </el-dialog>
  </el-drawer>
</template>

<style scoped>
.submission-toolbar {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 10px;
  margin-bottom: 16px;
}

.submission-toolbar .el-icon.is-rotating {
  animation: rotate-icon 0.8s linear infinite;
}

@keyframes rotate-icon {
  from {
    transform: rotate(0deg);
  }
  to {
    transform: rotate(360deg);
  }
}

.submission-table {
  width: 100%;
}

.submission-table-wrapper {
  position: relative;
  min-height: 200px;
}

/* Skeleton Loading Animation */
.skeleton-container {
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding: 16px 0;
}

.skeleton-row {
  display: grid;
  grid-template-columns: minmax(220px, 2fr) 120px 150px 170px 110px;
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

.skeleton-title {
  height: 36px;
}

.skeleton-status {
  height: 24px;
  width: 80px;
}

.skeleton-date {
  width: 140px;
}

.skeleton-action {
  width: 80px;
}

@keyframes shimmer {
  0% {
    background-position: 200% 0;
  }
  100% {
    background-position: -200% 0;
  }
}

/* Fade Transition for Content */
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

.paper-cell {
  display: grid;
  gap: 4px;
  min-width: 0;
}

.paper-cell strong {
  overflow: hidden;
  color: var(--app-text);
  font-size: 14px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.paper-cell span,
.muted-text {
  color: var(--app-text-muted);
  font-size: 12px;
}

.drawer-pagination {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}

.report-link {
  --el-button-bg-color: var(--app-primary);
  --el-button-border-color: var(--app-primary);
  --el-button-text-color: var(--app-text-on-primary);
  --el-button-hover-bg-color: var(--app-primary-hover);
  --el-button-hover-border-color: var(--app-primary-hover);
  --el-button-hover-text-color: var(--app-text-on-primary);
  --el-button-active-bg-color: var(--app-primary-active);
  --el-button-active-border-color: var(--app-primary-active);
  --el-button-active-text-color: var(--app-text-on-primary);
  cursor: pointer;
  font-weight: 600;
}

.review-submission-drawer :deep(.report-link:focus-visible) {
  box-shadow: var(--app-shadow-focus);
}

:global([class~="paper-report-dialog"]) {
  --report-surface: #faf9f5;
  --report-card: #fffaf3;
  --report-soft: #f4eee5;
  --report-muted: #ece2d5;
  --report-text: #171614;
  --report-body: #45413b;
  --report-subtle: #706b63;
  --report-line: #e5ddd2;
  --report-line-strong: #d6cabd;
  --report-primary: #c86f52;
  --report-primary-strong: #9f4f38;
  --report-primary-soft: rgba(200, 111, 82, 0.13);
  --report-accent-soft: rgba(185, 132, 44, 0.14);
}

:global([class~="paper-report-dialog"][class~="el-dialog"]) {
  overflow: hidden;
  border: 1px solid rgba(24, 25, 27, 0.08);
  border-radius: 16px;
  background: var(--report-surface);
  box-shadow: 0 24px 70px rgba(23, 22, 20, 0.22);
}

:global([class~="paper-report-dialog"] [class~="el-dialog__header"]) {
  margin: 0;
  padding: 0;
  border-bottom: 1px solid var(--report-line);
}

:global([class~="paper-report-dialog"] [class~="el-dialog__headerbtn"]) {
  top: 18px;
  right: 18px;
  width: 40px;
  height: 40px;
  border-radius: 8px;
}

:global([class~="paper-report-dialog"] [class~="el-dialog__body"]) {
  padding: 30px 48px 42px;
  color: var(--report-text);
}

.report-dialog-header {
  padding: 34px 48px 26px;
  color: var(--report-text);
}

.report-eyebrow {
  margin: 0 0 10px;
  color: var(--report-primary);
  font-size: 12px;
  font-weight: 800;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.report-dialog-header h2 {
  margin: 0;
  color: var(--report-text);
  font-family: Georgia, "Times New Roman", "Microsoft YaHei", serif;
  font-size: clamp(30px, 5vw, 42px);
  font-weight: 500;
  line-height: 1.12;
}

.report-header-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-top: 18px;
}

.report-meta-pill {
  display: inline-flex;
  align-items: center;
  min-height: 34px;
  padding: 0 12px;
  border: 1px solid var(--report-line);
  border-radius: 999px;
  background: rgba(255, 250, 243, 0.84);
  color: var(--report-subtle);
  font-size: 13px;
  font-weight: 700;
}

.report-meta-pill strong {
  margin-left: 8px;
  color: var(--report-body);
  font-weight: 760;
}

.report-detail {
  display: grid;
  gap: 24px;
}

.report-summary-grid {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 140px;
  gap: 20px;
  align-items: stretch;
}

.report-paper-card,
.report-recommendation-card,
.report-score-card,
.report-score-table,
.report-note-card {
  border: 1px solid var(--report-line);
  border-radius: 12px;
  background: var(--report-card);
}

.report-paper-card {
  display: grid;
  align-content: center;
  gap: 8px;
  padding: 22px;
  background:
    linear-gradient(90deg, var(--report-primary-soft), rgba(255, 250, 243, 0) 64%),
    var(--report-card);
}

.report-paper-card strong {
  color: var(--report-text);
  font-size: clamp(18px, 3vw, 24px);
  line-height: 1.35;
  overflow-wrap: anywhere;
}

.report-label {
  color: var(--report-subtle);
  font-size: 13px;
  font-weight: 700;
}

.report-score-card {
  display: grid;
  align-content: center;
  justify-items: center;
  gap: 12px;
  padding: 18px;
  text-align: center;
}

.report-score-card p {
  margin: 0;
  color: var(--report-subtle);
  font-size: 13px;
  font-weight: 700;
}

.report-score-ring {
  position: relative;
  display: grid;
  place-items: center;
  width: 104px;
  height: 104px;
  border-radius: 50%;
  background:
    radial-gradient(circle at center, var(--report-card) 0 56%, transparent 57%),
    conic-gradient(var(--report-primary) 0 var(--score-degrees), var(--report-muted) var(--score-degrees) 360deg);
}

.report-score-ring strong {
  color: var(--report-text);
  font-size: 34px;
  font-weight: 820;
  line-height: 1;
}

.report-score-ring span {
  position: absolute;
  bottom: 23px;
  color: var(--report-subtle);
  font-size: 12px;
}

.report-recommendation-card {
  display: grid;
  gap: 12px;
  padding: 18px 20px;
}

.report-recommendation-text {
  max-height: 170px;
  overflow: auto;
  padding-right: 8px;
  color: var(--report-body);
  font-size: 16px;
  font-weight: 650;
  line-height: 1.65;
  overflow-wrap: anywhere;
  white-space: pre-wrap;
}

.report-recommendation-text::-webkit-scrollbar {
  width: 8px;
}

.report-recommendation-text::-webkit-scrollbar-track {
  border-radius: 999px;
  background: var(--report-soft);
}

.report-recommendation-text::-webkit-scrollbar-thumb {
  border-radius: 999px;
  background: var(--report-line-strong);
}

.report-score-section {
  display: grid;
  gap: 10px;
}

.report-section-title {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.report-section-title h3 {
  margin: 0;
  color: var(--report-text);
  font-size: 18px;
  font-weight: 780;
}

.report-section-title span {
  color: var(--report-subtle);
  font-size: 13px;
}

.report-score-table {
  overflow: hidden;
}

.report-score-row {
  display: grid;
  grid-template-columns: minmax(150px, 1fr) minmax(160px, 2fr) 96px;
  gap: 18px;
  align-items: center;
  min-height: 72px;
  padding: 15px 18px;
  border-top: 1px solid var(--report-line);
}

.report-score-row:first-child {
  min-height: 42px;
  border-top: 0;
  background: var(--report-muted);
  color: var(--report-subtle);
  font-size: 13px;
  font-weight: 760;
}

.report-criterion {
  display: grid;
  gap: 4px;
  min-width: 0;
}

.report-criterion strong {
  color: var(--report-text);
  font-size: 15px;
  line-height: 1.25;
}

.report-criterion span {
  color: var(--report-subtle);
  font-size: 12px;
  letter-spacing: 0.03em;
}

.score-bar-track {
  height: 10px;
  overflow: hidden;
  border-radius: 999px;
  background: var(--report-muted);
}

.score-bar-fill {
  height: 100%;
  border-radius: inherit;
  background: linear-gradient(90deg, var(--report-primary), #e0a082);
}

.report-score-value {
  color: var(--report-text);
  font-size: 15px;
  font-weight: 760;
  text-align: right;
  white-space: nowrap;
}

.report-score-value span {
  color: var(--report-subtle);
  font-weight: 500;
}

.report-empty-row {
  padding: 22px 18px;
  border-top: 1px solid var(--report-line);
  color: var(--report-subtle);
  font-size: 14px;
}

.report-note-card {
  display: grid;
  gap: 10px;
  padding: 18px;
  background: var(--report-accent-soft);
}

.report-note-card p {
  margin: 0;
  color: var(--report-body);
  font-size: 14px;
  line-height: 1.75;
}

@media (max-width: 720px) {
  .submission-toolbar {
    justify-content: flex-start;
    flex-wrap: wrap;
  }

  :global([class~="paper-report-dialog"][class~="el-dialog"]) {
    width: 100%;
    min-height: 100vh;
    margin: 0;
    border: 0;
    border-radius: 0;
  }

  :global([class~="paper-report-dialog"] [class~="el-dialog__body"]),
  .report-dialog-header {
    padding-right: 22px;
    padding-left: 22px;
  }

  .report-summary-grid,
  .report-score-row {
    grid-template-columns: 1fr;
  }

  .report-score-card {
    justify-items: start;
    text-align: left;
  }

  .report-score-value {
    text-align: left;
  }
}
</style>
