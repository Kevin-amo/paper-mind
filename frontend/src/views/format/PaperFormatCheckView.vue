<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue';
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

interface RuleItem {
  label: string;
  value: string;
}

interface RuleGroup {
  title: string;
  items: RuleItem[];
}

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
const templateName = ref('');
const schoolName = ref('');
const templateFileInputRef = ref<HTMLInputElement | null>(null);
const paperFileInputRef = ref<HTMLInputElement | null>(null);
const violationPagination = reactive({
  page: 1,
  size: 8,
});
const DOCUMENT_UPLOAD_TIMEOUT_MS = 120_000;
const DOCUMENT_UPLOAD_POLL_INTERVAL_MS = 1_500;

const selectedTemplate = computed(() => templates.value.find((item) => item.id === selectedTemplateId.value) ?? null);
const selectedDocument = computed(() => documents.value.find((item) => item.sourceId === selectedSourceId.value) ?? null);
const selectedFormatSpec = computed<Record<string, unknown>>(() => readObject(selectedTemplate.value?.formatSpec) ?? {});
const readableRuleGroups = computed<RuleGroup[]>(() => {
  const spec = selectedFormatSpec.value;
  return [
    buildPageRuleGroup(spec),
    buildBodyRuleGroup(spec),
    buildHeadingRuleGroup(spec),
    buildHeaderFooterRuleGroup(spec),
  ].filter((group): group is RuleGroup => Boolean(group && group.items.length));
});
const violations = computed<FormatViolation[]>(() => (
  Array.isArray(checkJob.value?.violations) ? checkJob.value?.violations as FormatViolation[] : []
));
const paginatedViolations = computed<FormatViolation[]>(() => {
  const start = (violationPagination.page - 1) * violationPagination.size;
  return violations.value.slice(start, start + violationPagination.size);
});
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

function handleViolationPageChange(page: number) {
  violationPagination.page = page;
}

function readObject(value: unknown): Record<string, unknown> | null {
  if (value && typeof value === 'object' && !Array.isArray(value)) {
    return value as Record<string, unknown>;
  }
  return null;
}

function readNumber(value: unknown): number | null {
  if (typeof value === 'number' && Number.isFinite(value)) {
    return value;
  }
  if (typeof value === 'string' && value.trim()) {
    const parsed = Number(value.trim());
    return Number.isFinite(parsed) ? parsed : null;
  }
  return null;
}

function readString(value: unknown): string | null {
  if (typeof value !== 'string') {
    return null;
  }
  const trimmed = value.trim();
  return trimmed || null;
}

function hasExtractedValue(value: unknown) {
  return value !== null && value !== undefined && !(typeof value === 'string' && !value.trim());
}

function formatNumber(value: number) {
  return value.toFixed(2).replace(/\.?0+$/, '');
}

function formatPt(value: unknown): string | null {
  const number = readNumber(value);
  return number === null ? null : `${formatNumber(number)}pt`;
}

function formatMm(value: unknown): string | null {
  const number = readNumber(value);
  return number === null ? null : `${formatNumber(number)}mm`;
}

function formatBoolean(value: unknown): string | null {
  if (typeof value !== 'boolean') {
    return null;
  }
  return value ? '是' : '否';
}

function formatAlignment(value: unknown): string | null {
  const alignment = readString(value);
  if (!alignment) {
    return null;
  }
  const alignmentMap: Record<string, string> = {
    CENTER: '居中',
    LEFT: '左对齐',
    RIGHT: '右对齐',
    BOTH: '两端对齐',
    JUSTIFY: '两端对齐',
  };
  return alignmentMap[alignment.toUpperCase()] ?? alignment;
}

function formatLineSpacing(value: unknown): string | null {
  const rule = readString(value);
  if (!rule) {
    return null;
  }
  const lineSpacingMap: Record<string, string> = {
    FIXED: '固定值',
    AUTO: '倍数',
    AT_LEAST: '最小值',
  };
  return lineSpacingMap[rule.toUpperCase()] ?? rule;
}

function fieldValue(primary: Record<string, unknown> | null, fallback: Record<string, unknown> | null, key: string): unknown {
  const primaryValue = primary?.[key];
  return hasExtractedValue(primaryValue) ? primaryValue : fallback?.[key];
}

function readFirstString(primary: Record<string, unknown> | null, fallback: Record<string, unknown> | null, keys: string[]) {
  for (const key of keys) {
    const value = readString(fieldValue(primary, fallback, key));
    if (value) {
      return value;
    }
  }
  return null;
}

function addRule(items: RuleItem[], label: string, value: string | null) {
  if (value) {
    items.push({ label, value });
  }
}

