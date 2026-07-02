<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue';
import { Refresh } from '@element-plus/icons-vue';
import { ElMessage } from 'element-plus';
import { listReviewCriteria, batchUpdateCriterionWeights } from '../../../api/reviews';
import { getErrorMessage } from '../../../api/http';
import type { ReviewCriterion } from '../../../types';

const loading = ref(false);
const saving = ref(false);
const criteria = ref<ReviewCriterion[]>([]);
const editingWeights = reactive<Record<string, number>>({});

const weightSum = computed(() => {
  return Object.values(editingWeights).reduce((sum, w) => sum + w, 0);
});

const weightSumValid = computed(() => weightSum.value === 100);

async function loadCriteria() {
  loading.value = true;
  try {
    criteria.value = await listReviewCriteria(true);
    // Initialize editing weights from loaded criteria
    for (const key of Object.keys(editingWeights)) {
      delete editingWeights[key];
    }
    for (const c of criteria.value) {
      editingWeights[c.id] = c.weight;
    }
  } catch (error) {
    ElMessage.error(getErrorMessage(error));
  } finally {
    loading.value = false;
  }
}

async function saveWeights() {
  if (!weightSumValid.value) {
    ElMessage.error(`权重总和必须等于100，当前总和为${weightSum.value}`);
    return;
  }
  saving.value = true;
  try {
    const weights = criteria.value.map((c) => ({
      id: c.id,
      weight: editingWeights[c.id] ?? c.weight,
    }));
    await batchUpdateCriterionWeights(weights);
    ElMessage.success('权重配置已保存');
    await loadCriteria();
  } catch (error) {
    ElMessage.error(getErrorMessage(error));
  } finally {
    saving.value = false;
  }
}

function resetWeights() {
  for (const c of criteria.value) {
    editingWeights[c.id] = c.weight;
  }
}

onMounted(loadCriteria);
</script>

<template>
  <div class="criteria-panel">
    <div class="criteria-header">
      <h3>评审指标权重配置</h3>
      <div class="header-actions">
        <el-button @click="loadCriteria">
          <el-icon :class="{ 'is-rotating': loading }"><Refresh /></el-icon>
          刷新
        </el-button>
        <div class="weight-summary" :class="{ 'weight-invalid': !weightSumValid }">
          <span>权重总和：</span>
          <strong>{{ weightSum }}</strong>
          <span>/ 100</span>
          <el-tag v-if="!weightSumValid" type="danger" size="small" class="weight-warning">
            权重总和不等于100
          </el-tag>
          <el-tag v-else type="success" size="small" class="weight-warning">
            有效
          </el-tag>
        </div>
      </div>
    </div>
    <div class="table-wrapper" :class="{ 'is-loading': loading }">
      <!-- Skeleton placeholder during loading -->
      <div v-if="loading && criteria.length === 0" class="skeleton-container">
        <div v-for="i in 5" :key="i" class="skeleton-row">
          <div class="skeleton-cell skeleton-code"></div>
          <div class="skeleton-cell skeleton-name"></div>
          <div class="skeleton-cell skeleton-weight"></div>
          <div class="skeleton-cell skeleton-enabled"></div>
          <div class="skeleton-cell skeleton-desc"></div>
        </div>
      </div>

      <!-- Actual table with fade transition -->
      <transition name="fade-content">
        <el-table
          v-show="!loading || criteria.length > 0"
          :data="criteria"
          class="criteria-table"
        >
          <el-table-column prop="code" label="Code" width="140" />
          <el-table-column prop="name" label="名称" min-width="140" show-overflow-tooltip />
          <el-table-column label="权重" width="160">
            <template #default="{ row }">
              <el-input-number
                v-model="editingWeights[row.id]"
                :min="1"
                :max="100"
                :step="5"
                controls-position="right"
                size="small"
              />
            </template>
          </el-table-column>
          <el-table-column label="启用" width="80">
            <template #default="{ row }">
              <el-tag :type="row.enabled ? 'success' : 'info'" size="small">{{ row.enabled ? '启用' : '停用' }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="description" label="描述" min-width="220" show-overflow-tooltip>
            <template #default="{ row }">{{ row.description || '-' }}</template>
          </el-table-column>
        </el-table>
      </transition>
    </div>
    <div class="criteria-actions">
      <el-button @click="resetWeights">重置</el-button>
      <el-button type="primary" :loading="saving" :disabled="!weightSumValid" @click="saveWeights">
        保存权重配置
      </el-button>
    </div>
  </div>
</template>

<style scoped>
.criteria-panel {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.criteria-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 12px;
}

.header-actions {
  display: flex;
  align-items: center;
  gap: 12px;
}

.header-actions .el-icon.is-rotating {
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

.criteria-header h3 {
  margin: 0;
  color: var(--app-text);
  font-size: 16px;
  font-weight: 700;
}

.weight-summary {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 14px;
  color: var(--app-text-muted);
  padding: 6px 12px;
  border-radius: 8px;
  background: var(--app-surface);
  border: 1px solid var(--app-border);
}

.weight-summary strong {
  font-size: 18px;
  color: var(--app-primary);
}

.weight-invalid strong {
  color: var(--el-color-danger);
}

.weight-warning {
  margin-left: 4px;
}

.criteria-table {
  overflow: hidden;
  border: 1px solid var(--app-border);
  border-radius: 18px;
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
  grid-template-columns: 140px 140px 160px 80px 220px;
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

.skeleton-code {
  width: 100px;
}

.skeleton-name {
  width: 120px;
}

.skeleton-weight {
  width: 120px;
}

.skeleton-enabled {
  width: 60px;
  height: 24px;
}

.skeleton-desc {
  width: 180px;
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

.criteria-actions {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 10px;
}
</style>
