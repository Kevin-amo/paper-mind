import { reactive, ref } from 'vue';
import { ElMessage } from 'element-plus';
import {
  listReviewSubmissions,
  uploadReviewSubmission,
} from '../api/reviewSubmissions';
import { getErrorMessage } from '../api/http';
import type { ReviewSubmission } from '../types';

const PENDING_STATUSES: Set<string> = new Set(['PENDING', 'PROCESSING']);

export function useReviewSubmissions() {
  const submissions = ref<ReviewSubmission[]>([]);
  const loading = ref(false);
  const uploading = ref(false);
  const pagination = reactive({ page: 0, size: 20, total: 0 });

  let pollingTimer: ReturnType<typeof setTimeout> | null = null;
  let pollingAttempts = 0;
  const maxPollingAttempts = 30;
  const pollingInterval = 3000;

  async function loadSubmissions(page = pagination.page) {
    loading.value = true;
    try {
      const result = await listReviewSubmissions({
        page,
        size: pagination.size,
      });
      submissions.value = result.items;
      pagination.page = result.page;
      pagination.size = result.size;
      pagination.total = result.total;
    } catch (error) {
      ElMessage.error(getErrorMessage(error));
    } finally {
      loading.value = false;
    }
  }

  async function submitFile(file: File) {
    if (uploading.value) {
      return;
    }
    uploading.value = true;
    try {
      await uploadReviewSubmission({
        file,
        title: file.name.replace(/\.[^.]+$/, ''),
      });
      uploading.value = false;
      ElMessage.success('评审投稿已提交');
      await loadSubmissions(0);
      startStatusPolling();
    } catch (error) {
      uploading.value = false;
      ElMessage.error(getErrorMessage(error));
    }
  }

  function hasPendingSubmissions(): boolean {
    return submissions.value.some(
      (s) => PENDING_STATUSES.has(s.documentStatus),
    );
  }

  function notifyFailedSubmissions() {
    for (const s of submissions.value) {
      if (s.documentStatus === 'FAILED' && s.errorMessage) {
        ElMessage.error(`${s.title || s.fileName}: ${s.errorMessage}`);
      }
    }
  }

  function startStatusPolling() {
    if (pollingTimer) {
      return;
    }
    pollingAttempts = 0;
    scheduleNextPoll();
  }

  function scheduleNextPoll() {
    pollingTimer = setTimeout(async () => {
      pollingTimer = null;
      pollingAttempts++;

      if (pollingAttempts > maxPollingAttempts) {
        stopStatusPolling();
        return;
      }

      try {
        await loadSubmissions(pagination.page);
        notifyFailedSubmissions();
        if (hasPendingSubmissions()) {
          scheduleNextPoll();
        } else {
          stopStatusPolling();
        }
      } catch {
        stopStatusPolling();
      }
    }, pollingInterval);
  }

  function stopStatusPolling() {
    if (pollingTimer) {
      clearTimeout(pollingTimer);
      pollingTimer = null;
    }
    pollingAttempts = 0;
  }

  return {
    submissions,
    loading,
    uploading,
    pagination,
    loadSubmissions,
    submitFile,
    startStatusPolling,
    stopStatusPolling,
  };
}