function buildPageRuleGroup(spec: Record<string, unknown>): RuleGroup | null {
  const pageRule = readObject(spec.pageRule);
  if (!pageRule) {
    return null;
  }
  const items: RuleItem[] = [];
  addRule(items, '纸张尺寸', formatPaperSize(pageRule));
  addRule(items, '页边距', formatMargins(pageRule));
  addRule(items, '内外侧边距', formatInsideOutsideMargins(pageRule));
  addRule(items, '装订线', formatMm(pageRule.gutterMm));
  addRule(items, '页眉页脚距离', formatHeaderFooterDistance(pageRule));
  addRule(items, '对称页边距', formatBoolean(pageRule.mirrorMargins));
  addRule(items, '双面打印', formatBoolean(pageRule.duplexPrint));
  return { title: '页面设置', items };
}

function buildBodyRuleGroup(spec: Record<string, unknown>): RuleGroup | null {
  const roleRules = readObject(spec.roleRules);
  const bodyRule = readObject(spec.bodyRule);
  const bodyFallback = readObject(roleRules?.body);
  if (!bodyRule && !bodyFallback) {
    return null;
  }
  const items: RuleItem[] = [];
  addRule(items, '中文字体', readString(fieldValue(bodyRule, bodyFallback, 'eastAsiaFont')));
  addRule(items, '西文字体', readFirstString(bodyRule, bodyFallback, ['asciiFont', 'latinFont', 'hAnsiFont']));
  addRule(items, '字号', formatPt(fieldValue(bodyRule, bodyFallback, 'fontSizePt')));
  addRule(items, '行距', formatParagraphLineSpacing(bodyRule, bodyFallback));
  addRule(items, '段前段后', formatParagraphSpacing(bodyRule, bodyFallback));
  addRule(items, '首行缩进', formatMm(fieldValue(bodyRule, bodyFallback, 'firstLineIndentMm')));
  addRule(items, '对齐方式', formatAlignment(fieldValue(bodyRule, bodyFallback, 'alignment')));
  return { title: '正文格式', items };
}

function buildHeadingRuleGroup(spec: Record<string, unknown>): RuleGroup | null {
  const roleRules = readObject(spec.roleRules);
  const items: RuleItem[] = [];
  [
    { level: 1, label: '一级标题' },
    { level: 2, label: '二级标题' },
    { level: 3, label: '三级标题' },
  ].forEach((heading) => {
    const headingRule = readHeadingRule(spec.headingRules, heading.level);
    const headingFallback = readObject(roleRules?.[`heading${heading.level}`]);
    addRule(items, heading.label, formatParagraphSummary(headingRule, headingFallback, true));
  });
  return { title: '标题格式', items };
}

function buildHeaderFooterRuleGroup(spec: Record<string, unknown>): RuleGroup | null {
  const headerFooterRule = readObject(spec.headerFooterRule);
  if (!headerFooterRule) {
    return null;
  }
  const items: RuleItem[] = [];
  addRule(items, '页眉文字', readString(headerFooterRule.headerText));
  addRule(items, '页眉字体', readString(headerFooterRule.headerFontEastAsia));
  addRule(items, '页眉字号', formatPt(headerFooterRule.headerFontSizePt));
  addRule(items, '页眉居中', formatBoolean(headerFooterRule.headerCentered));
  addRule(items, '页脚包含页码', formatBoolean(headerFooterRule.footerPageNumber));
  addRule(items, '页脚居中', formatBoolean(headerFooterRule.footerCentered));
  return { title: '页眉页脚', items };
}

function formatPaperSize(pageRule: Record<string, unknown>) {
  const width = formatMm(pageRule.pageWidthMm);
  const height = formatMm(pageRule.pageHeightMm);
  if (width && height) {
    return `${width} × ${height}`;
  }
  if (width) {
    return `宽 ${width}`;
  }
  if (height) {
    return `高 ${height}`;
  }
  return null;
}

function formatMargins(pageRule: Record<string, unknown>) {
  return joinParts([
    labelValue('上', formatMm(pageRule.marginTopMm)),
    labelValue('右', formatMm(pageRule.marginRightMm)),
    labelValue('下', formatMm(pageRule.marginBottomMm)),
    labelValue('左', formatMm(pageRule.marginLeftMm)),
  ]);
}

function formatInsideOutsideMargins(pageRule: Record<string, unknown>) {
  return joinParts([
    labelValue('内侧', formatMm(pageRule.insideMarginMm)),
    labelValue('外侧', formatMm(pageRule.outsideMarginMm)),
  ]);
}

function formatHeaderFooterDistance(pageRule: Record<string, unknown>) {
  return joinParts([
    labelValue('页眉', formatMm(pageRule.headerDistanceMm)),
    labelValue('页脚', formatMm(pageRule.footerDistanceMm)),
  ]);
}

