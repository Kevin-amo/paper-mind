import { ref } from 'vue';
import { ElMessage } from 'element-plus';
import { uploadDocumentsBatch } from '../api/documents';
import { getErrorMessage } from '../api/http';
import type { BatchUploadDocumentPayload, BatchDocumentIngestionResponse } from '../types';

function buildFallbackSourceId(file: File) {
  return `paper-${file.name}-${file.size}-${file.lastModified}`
    .replace(/[^\p{L}\p{N}._-]+/gu, '-')
    .slice(0, 128);
}

export function useChatUpload(options: {
  onSuccess?: () => void;
} = {}) {
  const isUploading = ref(false);

  async function uploadFiles(files: File[]) {
    if (!files.length) return;

    isUploading.value = true;

    const payload: BatchUploadDocumentPayload = {
      items: files.map((file) => ({
        file,
        sourceId: buildFallbackSourceId(file),
        title: file.name.replace(/\.[^.]+$/, ''),
      })),
    };

    try {
      const result: BatchDocumentIngestionResponse = await uploadDocumentsBatch(payload);

      const { acceptedCount, failureCount } = result;
      if (acceptedCount > 0 && failureCount === 0) {
        ElMessage.success(`上传请求已提交，共 ${acceptedCount} 个文件正在处理`);
      } else if (acceptedCount > 0) {
        ElMessage.warning(`上传请求已提交：已入队 ${acceptedCount} 个，失败 ${failureCount} 个`);
      } else {
        ElMessage.error('上传请求提交失败，请检查文件格式或网络连接');
      }

      options.onSuccess?.();
    } catch (error) {
      const message = getErrorMessage(error);
      ElMessage.error(`上传失败：${message}`);
    } finally {
      isUploading.value = false;
    }
  }

  return {
    isUploading,
    uploadFiles,
  };
}
