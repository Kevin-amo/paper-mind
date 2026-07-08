<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';
import { ElMessage } from 'element-plus';
import {
  Check,
  DocumentChecked,
  Upload,
  Warning,
} from '@element-plus/icons-vue';
import MainLayout from '../../layouts/MainLayout.vue';
import { getErrorMessage } from '../../api/http';
import { getDocumentUploadJob, listDocuments, uploadDocument } from '../../api/documents';
import {
  createFormatCheck,
  listFormatTemplates,
  updateFormatTemplateSpec,
  uploadFormatTemplate,
} from '../../api/paperFormat';
import type {
  DocumentSummary,
  DocumentJobResponse,
  FormatCheckSummary,
  FormatViolation,
  PaperFormatCheckJob,
  PaperFormatTemplate,
} from '../../types';

const router = useRouter();
const templates = ref<PaperFormatTemplate[]>([]);
const documents = ref<DocumentSummary[]>([]);
const selectedTemplateId = ref('');
const selectedSourceId = ref('');
const checkJob = ref<PaperFormatCheckJob | null>(null);
const loadingTemplates = ref(false);
const loadingDocuments = ref(false);
const uploadingTemplate = ref(false);
const uploadingDocument = ref(false);
const checking = ref(false);
const confirming = ref(false);
const templateName = ref('');
const schoolName = ref('');
const templateFileInputRef = ref<HTMLInputElement | null>(null);
const paperFileInputRef = ref<HTMLInputElement | null>(null);
const DOCUMENT_UPLOAD_TIMEOUT_MS = 120_000;
const DOCUMENT_UPLOAD_POLL_INTERVAL_MS = 1_500;

const selectedTemplate = computed(() => templates.value.find((item) => item.id === selectedTemplateId.value) ?? null);
const selectedDocument = computed(() => documents.value.find((item) => item.sourceId === selectedSourceId.value) ?? null);
const violations = computed<FormatViolation[]>(() => (
  Array.isArray(checkJob.value?.violations) ? checkJob.value?.violations as FormatViolation[] : []
));
const summary = computed<FormatCheckSummary>(() => {
  const raw = checkJob.value?.summary;
  if (raw && typeof raw === 'object') {
    const value = raw as Partial<FormatCheckSummary>;
    return {
      total: Number(value.total ?? violations.value.length),
      errorCount: Number(value.errorCount ?? countBySeverity('ERROR')),
      warningCount: Number(value.warningCount ?? countBySeverity('WARNING')),
      reviewCount: Number(value.reviewCount ?? countBySeverity('REVIEW')),
      passed: value.passed,
    };
  }
  return {
    total: violations.value.length,
    errorCount: countBySeverity('ERROR'),
    warningCount: countBySeverity('WARNING'),
    reviewCount: countBySeverity('REVIEW'),
  };
});
const readyTemplates = computed(() => templates.value.filter((item) => item.status === 'READY' || item.status === 'NEED_CONFIRM'));
const indexedDocuments = computed(() => documents.value.filter((item) => item.status === 'INDEXED' || item.status === 'READY'));

function countBySeverity(severity: string) {
  return violations.value.filter((item) => item.severity === severity).length;
}

function selectTemplateFile() {
  templateFileInputRef.value?.click();
}

function selectPaperFile() {
  paperFileInputRef.value?.click();
}

async function handleTemplateFileChange(event: Event) {
  const file = (event.target as HTMLInputElement).files?.[0];
  (event.target as HTMLInputElement).value = '';
  if (!file) {
    return;
  }
  if (!file.name.toLowerCase().endsWith('.docx')) {
    ElMessage.warning('目前仅支持 .docx 模板');
    return;
  }
  uploadingTemplate.value = true;
  try {
    const template = await uploadFormatTemplate({
      file,
      name: templateName.value.trim() || file.name.replace(/\.docx$/i, ''),
      schoolName: schoolName.value.trim() || undefined,
    });
    await loadTemplates();
    selectedTemplateId.value = template.id;
    ElMessage.success('模板已解析');
  } catch (error) {
    ElMessage.error(getErrorMessage(error));
  } finally {
    uploadingTemplate.value = false;
  }
}