function formatParagraphLineSpacing(primary: Record<string, unknown> | null, fallback: Record<string, unknown> | null) {
  const rule = formatLineSpacing(fieldValue(primary, fallback, 'lineSpacingRule'));
  const spacingPt = formatPt(fieldValue(primary, fallback, 'lineSpacingPt'));
  const multiple = readNumber(fieldValue(primary, fallback, 'lineSpacingMultiple'));
  const multipleText = multiple === null ? null : `${formatNumber(multiple)}倍`;
  return joinParts([rule, spacingPt ?? multipleText], ' ');
}

function formatParagraphSpacing(primary: Record<string, unknown> | null, fallback: Record<string, unknown> | null) {
  return joinParts([
    labelValue('段前', formatPt(fieldValue(primary, fallback, 'spaceBeforePt'))),
    labelValue('段后', formatPt(fieldValue(primary, fallback, 'spaceAfterPt'))),
  ]);
}

function formatParagraphSummary(primary: Record<string, unknown> | null, fallback: Record<string, unknown> | null, includeBold: boolean) {
  if (!primary && !fallback) {
    return null;
  }
  return joinParts([
    labelValue('中文字体', readString(fieldValue(primary, fallback, 'eastAsiaFont'))),
    labelValue('西文字体', readFirstString(primary, fallback, ['asciiFont', 'latinFont', 'hAnsiFont'])),
    labelValue('字号', formatPt(fieldValue(primary, fallback, 'fontSizePt'))),
    includeBold ? labelValue('加粗', formatBoolean(fieldValue(primary, fallback, 'bold'))) : null,
    labelValue('行距', formatParagraphLineSpacing(primary, fallback)),
    labelValue('对齐', formatAlignment(fieldValue(primary, fallback, 'alignment'))),
  ]);
}

function readHeadingRule(value: unknown, level: number): Record<string, unknown> | null {
  if (Array.isArray(value)) {
    const matchingRule = value
      .map((item) => readObject(item))
      .find((item) => readNumber(item?.level) === level);
    return matchingRule ?? readObject(value[level - 1]);
  }
  const headingRules = readObject(value);
  if (!headingRules) {
    return null;
  }
  const directRule = readObject(headingRules[String(level)]) ?? readObject(headingRules[String(level - 1)]);
  if (directRule) {
    return directRule;
  }
  return Object.values(headingRules)
    .map((item) => readObject(item))
    .find((item) => readNumber(item?.level) === level) ?? null;
}

function labelValue(label: string, value: string | null) {
  return value ? `${label} ${value}` : null;
}

