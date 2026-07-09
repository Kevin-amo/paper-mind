<script lang="ts">
export default {
  name: 'ChatComposer',
};
</script>

<script setup lang="ts">
import { computed, ref } from 'vue';
import { Promotion, Setting, Plus, Grid } from '@element-plus/icons-vue';

const props = defineProps<{
  loading?: boolean;
}>();

const emit = defineEmits<{
  submit: [payload: { question: string; topK?: number }];
  selectFiles: [];
}>();

const question = ref('');
const topK = ref(3);
const moreMenuVisible = ref(false);
const advancedVisible = ref(false);
const topKOptions = Array.from({ length: 10 }, (_, index) => index + 1);

const canSubmit = computed(() => question.value.trim().length > 0 && !props.loading);
const placeholder = computed(() => '告诉我你的研究目标，按 Enter 发送，Shift + Enter 换行');

function submitQuestion() {
  const content = question.value.trim();
  if (!content || props.loading) {
    return;
  }

  emit('submit', {
    question: content,
    topK: topK.value || undefined,
  });
  question.value = '';
}

function handleKeydown(event: KeyboardEvent) {
  if (event.key !== 'Enter' || event.shiftKey) {
    return;
  }

  event.preventDefault();
  submitQuestion();
}

function fillQuestion(value: string) {
  question.value = value;
  submitQuestion();
}

defineExpose({ fillQuestion });
</script>

<template>
  <section class="chat-composer claude-floating-composer">
    <div class="composer-box">
      <el-input
        v-model="question"
        type="textarea"
        resize="none"
        :autosize="{ minRows: 2, maxRows: 7 }"
        :placeholder="placeholder"
        @keydown="handleKeydown"
      />
      <div class="composer-actions">
        <div class="composer-left-actions">
          <el-button
            class="composer-icon-button"
            text
            :icon="Plus"
            title="上传论文"
            aria-label="上传论文"
            @click="emit('selectFiles')"
          />
          <el-dropdown trigger="click" placement="top" @visible-change="(visible) => moreMenuVisible = visible">
            <el-button
              class="composer-pill"
              text
              title="更多"
              aria-label="更多功能"
            >
              更多
            </el-button>
            <template #dropdown>
              <el-dropdown-menu class="more-dropdown-menu">
                <el-dropdown-item @click="advancedVisible = true; moreMenuVisible = false">
                  <span class="menu-item-label">引用召回数量</span>
                  <span class="menu-item-value">{{ topK }}</span>
                </el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
        <el-button
          class="send-button"
          type="primary"
          circle
          :loading="props.loading"
          :disabled="!canSubmit"
          :icon="Promotion"
          aria-label="发送"
          @click="submitQuestion"
        />
      </div>
    </div>

    <p class="composer-hint">PaperMind 可能会犯错误。请务必仔细检查生成的研究声明。</p>

    <el-dialog v-model="advancedVisible" title="高级设置" width="420px" class="advanced-dialog claude-workspace-dialog" append-to-body align-center>
      <div class="advanced-setting-card">
        <div class="advanced-setting-header">
          <div>
            <strong>引用召回数量 Top K</strong>
            <span>控制回答时参考的论文片段数量，默认 3 条。</span>
          </div>
        </div>
        <div class="topk-options" role="radiogroup" aria-label="引用召回数量 Top K">
          <button
            v-for="option in topKOptions"
            :key="option"
            type="button"
            class="topk-option"
            :class="{ active: topK === option }"
            :aria-checked="topK === option"
            role="radio"
            @click="topK = option"
          >
            {{ option }}
          </button>
        </div>
      </div>
      <template #footer>
        <el-button @click="advancedVisible = false">完成</el-button>
      </template>
    </el-dialog>
  </section>
</template>

<style scoped>
.chat-composer {
  position: sticky;
  bottom: 0;
  z-index: 5;
  padding: 0 0 14px;
  background: linear-gradient(180deg, rgba(250, 249, 245, 0), rgba(250, 249, 245, 0.86) 42%, var(--app-bg));
}

.composer-box {
  display: grid;
  gap: 10px;
  width: min(940px, calc(100% - 48px));
  margin: 0 auto;
  padding: 20px 20px 14px;
  border: 1px solid var(--app-border);
  border-radius: 24px;
  background: var(--app-surface);
  box-shadow: 0 14px 36px rgba(20, 20, 19, 0.08);
}

