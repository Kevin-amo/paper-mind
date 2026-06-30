<script setup lang="ts">
import { computed, ref } from 'vue';
import { UploadFilled } from '@element-plus/icons-vue';
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
      <el-button :loading="props.loading" @click="emit('refresh')">刷新</el-button>
    </div>

    <el-table
      v-loading="props.loading"
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

.submission-table {
  width: 100%;
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