function joinParts(parts: Array<string | null>, separator = ' / ') {
  const presentParts = parts.filter((part): part is string => Boolean(part));
  return presentParts.length ? presentParts.join(separator) : null;
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
    violationPagination.page = 1;
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
  <MainLayout class="format-page paper-mind-review-shell">
    <header class="format-top-nav">
      <button class="format-brand" type="button" @click="router.push('/user')">
        <span class="format-brand-mark" aria-hidden="true"><span></span></span>
        <span>Paper Mind</span>
      </button>
      <nav class="format-nav-links" aria-label="主导航">
        <button class="format-nav-link" type="button" @click="router.push('/user')">用户端</button>
        <button class="format-nav-link active" type="button">格式校对</button>
      </nav>
      <div class="format-nav-actions">
        <el-button text @click="router.push('/user')">返回</el-button>
      </div>
    </header>

    <main class="format-workspace">
      <section class="format-hero">
        <div class="format-hero-copy">
          <p class="format-eyebrow">Format Check</p>
          <h1>论文格式校对</h1>
          <p>上传学校模板，选择学生论文，先做确定性格式检查，再进入正式评审或自查。</p>
        </div>
        <aside class="format-hero-summary" aria-label="校对概览">
          <div class="summary-card summary-error">
            <span>错误</span>
            <strong>{{ summary.errorCount }}</strong>
            <small>ERROR</small>
          </div>
          <div class="summary-card summary-warning">
            <span>警告</span>
            <strong>{{ summary.warningCount }}</strong>
            <small>WARNING</small>
          </div>
        </aside>
      </section>

      <section class="format-layout">
        <!-- 左栏：模板管理 -->
        <section class="format-panel format-panel-template">
          <div class="panel-header">
            <div class="panel-icon template-icon">
              <el-icon><Upload /></el-icon>
            </div>
            <div>
              <h3>学校模板</h3>
              <p>上传 .docx 模板文件，解析后自动提取格式规则</p>
            </div>
          </div>

          <div class="panel-body">
            <div class="upload-zone">
              <el-input v-model="templateName" placeholder="模板名称" class="format-input" />
              <el-input v-model="schoolName" placeholder="学校名称（选填）" class="format-input" />
              <el-button :loading="uploadingTemplate" @click="selectTemplateFile">
                <el-icon style="margin-right: 6px"><Upload /></el-icon>
                上传模板
              </el-button>
            </div>

            <div class="select-block">
              <label class="select-label">已解析模板</label>
              <el-select v-model="selectedTemplateId" class="full-control" filterable placeholder="选择模板" :loading="loadingTemplates">
                <el-option
                  v-for="template in readyTemplates"
                  :key="template.id"
                  :label="`${template.name} · ${template.status}`"
                  :value="template.id"
                />
              </el-select>
            </div>

            <div v-if="selectedTemplate" class="template-info-card">
              <div class="template-info-top">
                <el-tag :type="selectedTemplate.status === 'READY' ? 'success' : 'warning'" round>{{ selectedTemplate.status }}</el-tag>
              </div>
              <div class="template-info-meta">
                <div class="meta-row">
                  <span class="meta-label">学校</span>
                  <span class="meta-value">{{ selectedTemplate.schoolName || '未填写' }}</span>
                </div>
                <div class="meta-row">
                  <span class="meta-label">文件</span>
                  <span class="meta-value">{{ selectedTemplate.fileName }}</span>
                </div>
              </div>
            </div>

            <div class="spec-block">
              <div class="spec-header">
                <span>模板规则摘要</span>
                <small>SUMMARY</small>
              </div>
              <div class="rule-summary-panel">
                <div v-if="readableRuleGroups.length" class="rule-group-list">
                  <section v-for="group in readableRuleGroups" :key="group.title" class="rule-group-card">
                    <h4>{{ group.title }}</h4>
                    <dl class="rule-list">
                      <div v-for="item in group.items" :key="`${group.title}-${item.label}`" class="rule-row">
                        <dt>{{ item.label }}</dt>
                        <dd>{{ item.value }}</dd>
                      </div>
                    </dl>
                  </section>
                </div>
                <div v-else class="rule-empty-state">暂无可展示的模板规则</div>

                <details class="raw-json-details">
                  <summary>高级信息 / 原始规则 JSON</summary>
                  <pre class="spec-preview">{{ JSON.stringify(selectedFormatSpec, null, 2) }}</pre>
                </details>
              </div>
            </div>
          </div>
        </section>

        <!-- 右栏：论文选择与校对结果 -->
        <section class="format-panel format-panel-paper">
          <div class="panel-header">
            <div class="panel-icon paper-icon">
              <el-icon><DocumentChecked /></el-icon>
            </div>
            <div>
              <h3>学生论文与校对结果</h3>
              <p>选择已入库论文后执行格式校对，问题列表将在此展示</p>
            </div>
          </div>

          <div class="panel-body">
            <div class="paper-action-row">
              <el-select v-model="selectedSourceId" filterable placeholder="选择已入库论文" :loading="loadingDocuments" class="paper-select">
                <el-option
                  v-for="document in indexedDocuments"
                  :key="document.sourceId"
                  :label="`${document.title} · ${document.fileName}`"
                  :value="document.sourceId"
                />
              </el-select>
              <el-button :loading="uploadingDocument" @click="selectPaperFile">
                <el-icon style="margin-right: 6px"><Upload /></el-icon>
                上传论文
              </el-button>
              <el-button type="primary" :loading="checking" :disabled="!selectedTemplateId || !selectedSourceId" @click="runCheck" class="run-check-btn">
                执行校对
              </el-button>
            </div>

            <div v-if="selectedDocument" class="paper-info-card">
              <div class="meta-row">
                <span class="meta-label">标题</span>
                <span class="meta-value">{{ selectedDocument.title }}</span>
              </div>
              <div class="meta-row">
                <span class="meta-label">文件</span>
                <span class="meta-value">{{ selectedDocument.fileName }}</span>
              </div>
              <div class="meta-row">
                <span class="meta-label">状态</span>
                <span class="meta-value">{{ selectedDocument.status }}</span>
              </div>
            </div>

            <!-- 校对状态横幅 -->
            <div class="report-banner" :class="{ 'report-banner-passed': checkJob?.status === 'PASSED', 'report-banner-issues': checkJob && checkJob.status !== 'PASSED' }">
              <div class="report-banner-content">
                <div class="report-banner-icon">
                  <el-icon v-if="checkJob?.status === 'PASSED'"><Check /></el-icon>
                  <el-icon v-else><Warning /></el-icon>
                </div>
                <div>
                  <strong>{{ checkJob ? `检查状态：${checkJob.status}` : '尚未执行格式校对' }}</strong>
                  <p>{{ checkJob ? `共发现 ${summary.total} 个格式问题` : '选择模板和论文后点击「执行校对」' }}</p>
                </div>
              </div>
            </div>

            <!-- 违规列表 -->
            <div class="violation-list">
              <article
                v-for="violation in paginatedViolations"
                :key="`${violation.code}-${violation.location}-${violation.message}`"
                class="violation-item"
                :class="`violation-${violation.severity.toLowerCase()}`"
              >
                <div class="violation-header">
                  <span class="violation-severity-dot" :class="`dot-${violation.severity.toLowerCase()}`"></span>
                  <el-tag :type="severityType(violation.severity)" size="small" round>{{ violation.severity }}</el-tag>
                  <strong class="violation-code">{{ violation.code }}</strong>
                  <span class="violation-location">{{ violation.location || '整篇文档' }}</span>
                </div>
                <p class="violation-message">{{ violation.message }}</p>
                <div class="violation-detail">
                  <div class="detail-row">
                    <span class="detail-label">期望</span>
                    <span class="detail-value">{{ violation.expected || '-' }}</span>
                  </div>
                  <div class="detail-row">
                    <span class="detail-label">实际</span>
                    <span class="detail-value">{{ violation.actual || '-' }}</span>
                  </div>
                </div>
                <div v-if="violation.suggestion" class="violation-suggestion">
                  <el-icon><Warning /></el-icon>
                  <span>{{ violation.suggestion }}</span>
                </div>
              </article>

              <div v-if="violations.length > violationPagination.size" class="violation-pagination">
                <span>共 {{ violations.length }} 个问题</span>
                <el-pagination
                  background
                  layout="prev, pager, next"
                  :total="violations.length"
                  :page-size="violationPagination.size"
                  :current-page="violationPagination.page"
                  @current-change="handleViolationPageChange"
                />
              </div>

              <!-- 通过状态 -->
              <div v-if="checkJob && !violations.length" class="format-passed-state">
                <div class="passed-icon"><el-icon><Check /></el-icon></div>
                <strong>格式校对通过</strong>
                <p>未发现任何格式问题，论文格式符合模板规则。</p>
              </div>

              <!-- 空状态 -->
              <div v-if="!checkJob" class="format-idle-state">
                <div class="idle-icon" aria-hidden="true"></div>
                <strong>等待执行格式校对</strong>
                <p>选择学校模板和学生论文后，点击「执行校对」按钮开始检查。</p>
              </div>
            </div>
          </div>
        </section>
      </section>
    </main>

    <input ref="templateFileInputRef" type="file" accept=".docx,application/vnd.openxmlformats-officedocument.wordprocessingml.document" hidden @change="handleTemplateFileChange" />
    <input ref="paperFileInputRef" type="file" accept=".docx,application/vnd.openxmlformats-officedocument.wordprocessingml.document" hidden @change="handlePaperFileChange" />
  </MainLayout>
</template>

<style scoped>
.format-page {
  --format-shell-width: min(1360px, calc(100vw - 72px));
  gap: 0;
  min-height: 100vh;
  padding: 0 36px 80px;
  background: var(--claude-canvas);
}

.format-top-nav,
.format-workspace {
  width: var(--format-shell-width);
  margin-inline: auto;
}

/* ===== Top Navigation ===== */
.format-top-nav {
  display: grid;
  grid-template-columns: auto 1fr auto;
  align-items: center;
  gap: 24px;
  min-height: 64px;
  border-bottom: 1px solid var(--app-border-light);
}

.format-brand,
.format-nav-links {
  display: flex;
  align-items: center;
}

.format-brand {
  gap: 10px;
  border: 0;
  background: transparent;
  color: var(--app-text);
  padding: 0;
  font-size: 15px;
  font-weight: 600;
  cursor: pointer;
}

.format-brand-mark {
  position: relative;
  width: 18px;
  height: 18px;
  flex: 0 0 18px;
}

.format-brand-mark::before,
.format-brand-mark::after,
.format-brand-mark span::before,
.format-brand-mark span::after {
  position: absolute;
  top: 50%;
  left: 50%;
  width: 18px;
  height: 2px;
  border-radius: 999px;
  background: var(--app-text);
  content: "";
  transform: translate(-50%, -50%);
}

.format-brand-mark::after {
  transform: translate(-50%, -50%) rotate(90deg);
}

.format-brand-mark span::before {
  transform: translate(-50%, -50%) rotate(45deg);
}

.format-brand-mark span::after {
  transform: translate(-50%, -50%) rotate(-45deg);
}

.format-nav-links {
  gap: 4px;
}

.format-nav-link {
  min-height: 36px;
  border: 0;
  border-radius: var(--app-radius-md);
  background: transparent;
  color: var(--app-text-muted);
  padding: 8px 14px;
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
  transition: background-color 0.2s ease, color 0.2s ease;
}

.format-nav-link.active,
.format-nav-link:hover {
  background: var(--app-surface-soft);
  color: var(--app-text);
}

.format-nav-actions {
  display: flex;
  align-items: center;
  justify-content: flex-end;
}

/* ===== Hero Section ===== */
.format-hero {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 420px;
  gap: 28px;
  align-items: end;
  padding: 38px 0 24px;
}

.format-eyebrow {
  margin: 0 0 8px;
  color: var(--app-primary);
  font-size: 12px;
  font-weight: 500;
  letter-spacing: 0;
  line-height: 1.4;
  text-transform: uppercase;
}

.format-hero-copy h1 {
  margin: 0;
  color: var(--app-text);
  font-family: var(--claude-serif);
  font-size: clamp(38px, 4vw, 58px);
  font-weight: 500;
  letter-spacing: 0;
  line-height: 1.08;
}

.format-hero-copy p:last-child {
  max-width: 680px;
  margin: 14px 0 0;
  color: var(--app-text-muted);
  font-size: 15px;
  line-height: 1.7;
}

.format-hero-summary {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.summary-card {
  display: flex;
  flex-direction: column;
  gap: 6px;
  border: 1px solid var(--app-border);
  border-radius: var(--app-radius-lg);
  background: var(--app-surface-soft);
  padding: 16px 14px;
  text-align: left;
}

.summary-card span {
  color: var(--app-text-muted);
  font-size: 12px;
  font-weight: 500;
}

.summary-card strong {
  color: var(--app-text);
  font-family: var(--claude-serif);
  font-size: 36px;
  font-weight: 500;
  line-height: 1;
}

.summary-card small {
  color: var(--app-text-subtle);
  font-size: 10px;
  font-weight: 600;
  letter-spacing: 1px;
}

.summary-error {
  border-color: rgba(198, 69, 69, 0.22);
  background: var(--app-danger-soft);
}
.summary-error strong { color: var(--app-danger); }

.summary-warning {
  border-color: rgba(212, 160, 23, 0.22);
  background: var(--app-warning-soft);
}
.summary-warning strong { color: var(--app-warning-hover); }

/* ===== Layout ===== */
.format-layout {
  display: grid;
  grid-template-columns: minmax(0, 0.85fr) minmax(0, 1.15fr);
  gap: 20px;
  align-items: start;
}

.format-panel {
  min-width: 0;
  border: 1px solid var(--app-border);
  border-radius: var(--app-radius-lg);
  background: var(--app-surface);
  overflow: hidden;
}

/* ===== Panel Header ===== */
.panel-header {
  display: flex;
  align-items: flex-start;
  gap: 14px;
  padding: 20px 22px 16px;
  border-bottom: 1px solid var(--app-border-light);
  background: var(--app-surface-strong);
}

.panel-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 40px;
  height: 40px;
  flex: 0 0 40px;
  border-radius: var(--app-radius-md);
  font-size: 18px;
}

.template-icon {
  background: var(--app-primary-soft);
  color: var(--app-primary);
}

.paper-icon {
  background: var(--app-accent-soft);
  color: var(--app-accent-hover);
}

.panel-header h3 {
  margin: 0;
  color: var(--app-text);
  font-size: 18px;
  font-weight: 600;
}

.panel-header p {
  margin: 4px 0 0;
  color: var(--app-text-muted);
  font-size: 13px;
  line-height: 1.5;
}

/* ===== Panel Body ===== */
.panel-body {
  display: grid;
  gap: 16px;
  padding: 20px 22px 22px;
}

/* ===== Upload Zone ===== */
.upload-zone {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(0, 1fr) auto;
  gap: 10px;
}

/* ===== Select Block ===== */
.select-block {
  display: grid;
  gap: 8px;
}

.select-label {
  color: var(--app-text-muted);
  font-size: 12px;
  font-weight: 600;
  letter-spacing: 0.5px;
}

.full-control {
  width: 100%;
}

/* ===== Template Info Card ===== */
.template-info-card {
  display: grid;
  gap: 12px;
  border: 1px solid var(--app-border);
  border-radius: var(--app-radius-md);
  background: var(--app-surface-soft);
  padding: 14px 16px;
}

.template-info-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}

