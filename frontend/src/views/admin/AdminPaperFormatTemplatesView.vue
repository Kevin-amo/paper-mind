<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue';
import { Check, Delete, Edit, Refresh, Upload, View } from '@element-plus/icons-vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import AdminShell from '../../components/admin/AdminShell.vue';
import {
  confirmAdminFormatTemplate,
  getAdminFormatTemplate,
  listAdminFormatTemplates,
  unpublishAdminFormatTemplate,
  updateAdminFormatTemplate,
  uploadAdminFormatTemplate,
} from '../../api/adminPaperFormat';
import { getErrorMessage } from '../../api/http';
import { formatDate, formatJson } from '../../utils/format';
import type { PaperFormatTemplate } from '../../types';

const loading = ref(false);
const saving = ref(false);
const uploading = ref(false);
const confirming = ref(false);
const templates = ref<PaperFormatTemplate[]>([]);
const selectedTemplate = ref<PaperFormatTemplate | null>(null);
const uploadDialogVisible = ref(false);
const editDialogVisible = ref(false);
const detailDrawerVisible = ref(false);
const uploadFileInputRef = ref<HTMLInputElement | null>(null);
const uploadFile = ref<File | null>(null);

const uploadForm = reactive({
  name: '',
  schoolName: '',
});

const editForm = reactive({
  id: '',
  name: '',
  schoolName: '',
  publicTemplate: true,
});

const templateTable = computed(() => templates.value);

async function loadTemplates() {
  loading.value = true;
  try {
    templates.value = await listAdminFormatTemplates();
  } catch (error) {
    ElMessage.error(getErrorMessage(error));
  } finally {
    loading.value = false;
  }
}

function resetUploadForm() {
  uploadForm.name = '';
  uploadForm.schoolName = '';
  uploadFile.value = null;
}

function openUploadDialog() {
  resetUploadForm();
  uploadDialogVisible.value = true;
}

function selectUploadFile() {
  uploadFileInputRef.value?.click();
}

function handleUploadFileChange(event: Event) {
  const file = (event.target as HTMLInputElement).files?.[0] ?? null;
  (event.target as HTMLInputElement).value = '';
  if (!file) return;
  if (!file.name.toLowerCase().endsWith('.docx')) {
    ElMessage.warning('目前仅支持 .docx 模板');
    return;
  }
  uploadFile.value = file;
  if (!uploadForm.name.trim()) {
    uploadForm.name = file.name.replace(/\.docx$/i, '');
  }
}

async function submitUpload() {
  if (!uploadFile.value) {
    ElMessage.warning('请先选择 .docx 模板文件');
    return;
  }
  if (!uploadForm.name.trim()) {
    ElMessage.warning('请填写模板名称');
    return;
  }
  uploading.value = true;
  try {
    const created = await uploadAdminFormatTemplate({
      file: uploadFile.value,
      name: uploadForm.name.trim(),
      schoolName: uploadForm.schoolName.trim() || null,
    });
    upsertTemplate(created);
    uploadDialogVisible.value = false;
    ElMessage.success('模板已上传并解析');
  } catch (error) {
    ElMessage.error(getErrorMessage(error));
  } finally {
    uploading.value = false;
  }
}

function openEditDialog(template: PaperFormatTemplate) {
  editForm.id = template.id;
  editForm.name = template.name;
  editForm.schoolName = template.schoolName ?? '';
  editForm.publicTemplate = template.publicTemplate;
  editDialogVisible.value = true;
}

async function submitEdit() {
  if (!editForm.id) return;
  if (!editForm.name.trim()) {
    ElMessage.warning('请填写模板名称');
    return;
  }
  saving.value = true;
  try {
    const updated = await updateAdminFormatTemplate(editForm.id, {
      name: editForm.name.trim(),
      schoolName: editForm.schoolName.trim(),
      publicTemplate: editForm.publicTemplate,
    });
    upsertTemplate(updated);
    editDialogVisible.value = false;
    ElMessage.success('模板信息已保存');
  } catch (error) {
    ElMessage.error(getErrorMessage(error));
  } finally {
    saving.value = false;
  }
}

async function openDetail(template: PaperFormatTemplate) {
  detailDrawerVisible.value = true;
  selectedTemplate.value = template;
  try {
    selectedTemplate.value = await getAdminFormatTemplate(template.id);
  } catch (error) {
    ElMessage.error(getErrorMessage(error));
  }
}

