import { reactive, ref } from 'vue';
import { ElMessage } from 'element-plus';
import {
  getReviewSubmissionUploadJob,
  listReviewSubmissions,
} from '../api/reviewSubmissions';
import { getUploadPolicy } from '../api/documents';
import { uploadFileToOss } from '../utils/ossUpload';
import { getErrorMessage } from '../api/http';
import type { ReviewSubmission } from '../types';

function buildFallbackSourceId(file: File) {
  return `review-${file.name}-${file.size}-${file.lastModified}`
    .replace(/[^\p{L}\p{N}._-]+/gu, '-')
    .slice(0, 128);
}

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

  /**
   * 提交投稿文件：走 OSS 直传流程。
   * 1. 向后端获取 OSS 直传凭证（sourceType=REVIEW）
   * 2. 前端直接 POST 到 OSS
   * 3. OSS 回调后端创建入库任务（标记来源为 REVIEW）
   * 4. 等待入库完成后刷新列表
   */
  async function submitFile(file: File) {
    if (uploading.value) {
      return;
    }
    uploading.value = true;
    try {
      // Step 1: 获取 OSS 直传凭证，指定 sourceType=REVIEW
      const policy = await getUploadPolicy({
        fileName: file.name,
        contentType: file.type || 'application/octet-stream',
        fileSize: file.size,
        sourceId: buildFallbackSourceId(file),
        title: file.name.replace(/\.[^.]+$/, ''),
        sourceType: 'REVIEW',
      });

      // Step 2: 直传 OSS
      await uploadFileToOss(file, policy);

      // Step 3: 等待入库完成（OSS 回调后端已创建 job）
      // 回调创建的 job 没有 jobId 直接返回给前端，需要通过 sourceId 查询
      // 由于 OSS 回调是异步的，job 可能还未创建，等待几秒后重试
      await waitForJobBySourceId(policy.sourceId);
      await loadSubmissions(0);
      ElMessage.success('评审投稿已提交');
    } catch (error) {
      ElMessage.error(getErrorMessage(error));
    } finally {
      uploading.value = false;
    }
  }

  /**
   * 等待 OSS 回调创建的 job 入库完成。
   * 先等 3 秒让 OSS 回调有时间到达后端并创建 job，
   * 然后轮询检查 sourceId 是否出现在列表中及其状态。
   */
  async function waitForJobBySourceId(sourceId: string) {
    // 先等 OSS 回调到达后端
    await new Promise((resolve) => { setTimeout(resolve, 3000); });

    const maxAttempts = 10;
    for (let attempt = 0; attempt < maxAttempts; attempt++) {
      try {
        const result = await listReviewSubmissions({ page: 0, size: 50 });
        const found = result.items.find((s) => s.sourceId === sourceId);
        if (found) {
          const status = String(found.documentStatus).toUpperCase();
          if (status === 'INDEXED' || status === 'READY') {
            return;
          }
          if (status === 'FAILED') {
            throw new Error('投稿入库失败');
          }
        }
      } catch (e) {
        // 列表查询失败不影响轮询
      }
      await new Promise((resolve) => { setTimeout(resolve, 3000); });
    }
    // 超时不算失败，只是不再等待
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
