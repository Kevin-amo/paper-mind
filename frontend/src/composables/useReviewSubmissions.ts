import { reactive, ref } from 'vue';
import { ElMessage } from 'element-plus';
import {
  getReviewSubmissionUploadJob,
  listReviewSubmissions,
  uploadReviewSubmission,
} from '../api/reviewSubmissions';
import { getErrorMessage } from '../api/http';
import type { ReviewSubmission } from '../types';

export function useReviewSubmissions() {
  const submissions = ref<ReviewSubmission[]>([]);
  const loading = ref(false);
  const uploading = ref(false);
  const pagination = reactive({ page: 0, size: 20, total: 0 });

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
      const result = await uploadReviewSubmission({
        file,
        title: file.name.replace(/\.[^.]+$/, ''),
      });
      await waitForUploadSettled(result.jobId);
      await loadSubmissions(0);
      ElMessage.success('评审投稿已提交');
    } catch (error) {
      ElMessage.error(getErrorMessage(error));
    } finally {
      uploading.value = false;
    }
  }

  async function waitForUploadSettled(jobId: string) {
    const maxAttempts = 20;
    for (let attempt = 0; attempt < maxAttempts; attempt++) {
      const job = await getReviewSubmissionUploadJob(jobId);
      if (job.status === 'INDEXED') {
        return;
      }
      if (job.status === 'FAILED') {
        throw new Error(job.errorMessage || '投稿入库失败');
      }
      await new Promise((resolve) => { setTimeout(resolve, 1500); });
    }
  }

  return {
    submissions,
    loading,
    uploading,
    pagination,
    loadSubmissions,
    submitFile,
  };
}
