package com.lqr.papermind.common.storage.service.impl;

import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.aliyun.oss.common.utils.BinaryUtil;
import com.aliyun.oss.model.MatchMode;
import com.aliyun.oss.model.PolicyConditions;
import com.lqr.papermind.common.storage.service.OssPostPolicyService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * 阿里云 OSS PostObject 直传凭证生成实现。
 * <p>
 * 使用 OSS SDK 生成 Post Policy 签名。
 * </p>
 */
@Slf4j
@Service
public class AliyunOssPostPolicyServiceImpl implements OssPostPolicyService {

    @Override
    public PostPolicyResult generatePostPolicy(
            String endpoint,
            String bucket,
            String accessKeyId,
            String accessKeySecret,
            String dir,
            String objectKey,
            long maxSizeBytes,
            Date expireEndTime) {

        OSS ossClient = new OSSClientBuilder().build(endpoint, accessKeyId, accessKeySecret);
        try {
            PolicyConditions policyConditions = new PolicyConditions();
            policyConditions.addConditionItem(PolicyConditions.COND_CONTENT_LENGTH_RANGE, 0, maxSizeBytes);
            policyConditions.addConditionItem(MatchMode.StartWith, PolicyConditions.COND_KEY, dir);

            String postPolicy = ossClient.generatePostPolicy(expireEndTime, policyConditions);
            byte[] binaryData = postPolicy.getBytes(StandardCharsets.UTF_8);
            String encodedPolicy = BinaryUtil.toBase64String(binaryData);
            String signature = ossClient.calculatePostSignature(postPolicy);

            // 清洗 endpoint：去掉可能携带的协议前缀，兼容带/不带 https:// 的写法
            String cleanEndpoint = endpoint.replaceFirst("^https?://", "");
            String host = "https://" + bucket + "." + cleanEndpoint;

            return new PostPolicyResult(encodedPolicy, signature, host, dir, objectKey);
        } finally {
            ossClient.shutdown();
        }
    }
}
