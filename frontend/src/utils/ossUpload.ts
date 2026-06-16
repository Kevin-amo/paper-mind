import type { OssUploadPolicy } from '../types';

export async function uploadFileToOss(
  file: File,
  policy: OssUploadPolicy,
  onProgress?: (progress: number) => void,
): Promise<void> {
  const formData = new FormData();
  formData.append('key', policy.objectKey);
  formData.append('policy', policy.policy);
  formData.append('OSSAccessKeyId', policy.accessKeyId);
  formData.append('signature', policy.signature);
  formData.append('success_action_status', '200');

  // OSS PostObject 回调参数：告诉 OSS 上传成功后回调后端的 URL 和回调体格式
  formData.append('callback', policy.callback);

  // OSS PostObject 回调自定义变量：提供 ${x:varName} 的具体值，由服务端签名防止篡改
  formData.append('callback-var', policy.callbackVar);

  // 文件本身必须放在最后
  formData.append('file', file);

  // 使用 XMLHttpRequest 以便跟踪上传进度
  return new Promise<void>((resolve, reject) => {
    const xhr = new XMLHttpRequest();
    xhr.open('POST', policy.host, true);

    xhr.upload.onprogress = (event) => {
      if (event.lengthComputable && onProgress) {
        const percent = Math.round((event.loaded / event.total) * 100);
        onProgress(percent);
      }
    };

    xhr.onload = () => {
      if (xhr.status >= 200 && xhr.status < 300) {
        resolve();
      } else {
        reject(new Error(`OSS 上传失败：HTTP ${xhr.status}`));
      }
    };

    xhr.onerror = () => {
      reject(new Error('OSS 上传网络错误'));
    };

    xhr.ontimeout = () => {
      reject(new Error('OSS 上传超时'));
    };

    xhr.timeout = 300000; // 5 分钟超时
    xhr.send(formData);
  });
}
