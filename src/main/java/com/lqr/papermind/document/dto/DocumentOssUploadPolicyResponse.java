package com.lqr.papermind.document.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 论文上传 OSS 直传凭证响应。
 */
@Data
@AllArgsConstructor
public class DocumentOssUploadPolicyResponse {

    /** OSS 访问 Key ID */
    private String accessKeyId;

    /** Base64 编码的 Policy */
    private String policy;

    /** Policy 签名 */
    private String signature;

    /** 上传目录前缀 */
    private String dir;

    /** OSS Bucket 外网访问地址，例如 https://bucket.oss-cn-hangzhou.aliyuncs.com */
    private String host;

    /** 凭证过期时间（秒） */
    private long expire;

    /** 前端直传时使用的完整 objectKey */
    private String objectKey;

    /** 回调成功后后端会创建的 sourceId */
    private String sourceId;

    /** 来源类型，透传给 OSS 回调的 x:sourceType 变量 */
    private String sourceType;

    /** OSS PostObject 回调参数，Base64 编码的 JSON（包含 callbackUrl/callbackBody/callbackBodyType） */
    private String callback;

    /** OSS PostObject 回调自定义变量，Base64 编码的 JSON（x:varName → value） */
    private String callbackVar;
}
