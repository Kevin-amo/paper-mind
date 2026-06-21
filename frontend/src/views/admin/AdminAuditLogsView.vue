<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue';
import AdminShell from '../../components/admin/AdminShell.vue';
import { listAdminAuditLogs, listAdminAuditOperators } from '../../api/adminReviews';
import { getErrorMessage } from '../../api/http';
import { reviewAuditActionLabel, reviewAuditActionOptions } from '../../constants/reviewAudit';
import { ElMessage } from 'element-plus';
import type { ReviewAuditLog, ReviewAuditOperator } from '../../types';

const loading = ref(false);
const auditLogs = ref<ReviewAuditLog[]>([]);
const total = ref(0);
const operatorOptions = ref<ReviewAuditOperator[]>([]);

const filters = reactive({
  operatorUserId: '' as string,
  action: '' as string,
  timeRange: null as [string, string] | null,
});

const pagination = reactive({
  page: 0,
  size: 20,
});

function formatDate(value: string | null | undefined) {
  return value ? new Date(value).toLocaleString('zh-CN') : '-';
}

function operatorLabel(log: ReviewAuditLog) {
  return log.operatorDisplayName || log.operatorUsername || log.operatorUserId || '系统';
}

function operatorOptionLabel(user: ReviewAuditOperator) {
  return user.displayName || user.username;
}

function buildParams(page = pagination.page) {
  return {
    operatorUserId: filters.operatorUserId || undefined,
    action: filters.action || undefined,
    startTime: filters.timeRange?.[0] || undefined,
    endTime: filters.timeRange?.[1] || undefined,
    page,
    size: pagination.size,
  };
}

async function loadAuditLogs(page = pagination.page) {
  loading.value = true;
  try {
    const data = await listAdminAuditLogs(buildParams(page));
    auditLogs.value = data.items;
    total.value = data.total;
    pagination.page = data.page;
    pagination.size = data.size;
  } catch (error) {
    ElMessage.error(getErrorMessage(error));
    auditLogs.value = [];
    total.value = 0;
  } finally {
    loading.value = false;
  }
}

async function loadOperatorOptions() {
  try {
    operatorOptions.value = await listAdminAuditOperators();
  } catch (error) {
    ElMessage.error(getErrorMessage(error));
    operatorOptions.value = [];
  }
}

function handleSearch() {
  loadAuditLogs(0);
}

function handleReset() {
  filters.operatorUserId = '';
  filters.action = '';
  filters.timeRange = null;
  loadAuditLogs(0);
}

function handlePageSizeChange(nextSize: number) {
  pagination.size = nextSize;
  loadAuditLogs(0);
}

function handlePageChange(nextPage: number) {
  loadAuditLogs(nextPage - 1);
}

onMounted(async () => {
  await Promise.all([loadOperatorOptions(), loadAuditLogs(0)]);
});
</script>

<template>
  <AdminShell active="audit-logs" title="审计日志">
    <section class="dashboard-card">
      <div class="section-header">
        <h3>审计日志</h3>
        <p>追溯评审流程中的关键操作记录，支持按操作人、动作类型和时间范围组合查询。</p>
      </div>

      <div class="toolbar">
        <el-select
          v-model="filters.operatorUserId"
          clearable
          filterable
          placeholder="操作人"
          class="filter-select"
        >
          <el-option
            v-for="user in operatorOptions"
            :key="user.userId"
            :label="operatorOptionLabel(user)"
            :value="user.userId"
          />
        </el-select>
        <el-select v-model="filters.action" clearable placeholder="动作类型" class="filter-select">
          <el-option
            v-for="option in reviewAuditActionOptions"
            :key="option.value"
            :label="option.label"
            :value="option.value"
          />
        </el-select>
        <el-date-picker
          v-model="filters.timeRange"
          type="datetimerange"
          range-separator="至"
          start-placeholder="开始时间"
          end-placeholder="结束时间"
          value-format="YYYY-MM-DDTHH:mm:ssZ"
          class="filter-daterange"
        />
        <el-button type="primary" @click="handleSearch">查询</el-button>
        <el-button @click="handleReset">重置</el-button>
      </div>

      <el-table :data="auditLogs" v-loading="loading" empty-text="暂无审计日志" class="audit-table">
        <el-table-column label="动作" min-width="160">
          <template #default="{ row }">
            <el-tag size="small" effect="plain">{{ reviewAuditActionLabel(row.action) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作人" min-width="160" show-overflow-tooltip>
          <template #default="{ row }">{{ operatorLabel(row) }}</template>
        </el-table-column>
        <el-table-column label="操作时间" min-width="180">
          <template #default="{ row }">{{ formatDate(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column label="关联任务" min-width="200" show-overflow-tooltip>
          <template #default="{ row }">{{ row.taskId }}</template>
        </el-table-column>
        <el-table-column label="操作备注" min-width="240" show-overflow-tooltip>
          <template #default="{ row }">{{ row.note || '-' }}</template>
        </el-table-column>
      </el-table>

      <div class="pagination-wrap">
        <el-pagination
          background
          layout="total, sizes, prev, pager, next"
          :total="total"
          :page-size="pagination.size"
          :current-page="pagination.page + 1"
          :page-sizes="[10, 20, 50]"
          @size-change="handlePageSizeChange"
          @current-change="handlePageChange"
        />
      </div>
    </section>
  </AdminShell>
</template>

<style scoped>
.dashboard-card {
  border: 1px solid var(--app-border);
  border-radius: var(--app-radius-md);
  padding: 20px;
  background: var(--app-surface);
}

.section-header {
  margin-bottom: 16px;
  padding-bottom: 14px;
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
  line-height: 1.6;
}

.toolbar {
  display: grid;
  grid-template-columns: minmax(180px, 1fr) minmax(180px, 1fr) minmax(280px, 1.6fr) auto auto;
  gap: 10px;
  margin-bottom: 16px;
}

.filter-select {
  width: 100%;
}

.filter-daterange {
  width: 100%;
}

.toolbar :deep([class~="el-input__wrapper"]),
.toolbar :deep([class~="el-select__wrapper"]) {
  min-height: 36px;
  border-radius: var(--app-radius-sm) !important;
  box-shadow: none !important;
  border: 1px solid var(--app-border);
}

.toolbar :deep([class~="el-input__wrapper"]:hover),
.toolbar :deep([class~="el-select__wrapper"]:hover) {
  border-color: var(--app-border-strong);
}

.toolbar :deep([class~="el-input__wrapper"][class~="is-focus"]),
.toolbar :deep([class~="el-select__wrapper"]:focus-within) {
  border-color: var(--app-border-strong);
}

.audit-table {
  border: 1px solid var(--app-border);
  border-radius: var(--app-radius-sm);
  overflow: hidden;
}

.pagination-wrap {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
  margin-top: 16px;
}

@media (max-width: 980px) {
  .toolbar {
    grid-template-columns: 1fr;
  }
}
</style>