async function handlePaperFileChange(event: Event) {
  const file = (event.target as HTMLInputElement).files?.[0];
  (event.target as HTMLInputElement).value = '';
  if (!file) {
    return;
  }
  if (!file.name.toLowerCase().endsWith('.docx')) {
    ElMessage.warning('目前仅支持 .docx 论文');
    return;
  }
  uploadingDocument.value = true;
  try {
    const result = await uploadDocument({
      file,
      title: file.name.replace(/\.docx$/i, ''),
    });
    ElMessage.success('论文已上传，正在等待入库完成');
    const job = await waitForDocumentIndexed(result.jobId);
    await loadDocuments();
    selectedSourceId.value = job.sourceId || result.sourceId;
    ElMessage.success('论文已入库，可以执行格式校对');
  } catch (error) {
    ElMessage.error(getErrorMessage(error));
  } finally {
    uploadingDocument.value = false;
  }
}

function sleep(ms: number) {
  return new Promise((resolve) => {
    window.setTimeout(resolve, ms);
  });
}

async function waitForDocumentIndexed(jobId: string): Promise<DocumentJobResponse> {
  const deadline = Date.now() + DOCUMENT_UPLOAD_TIMEOUT_MS;
  while (true) {
    const job = await getDocumentUploadJob(jobId);
    if (job.status === 'INDEXED') {
      return job;
    }
    if (job.status === 'FAILED') {
      throw new Error(job.errorMessage || '论文入库失败');
    }
    if (Date.now() >= deadline) {
      throw new Error('论文仍在入库处理中，请稍后从已入库论文列表中选择后再校对');
    }
    await sleep(DOCUMENT_UPLOAD_POLL_INTERVAL_MS);
  }
}

async function loadTemplates() {
  loadingTemplates.value = true;
  try {
    templates.value = await listFormatTemplates();
    if (!selectedTemplateId.value && templates.value.length) {
      selectedTemplateId.value = templates.value[0].id;
    }
  } catch (error) {
    ElMessage.error(getErrorMessage(error));
  } finally {
    loadingTemplates.value = false;
  }
}

async function loadDocuments() {
  loadingDocuments.value = true;
  try {
    const result = await listDocuments({ page: 0, size: 100 });
    documents.value = result.items;
    if (!selectedSourceId.value && result.items.length) {
      selectedSourceId.value = result.items[0].sourceId;
    }
  } catch (error) {
    ElMessage.error(getErrorMessage(error));
  } finally {
    loadingDocuments.value = false;
  }
}

async function confirmTemplateSpec() {
  const template = selectedTemplate.value;
  if (!template) {
    return;
  }
  confirming.value = true;
  try {
    const updated = await updateFormatTemplateSpec(template.id, {
      formatSpec: template.formatSpec,
      confirmed: true,
    });
    templates.value = templates.value.map((item) => (item.id === updated.id ? updated : item));
    ElMessage.success('规则已确认');
  } catch (error) {
    ElMessage.error(getErrorMessage(error));
  } finally {
    confirming.value = false;
  }
}

async function runCheck() {
  if (!selectedTemplateId.value || !selectedSourceId.value) {
    ElMessage.warning('请先选择模板和学生论文');
    return;
  }
  checking.value = true;
  try {
    checkJob.value = await createFormatCheck({
      templateId: selectedTemplateId.value,
      sourceId: selectedSourceId.value,
    });
    ElMessage.success(checkJob.value.status === 'PASSED' ? '格式校对通过' : '格式校对已完成');
  } catch (error) {
    ElMessage.error(getErrorMessage(error));
  } finally {
    checking.value = false;
  }
}

function severityType(severity: string) {
  if (severity === 'ERROR') {
    return 'danger';
  }
  if (severity === 'WARNING') {
    return 'warning';
  }
  return 'info';
}

onMounted(async () => {
  await Promise.all([loadTemplates(), loadDocuments()]);
});
</script>