.composer-box :deep(.el-textarea__inner) {
  min-height: 56px !important;
  border: 0 !important;
  background: transparent !important;
  box-shadow: none !important;
  color: var(--app-text);
  font-size: 16px;
  line-height: 1.7;
  padding: 2px 6px;
  outline: none !important;
  resize: none;
}

.composer-box :deep(.el-textarea__inner:focus),
.composer-box :deep(.el-textarea__inner:hover) {
  border: 0 !important;
  box-shadow: none !important;
}

.composer-box :deep(.el-textarea__inner::placeholder) {
  color: var(--app-text-subtle);
}

.composer-actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.composer-left-actions {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
}

.composer-icon-button {
  width: 34px;
  height: 34px;
  border-radius: 50%;
  color: var(--app-text);
}

.composer-icon-button:hover {
  border-color: var(--app-border-strong);
  background: var(--app-surface-muted);
  color: var(--app-primary);
}

.composer-pill {
  height: 34px;
  padding: 0 12px;
  border: 1px solid var(--app-border);
  border-radius: 999px;
  background: var(--app-surface-soft);
  color: var(--app-text-muted);
  font-size: 13px;
  font-weight: 500;
}

.composer-pill:hover {
  border-color: var(--app-border-strong);
  background: var(--app-surface-muted);
  color: var(--app-primary);
}

.send-button {
  width: 40px;
  height: 40px;
  border: 0;
  background: var(--app-primary);
  box-shadow: none;
}

.send-button:hover,
.send-button:focus {
  background: var(--app-primary-hover);
  box-shadow: none;
}

.send-button.is-disabled,
.send-button.is-disabled:hover {
  background: var(--app-border);
  color: var(--app-text-muted);
  box-shadow: none;
}

.composer-hint {
  width: min(940px, calc(100% - 48px));
  margin: 8px auto 0;
  color: var(--app-text-muted);
  font-size: 12px;
  text-align: center;
}

.advanced-setting-card {
  display: grid;
  gap: 18px;
  padding: 16px;
  border: 1px solid var(--app-border);
  border-radius: var(--app-radius-lg);
  background: var(--app-surface-soft);
}

.advanced-setting-header strong,
.advanced-setting-header span {
  display: block;
}

.advanced-setting-header strong {
  color: var(--app-text);
  font-size: 15px;
}

.advanced-setting-header span {
  margin-top: 5px;
  color: var(--app-text-muted);
  font-size: 12px;
  line-height: 1.6;
}

.topk-options {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: 10px;
}

.topk-option {
  height: 38px;
  border: 1px solid var(--app-border);
  border-radius: 999px;
  background: var(--app-surface);
  color: var(--app-text);
  cursor: pointer;
  font: inherit;
  font-weight: 500;
}

.topk-option:hover {
  border-color: var(--app-border-strong);
  color: var(--app-primary);
}

.topk-option.active {
  border-color: rgba(204, 120, 92, 0.32);
  background: var(--app-primary-soft);
  color: var(--app-primary);
}

.menu-item-label {
  font-size: 13px;
  color: var(--app-text);
}

.menu-item-value {
  margin-left: auto;
  font-size: 12px;
  color: var(--app-text-muted);
  background: var(--app-surface-soft);
  padding: 2px 8px;
  border-radius: 999px;
}

:global(.advanced-dialog .el-dialog) {
  border-radius: 26px;
}

:global(.advanced-dialog .el-dialog__title) {
  color: var(--app-text);
  font-weight: 700;
}

@media (max-width: 640px) {
  .chat-composer {
    padding-bottom: 12px;
  }

  .composer-box,
  .composer-hint {
    width: calc(100% - 24px);
  }

  .composer-box {
    border-radius: var(--app-radius-lg);
  }

  .composer-actions {
    align-items: flex-end;
  }

  .topk-options {
    grid-template-columns: repeat(5, minmax(42px, 1fr));
  }

  :global(.advanced-dialog) {
    width: calc(100% - 28px) !important;
  }
}
</style>

<style>
.more-dropdown-menu {
  min-width: 140px;
  padding: 8px 0;
  border-radius: 12px;
  border: 1px solid var(--app-border);
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.1);
}

.more-dropdown-menu .el-dropdown-menu__item {
  padding: 10px 16px;
  font-size: 13px;
  display: flex;
  align-items: center;
  gap: 8px;
}

.more-dropdown-menu .el-dropdown-menu__item:hover {
  background: var(--app-surface-soft);
}
</style>
