import { ref } from 'vue';
import { ElMessage } from 'element-plus';
import { getUploadPolicy } from '../api/documents';
import { getErrorMessage } from '../api/http';
import { uploadFileToOss } from '../utils/ossUpload';
import type { OssUploadPolicy, BatchUploadDocumentPayload, BatchDocumentIngestionResponse } from '../types';

export type UploadQueueItemStatus = 'pending' | 'uploading' | 'success' | 'failed';

export interface UploadQueueItem {
  id: string;
  fileName: string;
  fileSize: number;
  status: UploadQueueItemStatus;
  errorMessage?: string;
  progress?: number;
  sourceId?: string;
}

let idCounter = 0;

function generateId() {
  idCounter++;
  return `upload-${Date.now()}-${idCounter}`;
}

function buildFallbackSourceId(file: File) {
  return `paper-${file.name}-${file.size}-${file.lastModified}`
    .replace(/[^\p{L}\p{N}._-]+/gu, '-')
    .slice(0, 128);
}

export function useChatUpload(options: {
  onSuccess?: () => void;
} = {}) {
  const queue = ref<UploadQueueItem[]>([]);
  const isUploading = ref(false);

  function addFiles(files: File[]) {
    const newItems: UploadQueueItem[] = files.map((file) => ({
      id: generateId(),
      fileName: file.name,
      fileSize: file.size,
      status: 'pending',
    }));
    queue.value.push(...newItems);
    return newItems;
  }

  function removeItem(id: string) {
    queue.value = queue.value.filter((item) => item.id !== id);
  }

  function clearQueue() {
    queue.value = [];
  }

  function updateItem(id: string, patch: Partial<UploadQueueItem>) {
    const item = queue.value.find((i) => i.id === id);
    if (item) {
      Object.assign(item, patch);
    }
  }

  async function uploadFiles(files: File[]) {
    if (!files.length) return;

    const items = addFiles(files);
    isUploading.value = true;

    // 对每个文件：获取凭证 → 直传 OSS
    const uploadPromises = files.map(async (file, index) => {
      const queueItem = items[index];
      updateItem(queueItem.id, { status: 'uploading', progress: 0 });

      try {
        // Step 1: 获取上传凭证
        const policy = await getUploadPolicy({
          fileName: file.name,
          contentType: file.type || 'application/octet-stream',
          fileSize: file.size,
          sourceId: buildFallbackSourceId(file),
          title: file.name.replace(/\.[^.]+$/, ''),
        });

        updateItem(queueItem.id, { sourceId: policy.sourceId, progress: 5 });

        // Step 2: 直传 OSS（带真实进度）
        await uploadFileToOss(file, policy, (progress) => {
          // OSS 上传进度映射到 5-95 的范围（前5%是获取凭证，后5%是回调处理）
          const mappedProgress = Math.round(5 + (progress / 100) * 90);
          updateItem(queueItem.id, { progress: mappedProgress });
        });

        // 上传成功，OSS 会回调后端创建入库任务
        updateItem(queueItem.id, { status: 'success', progress: 100, sourceId: policy.sourceId });
        return { success: true, sourceId: policy.sourceId, fileName: file.name };
      } catch (error) {
        const message = getErrorMessage(error) || '上传失败';
        updateItem(queueItem.id, {
          status: 'failed',
          progress: 100,
          errorMessage: message,
        });
        return { success: false, sourceId: null, fileName: file.name, errorMessage: message };
      }
    });

    const results = await Promise.allSettled(uploadPromises);

    const successCount = results.filter(
      (r) => r.status === 'fulfilled' && r.value?.success,
    ).length;
    const failureCount = results.length - successCount;

    if (successCount > 0 && failureCount === 0) {
      ElMessage.success(`上传完成：${successCount} 个文件已进入解析队列`);
    } else if (successCount > 0) {
      ElMessage.warning(`上传完成：成功 ${successCount} 个，失败 ${failureCount} 个`);
    } else {
      ElMessage.error('上传失败，请检查文件格式或网络连接');
    }

    options.onSuccess?.();
    isUploading.value = false;
  }

  /**
   * 降级上传：使用旧的批量上传接口（MultipartFile → 后端 → 本地存储）。
   * 当 OSS 直传不可用时作为回退方案。
   */
  async function uploadFilesLegacy(payload: BatchUploadDocumentPayload) {
    // 此方法保留但不再默认使用
    // 如果需要降级，可从 api/documents.ts 调用 uploadDocumentsBatch
  }

  return {
    queue,
    isUploading,
    addFiles,
    removeItem,
    clearQueue,
    uploadFiles,
  };
}
