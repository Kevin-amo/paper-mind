<script setup lang="ts">
import { computed, reactive, watch } from 'vue';
import { ElMessage } from 'element-plus';
import type { AdminReviewTaskDetail, DispatchReviewTaskPayload, ReviewGroup } from '../../../types';

const props = defineProps<{
  modelValue: boolean;
  task: AdminReviewTaskDetail | null;
  groups: ReviewGroup[];
}>();

const emit = defineEmits<{
  'update:modelValue': [value: boolean];
  submit: [taskId: string, payload: DispatchReviewTaskPayload];
}>();

const visible = computed({
  get: () => props.modelValue,
  set: (value: boolean) => emit('update:modelValue', value),
});

const form = reactive({
  groupId: '',
  dueAt: '' as string | null,
});

const activeGroups = computed(() => props.groups.filter((group) => group.status === 'ACTIVE'));
const dispatchInvalid = computed(() => !form.groupId);

watch(
  () => props.task,
  (detail) => {
    form.groupId = detail?.task.groupId ?? '';
    form.dueAt = detail?.task.dueAt ?? null;
  },
  { immediate: true },
);

function groupLabel(group: ReviewGroup) {
  const leader = group.leaderDisplayName || group.leaderUsername || group.leaderUserId;
  return `${group.name} - ${leader}`;
}

function handleSubmit() {
  if (!props.task) return;
  if (!form.groupId) {
    ElMessage.warning('请选择评审小组');
    return;
  }
  emit('submit', props.task.task.id, {
    groupId: form.groupId,
    dueAt: form.dueAt || null,
  });
}
</script>

<template>
  <el-drawer v-model="visible" size="min(520px, 94vw)" destroy-on-close class="claude-workspace-drawer">
    <template #header>
      <div>
        <span class="eyebrow">Dispatch</span>
        <h3>派发小组</h3>
      </div>
    </template>

    <el-empty v-if="!task" description="请选择评审任务" />
    <el-form v-else label-position="top" class="dispatch-form">
      <el-alert :title="task.task.title" type="info" :closable="false" />
      <el-form-item label="评审小组" required>
        <el-select v-model="form.groupId" filterable class="full-width" placeholder="选择评审小组">
          <el-option
            v-for="group in activeGroups"
            :key="group.id"
            :label="groupLabel(group)"
            :value="group.id"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="截止时间">
        <el-date-picker v-model="form.dueAt" type="datetime" value-format="YYYY-MM-DDTHH:mm:ssZ" class="full-width" />
      </el-form-item>
    </el-form>

    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button type="primary" :disabled="!task || dispatchInvalid" @click="handleSubmit">派发</el-button>
    </template>
  </el-drawer>
</template>

<style scoped>
.eyebrow {
  color: var(--app-primary);
  font-size: 12px;
  font-weight: 800;
  letter-spacing: 0.12em;
  text-transform: uppercase;
}

h3 {
  margin: 4px 0 0;
}

.dispatch-form {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.full-width {
  width: 100%;
}
</style>
