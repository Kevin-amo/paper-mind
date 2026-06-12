import { ref } from 'vue';
import { ElMessage } from 'element-plus';
import {
  assignReviewers,
  confirmConsensus,
  dispatchReviewTask,
  getAdminReviewTask,
  listAdminReviewTasks,
  listReviewGroups,
  listReviewerLoads,
  recalculateConsensus,
  updateConsensus,
} from '../api/adminReviews';
import { getErrorMessage } from '../api/http';
import type {
  AdminReviewTaskDetail,
  AdminReviewTaskSummary,
  AssignReviewersPayload,
  DispatchReviewTaskPayload,
  ReviewGroup,
  ReviewerLoad,
  UpdateReviewConsensusPayload,
} from '../types';

export function useAdminReviews() {
  const loading = ref(false);
  const tasks = ref<AdminReviewTaskSummary[]>([]);
  const total = ref(0);
  const page = ref(0);
  const size = ref(20);
  const keyword = ref('');
  const status = ref('');
  const selectedTask = ref<AdminReviewTaskDetail | null>(null);
  const reviewerLoads = ref<ReviewerLoad[]>([]);
  const reviewGroups = ref<ReviewGroup[]>([]);

  async function loadTasks(nextPage = page.value) {
    loading.value = true;
    try {
      const data = await listAdminReviewTasks({
        keyword: keyword.value,
        status: status.value,
        page: nextPage,
        size: size.value,
      });
      tasks.value = data.items;
      total.value = data.total;
      page.value = data.page;
      size.value = data.size;
    } catch (error) {
      ElMessage.error(getErrorMessage(error));
    } finally {
      loading.value = false;
    }
  }

  async function openTask(taskId: string) {
    loading.value = true;
    try {
      selectedTask.value = await getAdminReviewTask(taskId);
      return selectedTask.value;
    } catch (error) {
      ElMessage.error(getErrorMessage(error));
      return null;
    } finally {
      loading.value = false;
    }
  }

  async function loadReviewerLoads() {
    try {
      reviewerLoads.value = await listReviewerLoads();
    } catch (error) {
      ElMessage.error(getErrorMessage(error));
    }
  }

  async function loadGroups() {
    try {
      reviewGroups.value = await listReviewGroups();
    } catch (error) {
      ElMessage.error(getErrorMessage(error));
    }
  }

  async function dispatchTask(taskId: string, payload: DispatchReviewTaskPayload) {
    loading.value = true;
    try {
      await dispatchReviewTask(taskId, payload);
      ElMessage.success('评审任务已派发到小组');
      await Promise.all([loadTasks(page.value), openTask(taskId), loadGroups()]);
    } catch (error) {
      ElMessage.error(getErrorMessage(error));
      throw error;
    } finally {
      loading.value = false;
    }
  }

  async function saveAssignments(taskId: string, payload: AssignReviewersPayload) {
    loading.value = true;
    try {
      await assignReviewers(taskId, payload);
      ElMessage.success('评审人分配已保存');
      await Promise.all([loadTasks(page.value), openTask(taskId), loadReviewerLoads()]);
    } catch (error) {
      ElMessage.error(getErrorMessage(error));
      throw error;
    } finally {
      loading.value = false;
    }
  }

  async function recalc(taskId: string) {
    loading.value = true;
    try {
      const consensus = await recalculateConsensus(taskId);
      if (selectedTask.value?.task.id === taskId) {
        selectedTask.value = { ...selectedTask.value, consensus };
      }
      ElMessage.success('共识结果已重新计算');
      await loadTasks(page.value);
      return consensus;
    } catch (error) {
      ElMessage.error(getErrorMessage(error));
      throw error;
    } finally {
      loading.value = false;
    }
  }

  async function saveConsensus(taskId: string, payload: UpdateReviewConsensusPayload) {
    loading.value = true;
    try {
      const consensus = await updateConsensus(taskId, payload);
      if (selectedTask.value?.task.id === taskId) {
        selectedTask.value = { ...selectedTask.value, consensus };
      }
      ElMessage.success('最终意见已保存');
      await loadTasks(page.value);
      return consensus;
    } catch (error) {
      ElMessage.error(getErrorMessage(error));
      throw error;
    } finally {
      loading.value = false;
    }
  }

  async function confirm(taskId: string) {
    loading.value = true;
    try {
      const consensus = await confirmConsensus(taskId);
      if (selectedTask.value?.task.id === taskId) {
        selectedTask.value = { ...selectedTask.value, consensus };
      }
      ElMessage.success('评审共识已确认');
      await loadTasks(page.value);
      return consensus;
    } catch (error) {
      ElMessage.error(getErrorMessage(error));
      throw error;
    } finally {
      loading.value = false;
    }
  }

  return {
    loading,
    tasks,
    total,
    page,
    size,
    keyword,
    status,
    selectedTask,
    reviewerLoads,
    reviewGroups,
    loadTasks,
    openTask,
    loadReviewerLoads,
    loadGroups,
    dispatchTask,
    saveAssignments,
    recalc,
    saveConsensus,
    confirm,
  };
}