.template-info-meta {
  display: grid;
  gap: 6px;
}

.meta-row {
  display: flex;
  align-items: baseline;
  gap: 8px;
  min-width: 0;
}

.meta-label {
  flex: 0 0 48px;
  color: var(--app-text-subtle);
  font-size: 12px;
  font-weight: 600;
}

.meta-value {
  color: var(--app-text-muted);
  font-size: 13px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* ===== Spec Block ===== */
.spec-block {
  display: grid;
  gap: 0;
  border: 1px solid var(--app-border);
  border-radius: var(--app-radius-md);
  overflow: hidden;
}

.spec-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 14px;
  background: var(--app-surface-muted);
  border-bottom: 1px solid var(--app-border);
}

.spec-header span {
  color: var(--app-text);
  font-size: 13px;
  font-weight: 600;
}

.spec-header small {
  color: var(--app-text-subtle);
  font-size: 10px;
  font-weight: 600;
  letter-spacing: 1px;
  text-transform: uppercase;
}

.rule-summary-panel {
  display: grid;
  gap: 14px;
  min-width: 0;
  padding: 14px;
  background: var(--app-surface);
}

.rule-group-list {
  display: grid;
  gap: 12px;
  min-width: 0;
}

.rule-group-card {
  min-width: 0;
  border: 1px solid var(--app-border-light);
  border-radius: var(--app-radius-md);
  background: var(--app-surface-soft);
  padding: 12px 14px;
}

