<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue';
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
    <el-table :data="criteria" v-loading="loading" class="criteria-table">
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

.criteria-actions {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 10px;
}
</style>
