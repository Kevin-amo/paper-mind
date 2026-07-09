<script lang="ts">
export default { name: 'AigcRewriteDialog' };
</script>

<script setup lang="ts">
import { computed, ref } from 'vue';
import { ElMessage } from 'element-plus';
import { CopyDocument } from '@element-plus/icons-vue';
import { getErrorMessage } from '../../api/http';
import { rewriteParagraph } from '../../api/aigcRewrite';
import type { AigcRewriteResponse, AigcRewriteStrength } from '../../types';

const visible = defineModel<boolean>({ default: false });

const paragraph = ref('');
const discipline = ref('通用中文学术论文');
const rewriteStrength = ref<AigcRewriteStrength>('standard');
const keepTermsText = ref('');
const extraRequirements = ref('');
const loading = ref(false);
const result = ref<AigcRewriteResponse | null>(null);

const keepTerms = computed(() =>
  keepTermsText.value
    .split(/[,\n，]/)
    .map((s) => s.trim())
    .filter((s) => s.length > 0),
);

const paragraphLength = computed(() => paragraph.value.trim().length);
const canSubmit = computed(() =>
  paragraphLength.value >= 20 && paragraphLength.value <= 5000 && !loading.value,
);

const strengthOptions = [
  { value: 'light', label: '轻微' },
  { value: 'standard', label: '标准' },
  { value: 'strong', label: '较强' },
] as const;

const scoreItems = computed(() => {
  if (!result.value?.qualityScore) return [];
  const s = result.value.qualityScore;
  return [
    { label: '直接性', value: s.directness },
    { label: '节奏', value: s.rhythm },
    { label: '学术语气', value: s.academicTone },
    { label: '信息密度', value: s.informationDensity },
    { label: '语义保真', value: s.meaningPreservation },
    { label: '综合', value: s.overall },
  ];
});

async function handleRewrite() {
  if (!canSubmit.value) return;
  loading.value = true;
  result.value = null;
  try {
    result.value = await rewriteParagraph({
      paragraph: paragraph.value.trim(),
      discipline: discipline.value.trim() || undefined,
      rewriteStrength: rewriteStrength.value,
      keepTerms: keepTerms.value.length > 0 ? keepTerms.value : undefined,
      extraRequirements: extraRequirements.value.trim() || undefined,
    });
  } catch (error) {
    ElMessage.error(getErrorMessage(error));
  } finally {
    loading.value = false;
  }
}

async function handleCopy() {
  if (!result.value?.rewrittenText) return;
  try {
    await navigator.clipboard.writeText(result.value.rewrittenText);
    ElMessage.success('已复制改写结果');
  } catch {
    ElMessage.error('复制失败，请手动选择文本复制');
  }
}

function handleReset() {
  paragraph.value = '';
  discipline.value = '通用中文学术论文';
  rewriteStrength.value = 'standard';
  keepTermsText.value = '';
  extraRequirements.value = '';
  result.value = null;
}
</script>

