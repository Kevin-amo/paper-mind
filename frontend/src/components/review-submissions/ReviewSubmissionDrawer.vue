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
      title="评审报告"
      width="640px"
      class="review-report-dialog claude-workspace-dialog"
      destroy-on-close
    >
      <div v-if="selectedSubmission && selectedReport" class="report-detail">
        <div class="report-heading">
          <div>
            <span class="report-label">论文</span>
            <strong>{{ selectedSubmission.title || selectedSubmission.fileName || selectedSubmission.sourceId }}</strong>
          </div>
          <div class="report-score">
            <span>最终分数</span>
            <strong>{{ selectedReport.finalScore ?? '-' }}</strong>
          </div>
        </div>

        <el-descriptions :column="1" border size="small" class="report-summary">
          <el-descriptions-item label="最终建议">
            <span class="recommendation-text">{{ selectedReport.finalRecommendation || '暂无最终建议' }}</span>
          </el-descriptions-item>
          <el-descriptions-item label="确认时间">
            {{ formatDate(selectedReport.confirmedAt) }}
          </el-descriptions-item>
        </el-descriptions>

        <div class="score-section">
          <h3>维度评分</h3>
          <el-table
            :data="selectedReport.criteriaScores"
            size="small"
            class="report-score-table"
            empty-text="暂无维度评分"
          >
            <el-table-column label="维度" min-width="180">
              <template #default="{ row }">
                <div class="criterion-cell">
                  <strong>{{ row.name || row.code }}</strong>
                  <span>{{ row.code }}</span>
                </div>
              </template>
            </el-table-column>
            <el-table-column label="评分" width="120" align="right">
              <template #default="{ row }">
                {{ row.score ?? '-' }}<span v-if="row.maxScore"> / {{ row.maxScore }}</span>
              </template>
            </el-table-column>
          </el-table>
        </div>
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

.report-detail {
  display: grid;
  gap: 16px;
}

.report-heading {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 16px;
  align-items: center;
}

.report-heading > div:first-child {
  display: grid;
  gap: 4px;
  min-width: 0;
}

.report-heading strong {
  overflow-wrap: anywhere;
  color: var(--app-text);
  font-size: 15px;
}

.report-label,
.report-score span {
  color: var(--app-text-muted);
  font-size: 12px;
}

.report-score {
  display: grid;
  min-width: 96px;
  padding: 10px 12px;
  border: 1px solid var(--app-border);
  border-radius: 8px;
  text-align: right;
}

.report-score strong {
  font-size: 24px;
  line-height: 1.2;
}

.report-summary {
  width: 100%;
}

.recommendation-text {
  line-height: 1.6;
  white-space: pre-wrap;
}

.score-section {
  display: grid;
  gap: 10px;
}

.score-section h3 {
  margin: 0;
  color: var(--app-text);
  font-size: 14px;
  font-weight: 700;
}

.report-score-table {
  width: 100%;
}

.criterion-cell {
  display: grid;
  gap: 2px;
  min-width: 0;
}

.criterion-cell strong {
  color: var(--app-text);
  font-size: 13px;
}

.criterion-cell span {
  color: var(--app-text-muted);
  font-size: 12px;
}

@media (max-width: 720px) {
  .submission-toolbar {
    justify-content: flex-start;
    flex-wrap: wrap;
  }

  .report-heading {
    grid-template-columns: 1fr;
  }

  .report-score {
    text-align: left;
  }
}
</style>