<template>
  <MainLayout class="format-page">
    <header class="format-topbar">
      <button class="format-brand" type="button" @click="router.push('/user')">PaperMind</button>
      <div class="format-nav-actions">
        <el-button @click="router.push('/user')">返回用户端</el-button>
      </div>
    </header>

    <section class="format-header">
      <div>
        <p class="format-eyebrow">Format Check</p>
        <h1>论文格式校对</h1>
        <p>上传学校模板，选择学生论文，先做确定性格式检查，再进入正式评审或自查。</p>
      </div>
      <div class="format-status-grid">
        <article>
          <span>ERROR</span>
          <strong>{{ summary.errorCount }}</strong>
        </article>
        <article>
          <span>WARNING</span>
          <strong>{{ summary.warningCount }}</strong>
        </article>
        <article>
          <span>REVIEW</span>
          <strong>{{ summary.reviewCount }}</strong>
        </article>
      </div>
    </section>

    <main class="format-layout">
      <section class="format-panel">
        <div class="panel-title">
          <el-icon><Upload /></el-icon>
          <strong>学校模板</strong>
        </div>
        <div class="upload-row">
          <el-input v-model="templateName" placeholder="模板名称" />
          <el-input v-model="schoolName" placeholder="学校名称" />
          <el-button :loading="uploadingTemplate" @click="selectTemplateFile">上传 .docx</el-button>
        </div>
        <el-select v-model="selectedTemplateId" class="full-control" filterable placeholder="选择模板" :loading="loadingTemplates">
          <el-option
            v-for="template in readyTemplates"
            :key="template.id"
            :label="`${template.name} · ${template.status}`"
            :value="template.id"
          />
        </el-select>
        <div v-if="selectedTemplate" class="template-meta">
          <el-tag :type="selectedTemplate.status === 'READY' ? 'success' : 'warning'">{{ selectedTemplate.status }}</el-tag>
          <span>{{ selectedTemplate.schoolName || '未填写学校' }}</span>
          <span>{{ selectedTemplate.fileName }}</span>
          <el-button size="small" :icon="Check" :loading="confirming" @click="confirmTemplateSpec">确认规则</el-button>
        </div>
        <pre class="spec-preview">{{ JSON.stringify(selectedTemplate?.formatSpec ?? {}, null, 2) }}</pre>
      </section>

      <section class="format-panel">
        <div class="panel-title">
          <el-icon><DocumentChecked /></el-icon>
          <strong>学生论文</strong>
        </div>
        <div class="paper-select-row">
          <el-select v-model="selectedSourceId" filterable placeholder="选择已入库论文" :loading="loadingDocuments">
            <el-option
              v-for="document in indexedDocuments"
              :key="document.sourceId"
              :label="`${document.title} · ${document.fileName}`"
              :value="document.sourceId"
            />
          </el-select>
          <el-button :loading="uploadingDocument" @click="selectPaperFile">上传论文</el-button>
          <el-button type="primary" :loading="checking" :disabled="!selectedTemplateId || !selectedSourceId" @click="runCheck">执行校对</el-button>
        </div>
        <div v-if="selectedDocument" class="template-meta">
          <span>{{ selectedDocument.title }}</span>
          <span>{{ selectedDocument.fileName }}</span>
          <span>{{ selectedDocument.status }}</span>
        </div>
        <div class="report-header">
          <div>
            <strong>{{ checkJob ? `检查状态：${checkJob.status}` : '尚未执行格式校对' }}</strong>
            <p>{{ checkJob ? `共发现 ${summary.total} 个格式问题` : '执行后将在这里展示问题列表。' }}</p>
          </div>
          <el-icon class="report-icon"><Warning /></el-icon>
        </div>
        <div class="violation-list">
          <article v-for="violation in violations" :key="`${violation.code}-${violation.location}-${violation.message}`" class="violation-item">
            <div class="violation-line">
              <el-tag :type="severityType(violation.severity)">{{ violation.severity }}</el-tag>
              <strong>{{ violation.code }}</strong>
              <span>{{ violation.location || '整篇文档' }}</span>
            </div>
            <p>{{ violation.message }}</p>
            <small>期望：{{ violation.expected || '-' }} / 实际：{{ violation.actual || '-' }}</small>
            <small v-if="violation.suggestion">建议：{{ violation.suggestion }}</small>
          </article>
          <el-empty v-if="checkJob && !violations.length" description="未发现格式问题" />
        </div>
      </section>
    </main>

    <input ref="templateFileInputRef" type="file" accept=".docx,application/vnd.openxmlformats-officedocument.wordprocessingml.document" hidden @change="handleTemplateFileChange" />
    <input ref="paperFileInputRef" type="file" accept=".docx,application/vnd.openxmlformats-officedocument.wordprocessingml.document" hidden @change="handlePaperFileChange" />
  </MainLayout>