<template>
  <el-dialog
    v-model="visible"
    title="论文润色"
    width="min(780px, 92vw)"
    class="aigc-rewrite-dialog claude-workspace-dialog"
    append-to-body
    align-center
    :close-on-click-modal="false"
    @closed="handleReset"
  >
    <div class="aigc-rewrite-body">
      <!-- 输入区 -->
      <section class="rewrite-input-section">
        <div class="form-field">
          <label class="form-label">
            <span>待润色段落</span>
            <span class="form-hint">{{ paragraphLength }} / 5000</span>
          </label>
          <el-input
            v-model="paragraph"
            type="textarea"
            :rows="6"
            placeholder="粘贴需要润色的论文段落（20-5000 字符）"
            maxlength="5000"
            show-word-limit
          />
        </div>

        <div class="form-row">
          <div class="form-field">
            <label class="form-label">学科领域</label>
            <el-input
              v-model="discipline"
              placeholder="通用中文学术论文"
              maxlength="100"
            />
          </div>
          <div class="form-field">
            <label class="form-label">改写强度</label>
            <el-select v-model="rewriteStrength" style="width: 100%">
              <el-option
                v-for="opt in strengthOptions"
                :key="opt.value"
                :value="opt.value"
                :label="opt.label"
              />
            </el-select>
          </div>
        </div>

        <div class="form-field">
          <label class="form-label">必须保留的术语</label>
          <el-input
            v-model="keepTermsText"
            type="textarea"
            :rows="2"
            placeholder="用逗号或换行分隔，如：深度学习, Transformer"
          />
        </div>

        <div class="form-field">
          <label class="form-label">额外要求</label>
          <el-input
            v-model="extraRequirements"
            type="textarea"
            :rows="2"
            placeholder="可选，如：保持第三人称、不使用反问句"
            maxlength="1000"
          />
        </div>

        <div class="form-actions">
          <el-button
            type="primary"
            :loading="loading"
            :disabled="!canSubmit"
            @click="handleRewrite"
          >
            {{ loading ? '润色中…' : '开始润色' }}
          </el-button>
          <el-button v-if="result" plain @click="handleReset">清空</el-button>
        </div>
      </section>

      <!-- 结果区 -->
      <section v-if="result" class="rewrite-result-section">
        <div class="result-header">
          <el-button :icon="CopyDocument" plain size="small" @click="handleCopy">
            复制改写结果
          </el-button>
        </div>

        <div class="result-block">
          <h4 class="result-block-title">改写后文本</h4>
          <div class="rewritten-text">{{ result.rewrittenText }}</div>
        </div>

        <div v-if="result.riskPatterns.length > 0" class="result-block">
          <h4 class="result-block-title">风险模式</h4>
          <div class="risk-pattern-list">
            <div
              v-for="(pattern, idx) in result.riskPatterns"
              :key="idx"
              class="risk-pattern-item"
            >
              <el-tag size="small" type="warning">{{ pattern.type }}</el-tag>
              <span class="risk-evidence">原文：{{ pattern.evidence }}</span>
              <span class="risk-suggestion">建议：{{ pattern.suggestion }}</span>
            </div>
          </div>
        </div>

        <div v-if="result.changeNotes.length > 0" class="result-block">
          <h4 class="result-block-title">修改说明</h4>
          <ul class="result-list">
            <li v-for="(note, idx) in result.changeNotes" :key="idx">{{ note }}</li>
          </ul>
        </div>

        <div v-if="result.warnings.length > 0" class="result-block">
          <h4 class="result-block-title">注意事项</h4>
          <ul class="result-list warning-list">
            <li v-for="(warning, idx) in result.warnings" :key="idx">{{ warning }}</li>
          </ul>
        </div>

        <div class="result-block">
          <h4 class="result-block-title">质量评分</h4>
          <div class="score-grid">
            <div v-for="item in scoreItems" :key="item.label" class="score-item">
              <span class="score-label">{{ item.label }}</span>
              <span class="score-value" :class="{ 'score-highlight': item.label === '综合' }">
                {{ item.value }} / 10
              </span>
            </div>
          </div>
        </div>
      </section>
    </div>
  </el-dialog>
</template>

<style scoped>
.aigc-rewrite-body {
  display: flex;
  flex-direction: column;
  gap: 20px;
  max-height: 70vh;
  overflow-y: auto;
  padding-right: 4px;
}

.form-field {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.form-row {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 14px;
}

@media (max-width: 600px) {
  .form-row {
    grid-template-columns: 1fr;
  }
}

.form-label {
  display: flex;
  align-items: center;
  justify-content: space-between;
  color: var(--app-text);
  font-size: 13px;
  font-weight: 600;
}

.form-hint {
  color: var(--app-text-muted);
  font-size: 12px;
  font-weight: 400;
}

.form-actions {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
  margin-top: 16px;
}

.result-header {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 12px;
}

.result-block {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.result-block-title {
  margin: 0;
  color: var(--app-text);
  font-size: 14px;
  font-weight: 600;
}

.rewritten-text {
  padding: 14px;
  border: 1px solid var(--app-border);
  border-radius: var(--app-radius-md, 8px);
  background: var(--app-surface-soft, rgba(0, 0, 0, 0.02));
  color: var(--app-text);
  font-size: 14px;
  line-height: 1.8;
  white-space: pre-wrap;
  word-break: break-word;
}

.risk-pattern-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.risk-pattern-item {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 6px;
  padding: 8px 10px;
  border: 1px solid var(--app-border);
  border-radius: var(--app-radius-sm, 6px);
  background: var(--app-surface);
  font-size: 13px;
}

.risk-evidence,
.risk-suggestion {
  color: var(--app-text-muted);
}

.result-list {
  margin: 0;
  padding-left: 18px;
  color: var(--app-text);
  font-size: 13px;
  line-height: 1.8;
}

.warning-list {
  color: var(--app-danger, #d14538);
}

.score-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 8px;
}

@media (max-width: 500px) {
  .score-grid {
    grid-template-columns: repeat(2, 1fr);
  }
}

.score-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 12px;
  border: 1px solid var(--app-border);
  border-radius: var(--app-radius-sm, 6px);
  background: var(--app-surface);
}

.score-label {
  color: var(--app-text-muted);
  font-size: 13px;
}

.score-value {
  color: var(--app-text);
  font-size: 14px;
  font-weight: 600;
}

.score-highlight {
  color: var(--app-primary, #cc785c);
}
</style>