.rule-group-card h4 {
  margin: 0 0 10px;
  color: var(--app-text);
  font-size: 13px;
  font-weight: 600;
}

.rule-list {
  display: grid;
  gap: 8px;
  margin: 0;
}

.rule-row {
  display: grid;
  grid-template-columns: minmax(80px, 0.32fr) minmax(0, 1fr);
  gap: 12px;
  align-items: start;
  min-width: 0;
}

.rule-row dt,
.rule-row dd {
  min-width: 0;
  margin: 0;
  font-size: 13px;
  line-height: 1.6;
}

.rule-row dt {
  color: var(--app-text-subtle);
  font-weight: 600;
}

.rule-row dd {
  color: var(--app-text-muted);
  overflow-wrap: anywhere;
  word-break: break-word;
}

.rule-empty-state {
  border: 1px dashed var(--app-border);
  border-radius: var(--app-radius-md);
  color: var(--app-text-muted);
  padding: 24px 14px;
  text-align: center;
  font-size: 13px;
}

.raw-json-details {
  min-width: 0;
  border-top: 1px solid var(--app-border-light);
  padding-top: 12px;
}

.raw-json-details summary {
  color: var(--app-text-muted);
  cursor: pointer;
  font-size: 13px;
  font-weight: 600;
  line-height: 1.5;
}

