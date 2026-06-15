<script setup lang="ts">
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
}>();

function beforeUpload(file: File) {
  emit('upload', file);
  return false;
}
</script>

<template>
  <el-drawer
    :model-value="props.modelValue"
    title="我的投稿"
    size="720px"
    destroy-on-close
    @update:model-value="emit('update:modelValue', $event)"
    @opened="emit('refresh')"
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
      <el-table-column label="错误信息" min-width="180">
        <template #default="{ row }: { row: ReviewSubmission }">
          <span class="error-text">{{ row.errorMessage || '-' }}</span>
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

.error-text {
  color: var(--app-danger);
  font-size: 12px;
}

.drawer-pagination {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}

@media (max-width: 720px) {
  .submission-toolbar {
    justify-content: flex-start;
    flex-wrap: wrap;
  }
}
</style>
