package com.lqr.papermind.common.storage.service;

import java.util.Date;

/**
 * OSS PostObject 直传凭证生成服务。
 */
public interface OssPostPolicyService {

    /**
     * Post Policy 签名结果。
     */
    record PostPolicyResult(
            String encodedPolicy,
            String signature,
            String host,
            String dir,
            String objectKey
    ) {}

    /**
     * 为指定目录前缀和文件大小限制生成 OSS PostObject 签名凭证。
     *
     * @param endpoint       OSS Endpoint
     * @param bucket         OSS Bucket 名称
     * @param accessKeyId    OSS AccessKey ID
     * @param accessKeySecret OSS AccessKey Secret
     * @param dir            上传目录前缀（如 documents/userId/sourceId/）
     * @param objectKey      完整对象键
     * @param maxSizeBytes   文件大小上限（字节）
     * @param expireEndTime  凭证过期时间
     * @return 签名结果
     */
    PostPolicyResult generatePostPolicy(
            String endpoint,
            String bucket,
            String accessKeyId,
            String accessKeySecret,
            String dir,
            String objectKey,
            long maxSizeBytes,
            Date expireEndTime);
}
