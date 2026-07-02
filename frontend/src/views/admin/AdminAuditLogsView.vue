<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue';
import { Refresh } from '@element-plus/icons-vue';
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
  size: 10,
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
    <section class="paper-mind-workspace-card audit-dashboard-panel">
      <div class="section-header">
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
        <el-button @click="loadAuditLogs(0)">
          <el-icon :class="{ 'is-rotating': loading }"><Refresh /></el-icon>
          刷新
        </el-button>
        <el-button @click="handleReset">重置</el-button>
      </div>

      <div class="table-wrapper" :class="{ 'is-loading': loading }">
        <!-- Skeleton placeholder during loading -->
        <div v-if="loading && auditLogs.length === 0" class="skeleton-container">
          <div v-for="i in 5" :key="i" class="skeleton-row">
            <div class="skeleton-cell skeleton-action"></div>
            <div class="skeleton-cell skeleton-operator"></div>
            <div class="skeleton-cell skeleton-time"></div>
            <div class="skeleton-cell skeleton-task"></div>
            <div class="skeleton-cell skeleton-note"></div>
          </div>
        </div>

        <!-- Actual table with fade transition -->
        <transition name="fade-content">
          <el-table
            v-show="!loading || auditLogs.length > 0"
            :data="auditLogs"
            empty-text="暂无审计日志"
            class="audit-table"
          >
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
        </transition>
      </div>

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
.audit-dashboard-panel {
  border: 1px solid var(--app-border);
  border-radius: var(--app-radius-lg);
  padding: 24px;
  background: var(--claude-canvas);
}

.section-header {
  margin-bottom: 16px;
  padding-bottom: 14px;
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
  line-height: 1.6;
}

.toolbar {
  display: grid;
  grid-template-columns: minmax(180px, 1fr) minmax(180px, 1fr) minmax(280px, 1.6fr) auto auto auto;
  gap: 10px;
  margin-bottom: 16px;
}

.toolbar .el-icon.is-rotating {
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
  border-color: var(--app-primary);
}

.audit-table {
  border: 1px solid var(--app-border);
  border-radius: var(--app-radius-lg);
  overflow: hidden;
}

.table-wrapper {
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
  grid-template-columns: 160px 160px 180px 200px 240px;
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

.skeleton-action {
  width: 80px;
  height: 24px;
}

.skeleton-operator {
  width: 120px;
}

.skeleton-time {
  width: 140px;
}

.skeleton-task {
  width: 160px;
}

.skeleton-note {
  width: 200px;
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