.raw-json-details summary:hover {
  color: var(--app-text);
}

.raw-json-details .spec-preview {
  min-height: 160px;
  margin-top: 10px;
  border: 1px solid var(--app-border);
  border-radius: var(--app-radius-md);
}

.spec-preview {
  min-height: 200px;
  max-height: 440px;
  max-width: 100%;
  overflow: auto;
  margin: 0;
  border: 0;
  border-radius: 0;
  background: var(--app-surface);
  color: var(--app-text);
  padding: 14px;
  font-family: "JetBrains Mono", ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  font-size: 12px;
  line-height: 1.6;
}

/* ===== Paper Action Row ===== */
.paper-action-row {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto auto;
  gap: 10px;
}

.paper-info-card {
  display: grid;
  gap: 6px;
  border: 1px solid var(--app-border);
  border-radius: var(--app-radius-md);
  background: var(--app-surface-soft);
  padding: 14px 16px;
}

/* ===== Report Banner ===== */
.report-banner {
  display: flex;
  align-items: center;
  gap: 16px;
  border: 1px solid var(--app-border);
  border-radius: var(--app-radius-md);
  background: var(--app-surface-soft);
  padding: 16px 18px;
  transition: background-color 0.2s ease, border-color 0.2s ease;
}

.report-banner-passed {
  border-color: rgba(93, 184, 114, 0.3);
  background: var(--app-success-soft);
}

.report-banner-issues {
  border-color: rgba(204, 120, 92, 0.28);
  background: var(--app-primary-soft);
}

.report-banner-content {
  display: flex;
  align-items: center;
  gap: 14px;
}

.report-banner-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 36px;
  height: 36px;
  flex: 0 0 36px;
  border-radius: var(--app-radius-sm);
  background: var(--app-surface);
  color: var(--app-primary);
  font-size: 18px;
}

.report-banner-passed .report-banner-icon {
  color: var(--app-success-hover);
}

.report-banner strong {
  color: var(--app-text);
  font-size: 14px;
}

.report-banner p {
  margin: 4px 0 0;
  color: var(--app-text-muted);
  font-size: 13px;
}

/* ===== Violation List ===== */
.violation-list {
  display: grid;
  gap: 10px;
}

.violation-item {
  border: 1px solid var(--app-border);
  border-left: 3px solid var(--app-border-strong);
  border-radius: var(--app-radius-md);
  background: var(--app-surface);
  padding: 14px 16px;
  transition: border-color 0.2s ease, box-shadow 0.2s ease;
}