</template>

<style scoped>
.format-page {
  --format-shell-width: min(1280px, calc(100vw - 48px));
  align-items: center;
  background: var(--app-bg);
}

.format-topbar,
.format-header,
.format-layout {
  width: var(--format-shell-width);
}

.format-topbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  min-height: 54px;
}

.format-brand {
  border: 0;
  background: transparent;
  color: var(--app-text);
  cursor: pointer;
  font-size: 16px;
  font-weight: 700;
}

.format-header {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 360px;
  gap: 24px;
  align-items: end;
}

.format-eyebrow {
  margin: 0 0 8px;
  color: var(--app-primary);
  font-size: 12px;
  font-weight: 700;
  text-transform: uppercase;
}

.format-header h1 {
  margin: 0;
  color: var(--app-text);
  font-family: var(--claude-serif);
  font-size: clamp(34px, 4vw, 54px);
  font-weight: 500;
  letter-spacing: 0;
}

.format-header p {
  max-width: 760px;
  margin: 12px 0 0;
  color: var(--app-text-muted);
  line-height: 1.7;
}

.format-status-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  overflow: hidden;
  border: 1px solid var(--app-border);
  border-radius: var(--app-radius-md);
  background: var(--app-border);
  gap: 1px;
}

.format-status-grid article {
  background: var(--app-surface);
  padding: 16px;
}

.format-status-grid span {
  color: var(--app-text-muted);
  font-size: 12px;
  font-weight: 700;
}

.format-status-grid strong {
  display: block;
  margin-top: 8px;
  color: var(--app-text);
  font-size: 30px;
}

.format-layout {
  display: grid;
  grid-template-columns: minmax(0, 0.92fr) minmax(0, 1.08fr);
  gap: 18px;
}

.format-panel {
  min-width: 0;
  border: 1px solid var(--app-border);
  border-radius: var(--app-radius-md);
  background: var(--app-surface);
  padding: 20px;
}

.panel-title,
.template-meta,
.violation-line {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

.panel-title {
  margin-bottom: 16px;
  color: var(--app-text);
}

.upload-row,
.paper-select-row {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(0, 1fr) auto;
  gap: 10px;
}

.paper-select-row {
  grid-template-columns: minmax(0, 1fr) auto auto;
}

.full-control {
  width: 100%;
  margin-top: 12px;
}

.template-meta {
  margin-top: 12px;
  color: var(--app-text-muted);
  font-size: 13px;
}

.spec-preview {
  min-height: 360px;
  max-height: 520px;
  overflow: auto;
  margin: 16px 0 0;
  border: 1px solid var(--app-border);
  border-radius: var(--app-radius-md);
  background: var(--app-surface-soft);
  color: var(--app-text);
  padding: 14px;
  font-size: 12px;
  line-height: 1.55;
}

.report-header {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  margin-top: 18px;
  border: 1px solid var(--app-border);
  border-radius: var(--app-radius-md);
  background: var(--app-surface-soft);
  padding: 16px;
}

.report-header strong {
  color: var(--app-text);
}

.report-header p {
  margin: 6px 0 0;
  color: var(--app-text-muted);
}

.report-icon {
  color: var(--app-primary);
  font-size: 24px;
}

.violation-list {
  display: grid;
  gap: 10px;
  margin-top: 14px;
}

.violation-item {
  border: 1px solid var(--app-border);
  border-radius: var(--app-radius-md);
  background: var(--app-surface);
  padding: 14px;
}

.violation-item p {
  margin: 10px 0 6px;
  color: var(--app-text);
}

.violation-item small {
  display: block;
  color: var(--app-text-muted);
  line-height: 1.7;
}

@media (max-width: 960px) {
  .format-page {
    --format-shell-width: calc(100vw - 24px);
  }

  .format-header,
  .format-layout,
  .upload-row,
  .paper-select-row {
    grid-template-columns: 1fr;
  }
}
</style>
