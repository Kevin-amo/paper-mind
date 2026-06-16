package com.lqr.papermind.document.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;

import java.util.List;

/**
 * 论文上传 OSS 直传配置，独立于头像 OSS 配置。
 */
@Data
@ConfigurationProperties(prefix = "app.storage.oss.document")
public class DocumentOssUploadProperties {

    /** OSS Endpoint，例如 oss-cn-hangzhou.aliyuncs.com */
    private String endpoint;

    /** OSS Bucket 名称 */
    private String bucket;

    /** OSS AccessKey ID */
    private String accessKeyId;

    /** OSS AccessKey Secret */
    private String accessKeySecret;

    /** 回调通知 URL，后端接收 OSS 回调的公网地址 */
    private String callbackUrl;

    /** 上传目录前缀，例如 documents */
    private String uploadPrefix = "documents";

    /** 上传文件最大大小 */
    private DataSize maxSize = DataSize.ofMegabytes(50);

    /** 允许的文件 MIME 类型 */
    private List<String> allowedContentTypes = List.of(
            "application/pdf",
            "application/msword",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
    );

    /** 上传凭证有效期（秒），默认 1800（30 分钟） */
    private long policyExpireSeconds = 1800;

    /**
     * 判断论文 OSS 配置是否完整。
     */
    public boolean isConfigured() {
        return hasText(endpoint) && hasText(bucket)
                && hasText(accessKeyId) && hasText(accessKeySecret);
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