async function confirmTemplate(template = selectedTemplate.value) {
  if (!template) return;
  confirming.value = true;
  try {
    const updated = await confirmAdminFormatTemplate(template.id);
    upsertTemplate(updated);
    selectedTemplate.value = updated;
    ElMessage.success('模板规则已确认');
  } catch (error) {
    ElMessage.error(getErrorMessage(error));
  } finally {
    confirming.value = false;
  }
}

async function unpublishTemplate(template: PaperFormatTemplate) {
  try {
    await ElMessageBox.confirm('下架后评审员将不能选择该模板，历史检查记录会保留。', '下架模板', {
      confirmButtonText: '下架',
      cancelButtonText: '取消',
      type: 'warning',
    });
    const updated = await unpublishAdminFormatTemplate(template.id);
    upsertTemplate(updated);
    if (selectedTemplate.value?.id === updated.id) {
      selectedTemplate.value = updated;
    }
    ElMessage.success('模板已下架');
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error(getErrorMessage(error));
    }
  }
}

async function publishTemplate(template: PaperFormatTemplate) {
  saving.value = true;
  try {
    const updated = await updateAdminFormatTemplate(template.id, { publicTemplate: true });
    upsertTemplate(updated);
    ElMessage.success('模板已恢复上架');
  } catch (error) {
    ElMessage.error(getErrorMessage(error));
  } finally {
    saving.value = false;
  }
}

function upsertTemplate(template: PaperFormatTemplate) {
  const index = templates.value.findIndex((item) => item.id === template.id);
  if (index >= 0) {
    templates.value.splice(index, 1, template);
  } else {
    templates.value.unshift(template);
  }
}

function statusType(status: string) {
  if (status === 'READY') return 'success';
  if (status === 'FAILED') return 'danger';
  if (status === 'NEED_CONFIRM') return 'warning';
  return 'info';
}

onMounted(loadTemplates);
</script>