.violation-item:hover {
  box-shadow: var(--app-shadow-sm);
}

.violation-error {
  border-left-color: var(--app-danger);
}

.violation-warning {
  border-left-color: var(--app-warning);
}

.violation-review {
  border-left-color: var(--app-primary);
}

.violation-header {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.violation-severity-dot {
  width: 8px;
  height: 8px;
  border-radius: 999px;
  flex: 0 0 8px;
}

.dot-error {
  background: var(--app-danger);
}

.dot-warning {
  background: var(--app-warning);
}

.dot-review {
  background: var(--app-primary);
}

.violation-code {
  color: var(--app-text);
  font-size: 14px;
  font-weight: 600;
}

.violation-location {
  color: var(--app-text-subtle);
  font-size: 12px;
}

.violation-message {
  margin: 10px 0 8px;
  color: var(--app-text);
  font-size: 14px;
  line-height: 1.6;
}

.violation-detail {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 8px;
  padding: 10px 0;
  border-top: 1px solid var(--app-border-light);
}

.detail-row {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.detail-label {
  color: var(--app-text-subtle);
  font-size: 11px;
  font-weight: 600;
  letter-spacing: 0.5px;
  text-transform: uppercase;
}

.detail-value {
  color: var(--app-text-muted);
  font-size: 13px;
  word-break: break-all;
}

.violation-suggestion {
  display: flex;
  align-items: flex-start;
  gap: 6px;
  margin-top: 4px;
  padding-top: 8px;
  border-top: 1px solid var(--app-border-light);
  color: var(--app-text-muted);
  font-size: 13px;
  line-height: 1.6;
}

.violation-suggestion .el-icon {
  margin-top: 2px;
  color: var(--app-warning);
  font-size: 14px;
  flex: 0 0 14px;
}

.violation-pagination {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 12px;
  min-width: 0;
  padding-top: 4px;
}

.violation-pagination span {
  color: var(--app-text-muted);
  font-size: 12px;
  white-space: nowrap;
}

/* ===== Passed / Idle States ===== */
.format-passed-state,
.format-idle-state {
  display: grid;
  justify-items: center;
  text-align: center;
  border: 1px dashed var(--app-border);
  border-radius: var(--app-radius-md);
  padding: 48px 24px;
}

.format-passed-state {
  border-color: rgba(93, 184, 114, 0.3);
  background: var(--app-success-soft);
}

.format-idle-state {
  background: linear-gradient(180deg, rgba(245, 240, 232, 0.5), rgba(250, 249, 245, 0));
}

.passed-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 48px;
  height: 48px;
  margin-bottom: 14px;
  border-radius: 999px;
  background: var(--app-success-soft);
  color: var(--app-success-hover);
  font-size: 24px;
}

.format-passed-state strong {
  color: var(--app-text);
  font-size: 16px;
  font-weight: 600;
}

.format-passed-state p {
  margin: 6px 0 0;
  color: var(--app-text-muted);
  font-size: 13px;
}

.idle-icon {
  position: relative;
  width: 32px;
  height: 42px;
  margin-bottom: 14px;
  border: 1.5px solid var(--app-border-strong);
  border-radius: 7px;
  background: var(--app-surface);
}

.idle-icon::before {
  position: absolute;
  top: -1.5px;
  right: -1.5px;
  width: 13px;
  height: 13px;
  border-bottom: 1.5px solid var(--app-border-strong);
  border-left: 1.5px solid var(--app-border-strong);
  border-radius: 0 7px 0 4px;
  background: var(--app-surface-strong);
  content: "";
}

.idle-icon::after {
  position: absolute;
  top: 20px;
  left: 9px;
  right: 9px;
  height: 1.5px;
  background: var(--app-border-strong);
  box-shadow: 0 7px 0 var(--app-border-strong);
  content: "";
}

.format-idle-state strong {
  color: var(--app-text);
  font-size: 15px;
  font-weight: 600;
}

.format-idle-state p {
  margin: 6px 0 0;
  color: var(--app-text-muted);
  font-size: 13px;
  line-height: 1.6;
}

/* ===== Responsive ===== */
@media (max-width: 960px) {
  .format-page {
    --format-shell-width: calc(100vw - 24px);
    padding: 0 12px 60px;
  }

  .format-hero,
  .format-layout,
  .upload-zone,
  .paper-action-row,
  .violation-detail {
    grid-template-columns: 1fr;
  }

  .format-hero-summary {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .rule-row {
    grid-template-columns: 1fr;
    gap: 2px;
  }

  .violation-pagination {
    justify-content: flex-start;
    flex-wrap: wrap;
  }
}
</style>
