<script setup lang="ts">
import { Warning } from '@element-plus/icons-vue';

const props = defineProps<{
  modelValue: boolean;
}>();

const emit = defineEmits<{
  'update:modelValue': [value: boolean];
  confirm: [];
}>();

function close() {
  emit('update:modelValue', false);
}

function confirm() {
  close();
  emit('confirm');
}
</script>

<template>
  <el-dialog
    :model-value="props.modelValue"
    title=""
    width="min(420px, 92vw)"
    class="logout-confirm-dialog"
    append-to-body
    align-center
    :close-on-click-modal="false"
    @update:model-value="$emit('update:modelValue', $event)"
  >
    <div class="logout-confirm-body">
      <div class="logout-confirm-icon" aria-hidden="true">
        <el-icon :size="28"><Warning /></el-icon>
      </div>
      <h3 class="logout-confirm-title">确认退出登录</h3>
      <p class="logout-confirm-desc">退出后需要重新登录，才能继续使用系统。</p>
    </div>
    <template #footer>
      <div class="logout-confirm-footer">
        <el-button class="logout-cancel-button" @click="close">取消</el-button>
        <el-button class="logout-confirm-button" type="danger" @click="confirm">确认退出</el-button>
      </div>
    </template>
  </el-dialog>
</template>

<style scoped>
.logout-confirm-dialog :deep(.el-dialog__header) {
  display: none;
}

.logout-confirm-dialog :deep(.el-dialog__body) {
  padding: 32px 28px 24px;
}

.logout-confirm-dialog :deep(.el-dialog__footer) {
  padding: 0 28px 28px;
  border-top: 0;
}

.logout-confirm-body {
  display: flex;
  flex-direction: column;
  align-items: center;
  text-align: center;
  gap: 14px;
}

.logout-confirm-icon {
  display: grid;
  place-items: center;
  width: 64px;
  height: 64px;
  border-radius: var(--app-radius-xl);
  color: var(--app-danger);
  background: var(--app-danger-soft);
  box-shadow: 0 0 0 6px rgba(209, 69, 59, 0.04);
}

.logout-confirm-title {
  margin: 0;
  color: var(--app-text);
  font-family: 'Lexend', 'Inter', -apple-system, BlinkMacSystemFont, 'Segoe UI', 'Microsoft YaHei', sans-serif;
  font-size: 18px;
  font-weight: 700;
  line-height: 1.35;
}

.logout-confirm-desc {
  margin: 0;
  max-width: 320px;
  color: var(--app-text-muted);
  font-size: 14px;
  line-height: 1.6;
}

.logout-confirm-footer {
  display: flex;
  justify-content: center;
  gap: 12px;
  width: 100%;
}

.logout-confirm-footer :deep(.el-button) {
  min-width: 104px;
  height: 40px;
  border-radius: var(--app-radius-md);
  font-size: 14px;
  font-weight: 600;
}

.logout-confirm-footer .logout-cancel-button {
  --el-button-hover-bg-color: var(--app-surface-soft);
  --el-button-hover-border-color: var(--app-border-strong);
  --el-button-hover-text-color: var(--app-text);
}

.logout-confirm-footer .logout-confirm-button {
  --el-button-bg-color: var(--app-danger);
  --el-button-border-color: var(--app-danger);
  --el-button-hover-bg-color: var(--app-danger-hover);
  --el-button-hover-border-color: var(--app-danger-hover);
  --el-button-active-bg-color: #a03028;
  --el-button-active-border-color: #a03028;
  transition: transform var(--app-transition-fast), box-shadow var(--app-transition-fast);
}

.logout-confirm-footer .logout-confirm-button:hover,
.logout-confirm-footer .logout-confirm-button:focus {
  transform: translateY(-1px);
  box-shadow: 0 8px 18px rgba(209, 69, 59, 0.22);
}

.logout-confirm-footer .logout-confirm-button:active {
  transform: translateY(0) scale(0.98);
  box-shadow: 0 4px 10px rgba(209, 69, 59, 0.18);
}

@media (max-width: 640px) {
  .logout-confirm-dialog :deep(.el-dialog__body) {
    padding: 28px 22px 20px;
  }

  .logout-confirm-dialog :deep(.el-dialog__footer) {
    padding: 0 22px 22px;
  }

  .logout-confirm-title {
    font-size: 17px;
  }

  .logout-confirm-desc {
    font-size: 13px;
  }

  .logout-confirm-footer {
    flex-direction: column-reverse;
    gap: 10px;
  }

  .logout-confirm-footer :deep(.el-button) {
    width: 100%;
    min-width: 0;
  }
}
</style>