<template>
  <AdminShell active="templates" title="学校论文模板">
    <section class="template-admin-panel paper-mind-workspace-card">
      <div class="template-toolbar">
        <div>
          <h3>学校论文模板管理</h3>
          <p>维护评审端格式检查可调用的学校论文模板。</p>
        </div>
        <div class="template-actions">
          <el-button @click="loadTemplates">
            <el-icon :class="{ 'is-rotating': loading }"><Refresh /></el-icon>
            刷新
          </el-button>
          <el-button type="primary" :icon="Upload" @click="openUploadDialog">上传模板</el-button>
        </div>
      </div>

      <el-table v-loading="loading" :data="templateTable" class="template-table" empty-text="暂无学校论文模板">
        <el-table-column prop="name" label="模板名称" min-width="180" show-overflow-tooltip />
        <el-table-column prop="schoolName" label="学校" min-width="150" show-overflow-tooltip>
          <template #default="{ row }">{{ row.schoolName || '-' }}</template>
        </el-table-column>
        <el-table-column prop="fileName" label="文件" min-width="190" show-overflow-tooltip />
        <el-table-column label="状态" width="120">
          <template #default="{ row }">
            <el-tag :type="statusType(row.status)" size="small">{{ row.status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="规则" width="100">
          <template #default="{ row }">
            <el-tag :type="row.confirmed ? 'success' : 'warning'" size="small">{{ row.confirmed ? '已确认' : '待确认' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="上架" width="100">
          <template #default="{ row }">
            <el-switch v-model="row.publicTemplate" :loading="saving" @change="row.publicTemplate ? publishTemplate(row) : unpublishTemplate(row)" />
          </template>
        </el-table-column>
        <el-table-column label="更新时间" min-width="170">
          <template #default="{ row }">{{ formatDate(row.updatedAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="260" fixed="right">
          <template #default="{ row }">
            <el-button text :icon="View" @click="openDetail(row)">详情</el-button>
            <el-button text :icon="Edit" @click="openEditDialog(row)">编辑</el-button>
            <el-button v-if="!row.confirmed" text :icon="Check" @click="confirmTemplate(row)">确认</el-button>
            <el-button text type="danger" :icon="Delete" @click="unpublishTemplate(row)">下架</el-button>
          </template>
        </el-table-column>
      </el-table>
    </section>

    <el-dialog v-model="uploadDialogVisible" title="上传学校论文模板" width="520px">
      <el-form label-position="top">
        <el-form-item label="模板名称" required>
          <el-input v-model="uploadForm.name" maxlength="160" />
        </el-form-item>
        <el-form-item label="学校名称">
          <el-input v-model="uploadForm.schoolName" maxlength="160" />
        </el-form-item>
        <el-form-item label="模板文件" required>
          <div class="file-picker">
            <el-button :icon="Upload" @click="selectUploadFile">选择 .docx</el-button>
            <span>{{ uploadFile?.name || '未选择文件' }}</span>
          </div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="uploadDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="uploading" @click="submitUpload">上传</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="editDialogVisible" title="编辑模板" width="520px">
      <el-form label-position="top">
        <el-form-item label="模板名称" required>
          <el-input v-model="editForm.name" maxlength="160" />
        </el-form-item>
        <el-form-item label="学校名称">
          <el-input v-model="editForm.schoolName" maxlength="160" />
        </el-form-item>
        <el-form-item label="评审端可用">
          <el-switch v-model="editForm.publicTemplate" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submitEdit">保存</el-button>
      </template>
    </el-dialog>

    <el-drawer v-model="detailDrawerVisible" title="模板详情" size="48%">
      <template v-if="selectedTemplate">
        <div class="template-detail-header">
          <div>
            <strong>{{ selectedTemplate.name }}</strong>
            <p>{{ selectedTemplate.schoolName || '-' }} / {{ selectedTemplate.fileName }}</p>
          </div>
          <el-button :icon="Check" type="primary" :loading="confirming" @click="confirmTemplate()">确认规则</el-button>
        </div>
        <el-descriptions :column="2" border>
          <el-descriptions-item label="状态">{{ selectedTemplate.status }}</el-descriptions-item>
          <el-descriptions-item label="规则确认">{{ selectedTemplate.confirmed ? '已确认' : '待确认' }}</el-descriptions-item>
          <el-descriptions-item label="评审端可用">{{ selectedTemplate.publicTemplate ? '是' : '否' }}</el-descriptions-item>
          <el-descriptions-item label="更新时间">{{ formatDate(selectedTemplate.updatedAt) }}</el-descriptions-item>
        </el-descriptions>
        <h4>formatSpec</h4>
        <pre>{{ formatJson(selectedTemplate.formatSpec) }}</pre>
        <h4>extractionReport</h4>
        <pre>{{ formatJson(selectedTemplate.extractionReport) }}</pre>
      </template>
    </el-drawer>

    <input ref="uploadFileInputRef" type="file" accept=".docx,application/vnd.openxmlformats-officedocument.wordprocessingml.document" hidden @change="handleUploadFileChange" />
  </AdminShell>
</template>

<style scoped>
.template-admin-panel {
  display: grid;
  gap: 16px;
  border: 1px solid var(--app-border);
  border-radius: var(--app-radius-lg);
  background: var(--claude-canvas);
  padding: 24px;
}

.template-toolbar,
.template-actions,
.template-detail-header,
.file-picker {
  display: flex;
  align-items: center;
  gap: 12px;
}

.template-toolbar,
.template-detail-header {
  justify-content: space-between;
}

.template-toolbar h3 {
  margin: 0;
  color: var(--app-text);
  font-size: 18px;
}

.template-toolbar p,
.template-detail-header p,
.file-picker span {
  margin: 4px 0 0;
  color: var(--app-text-muted);
  font-size: 13px;
}

.template-table {
  overflow: hidden;
  border: 1px solid var(--app-border);
  border-radius: var(--app-radius-lg);
}

.template-detail-header {
  margin-bottom: 16px;
}

.template-detail-header strong {
  color: var(--app-text);
  font-size: 18px;
}

h4 {
  margin: 18px 0 8px;
  color: var(--app-text);
}

pre {
  max-height: 300px;
  overflow: auto;
  border: 1px solid var(--app-border);
  border-radius: var(--app-radius-md);
  background: var(--app-surface-soft);
  color: var(--app-text);
  padding: 12px;
  font-size: 12px;
  line-height: 1.55;
}

.is-rotating {
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

@media (max-width: 860px) {
  .template-toolbar,
  .template-detail-header {
    align-items: flex-start;
    flex-direction: column;
  }
}
</style>
