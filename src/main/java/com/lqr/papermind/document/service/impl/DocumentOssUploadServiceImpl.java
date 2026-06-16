package com.lqr.papermind.document.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lqr.papermind.common.constant.MetadataKeys;
import com.lqr.papermind.common.storage.service.OssPostPolicyService;
import com.lqr.papermind.document.config.DocumentOssUploadProperties;
import com.lqr.papermind.document.dto.DocumentOssUploadCallbackRequest;
import com.lqr.papermind.document.dto.DocumentOssUploadCallbackResponse;
import com.lqr.papermind.document.dto.DocumentOssUploadPolicyRequest;
import com.lqr.papermind.document.dto.DocumentOssUploadPolicyResponse;
import com.lqr.papermind.document.entity.DocumentIngestionJob;
import com.lqr.papermind.document.model.DocumentIngestionMessage;
import com.lqr.papermind.document.service.DocumentIngestionJobService;
import com.lqr.papermind.document.service.DocumentIngestionProducer;
import com.lqr.papermind.document.service.DocumentOssUploadService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 论文上传 OSS 直传服务实现。
 * 使用服务端签名 Post Policy 方案生成直传凭证（委托给 {@link OssPostPolicyService}），
 * 并通过回调机制在 OSS 上传完成后创建入库任务。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentOssUploadServiceImpl implements DocumentOssUploadService {

    private final DocumentOssUploadProperties properties;
    private final DocumentIngestionJobService documentIngestionJobService;
    private final DocumentIngestionProducer documentIngestionProducer;
    private final OssPostPolicyService ossPostPolicyService;
    private final ObjectMapper objectMapper;

    private static final String CALLBACK_BODY_FORMAT =
            "objectKey=${object}&bucket=${bucket}&etag=${etag}&size=${size}&mimeType=${mimeType}"
            + "&sourceId=${x:sourceId}&title=${x:title}&fileName=${x:fileName}"
            + "&ownerUserId=${x:ownerUserId}&contentType=${x:contentType}&fileSize=${x:fileSize}"
            + "&sourceType=${x:sourceType}";

    /**
     * 为指定用户生成 OSS PostObject 直传凭证。
     *
     * <p>使用服务端签名 Post Policy 方案：
     * 1. 构造用户专属目录前缀 documents/{ownerUserId}/
     * 2. 委托 OssPostPolicyService 生成带时效的 Policy 签名
     * 3. 返回前端直传所需的全部参数</p>
     */
    @Override
    public DocumentOssUploadPolicyResponse generateUploadPolicy(UUID ownerUserId, DocumentOssUploadPolicyRequest request) {
        ensureConfigured();

        // 校验文件类型
        if (!properties.getAllowedContentTypes().contains(request.getContentType())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "不支持的文件类型：" + request.getContentType() + "，仅支持：" + properties.getAllowedContentTypes());
        }

        // 校验文件大小
        if (request.getFileSize() != null && request.getFileSize() > properties.getMaxSize().toBytes()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "文件大小超出限制，最大允许 " + properties.getMaxSize().toMegabytes() + " MB");
        }

        // 确定来源标识
        String sourceId = request.getSourceId();
        if (sourceId == null || sourceId.isBlank()) {
            sourceId = UUID.randomUUID().toString();
        } else {
            sourceId = sourceId.trim();
        }

        // 构造上传目录：documents/{ownerUserId}/{sourceId}/
        String dir = properties.getUploadPrefix() + "/" + ownerUserId + "/" + sourceId + "/";
        // 生成唯一 objectKey，避免同名覆盖
        String objectKey = dir + UUID.randomUUID() + "-" + sanitizeFileName(request.getFileName());

        Date expireEndTime = new Date(System.currentTimeMillis() + properties.getPolicyExpireSeconds() * 1000);

        try {
            // 委托给 OssPostPolicyService 生成签名，不直接调用 OSS SDK
            OssPostPolicyService.PostPolicyResult policyResult = ossPostPolicyService.generatePostPolicy(
                    properties.getEndpoint(),
                    properties.getBucket(),
                    properties.getAccessKeyId(),
                    properties.getAccessKeySecret(),
                    dir,
                    objectKey,
                    properties.getMaxSize().toBytes(),
                    expireEndTime
            );

            // 确定 sourceType，默认 USER
            String sourceType = request.getSourceType();
            if (sourceType == null || sourceType.isBlank()) {
                sourceType = MetadataKeys.SOURCE_TYPE_USER;
            }

            // 确定 title：如果请求未提供，从 fileName 去除后缀派生
            String title = request.getTitle();
            if (title == null || title.isBlank()) {
                title = request.getFileName().replaceFirst("\\.[^.]+$", "");
            }

            // 校验回调地址
            String callbackUrl = properties.getCallbackUrl();
            if (callbackUrl == null || callbackUrl.isBlank()) {
                throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "论文上传 OSS 回调地址未配置");
            }

            // 生成 callback 参数：Base64(JSON)
            // callback 指定 OSS 上传完成后回调后端的 URL、回调体模板和回调体格式
            Map<String, String> callbackMap = new LinkedHashMap<>();
            callbackMap.put("callbackUrl", callbackUrl);
            callbackMap.put("callbackBody", CALLBACK_BODY_FORMAT);
            callbackMap.put("callbackBodyType", "application/x-www-form-urlencoded");
            String callbackJson = objectMapper.writeValueAsString(callbackMap);
            String callback = Base64.getEncoder().encodeToString(
                    callbackJson.getBytes(StandardCharsets.UTF_8));

            // 生成 callback-var 参数：Base64(JSON)
            // callback-var 提供自定义变量（x:varName）的具体值，供 OSS 在回调体模板中替换
            // 所有自定义变量由服务端确定，防止前端篡改
            Map<String, String> callbackVarMap = new LinkedHashMap<>();
            callbackVarMap.put("x:ownerUserId", ownerUserId.toString());
            callbackVarMap.put("x:sourceId", sourceId);
            callbackVarMap.put("x:sourceType", sourceType);
            callbackVarMap.put("x:fileName", request.getFileName());
            callbackVarMap.put("x:title", title);
            callbackVarMap.put("x:contentType", request.getContentType());
            if (request.getFileSize() != null) {
                callbackVarMap.put("x:fileSize", String.valueOf(request.getFileSize()));
            }
            String callbackVarJson = objectMapper.writeValueAsString(callbackVarMap);
            String callbackVar = Base64.getEncoder().encodeToString(
                    callbackVarJson.getBytes(StandardCharsets.UTF_8));

            return new DocumentOssUploadPolicyResponse(
                    properties.getAccessKeyId(),
                    policyResult.encodedPolicy(),
                    policyResult.signature(),
                    policyResult.dir(),
                    policyResult.host(),
                    properties.getPolicyExpireSeconds(),
                    policyResult.objectKey(),
                    sourceId,
                    sourceType,
                    callback,
                    callbackVar
            );
        } catch (Exception ex) {
            log.error("oss.upload.policy.failed ownerUserId={} fileName={}", ownerUserId, request.getFileName(), ex);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "生成上传凭证失败", ex);
        }
    }

    /**
     * 处理 OSS 上传回调。
     *
     * <p>流程：
     * 1. 验证回调签名（防止伪造）
     * 2. 从回调参数中提取文件元数据和用户信息
     * 3. 创建 document_ingestion_job（标记来源为 OSS）
     * 4. 投递 MQ 消息触发后续入库流程</p>
     */
    @Override
    public DocumentOssUploadCallbackResponse handleUploadCallback(
            DocumentOssUploadCallbackRequest callbackRequest,
            String authorizationHeader,
            String requestBody,
            String uri) {
        ensureConfigured();

        // 验证回调签名
        if (!verifyCallbackSignature(authorizationHeader, uri, requestBody)) {
            log.warn("oss.upload.callback.signature.invalid objectKey={}", callbackRequest.getObjectKey());
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "回调签名验证失败");
        }

        log.info("oss.upload.callback.start objectKey={} bucket={} size={} mimeType={} sourceId={} ownerUserId={}",
                callbackRequest.getObjectKey(), callbackRequest.getBucket(),
                callbackRequest.getSize(), callbackRequest.getMimeType(),
                callbackRequest.getSourceId(), callbackRequest.getOwnerUserId());

        try {
            UUID ownerUserId = UUID.fromString(callbackRequest.getOwnerUserId());
            String sourceId = callbackRequest.getSourceId();
            String fileName = callbackRequest.getFileName();
            String objectKey = callbackRequest.getObjectKey();
            String bucket = callbackRequest.getBucket();
            String etag = callbackRequest.getEtag();
            String contentType = callbackRequest.getContentType();
            Long fileSize = parseLong(callbackRequest.getFileSize());

            // 确定来源标识：如果回调中没有，则自动生成
            if (sourceId == null || sourceId.isBlank()) {
                sourceId = UUID.randomUUID().toString();
            }

            // 确定 sourceType：优先使用回调传递的值，否则默认 USER
            String sourceType = callbackRequest.getSourceType();
            if (sourceType == null || sourceType.isBlank()) {
                sourceType = MetadataKeys.SOURCE_TYPE_USER;
            }

            // 创建入库任务，标记文件来源为 OSS
            UUID jobId = UUID.randomUUID();
            Map<String, Object> extraMetadata = new LinkedHashMap<>();
            extraMetadata.put(MetadataKeys.SOURCE_TYPE, sourceType);
            extraMetadata.put("storageProvider", "OSS");
            extraMetadata.put("objectKey", objectKey);
            extraMetadata.put("bucket", bucket);
            extraMetadata.put("etag", etag);
            extraMetadata.put(MetadataKeys.CONTENT_TYPE, contentType);
            if (fileSize != null) {
                extraMetadata.put(MetadataKeys.CONTENT_LENGTH, fileSize);
            }

            // filePath 存储 OSS objectKey，方便后续 Consumer 判断来源
            // job 的 storageProvider 字段标识来源为 OSS，Consumer 根据此字段选择读取策略
            DocumentIngestionJob job = documentIngestionJobService.createJob(
                    jobId,
                    ownerUserId,
                    sourceId,
                    fileName,
                    objectKey,  // filePath 存储 objectKey
                    callbackRequest.getTitle(),
                    extraMetadata
            );

            // 投递 MQ 消息
            documentIngestionProducer.publish(new DocumentIngestionMessage(job.getId(), ownerUserId, sourceId));

            log.info("oss.upload.callback.done ownerUserId={} jobId={} sourceId={} objectKey={} fileName={}",
                    ownerUserId, job.getId(), sourceId, objectKey, fileName);

            return DocumentOssUploadCallbackResponse.success(job.getId().toString(), sourceId);
        } catch (Exception ex) {
            log.error("oss.upload.callback.failed objectKey={} sourceId={}", callbackRequest.getObjectKey(), callbackRequest.getSourceId(), ex);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "回调处理失败", ex);
        }
    }

    /**
     * 验证 OSS 回调签名。
     *
     * <p>阿里云 OSS 回调签名格式：
     * Authorization = "OSS {AccessKeyId}:{Signature}"
     * Signature = Base64(HMAC-SHA1(AccessKeySecret, URI + "\n" + Body))</p>
     *
     * <p>签名验证使用标准 javax.crypto 包，不依赖 OSS SDK。</p>
     */
    private boolean verifyCallbackSignature(String authorizationHeader, String uri, String requestBody) {
        if (authorizationHeader == null || authorizationHeader.isBlank()) {
            return false;
        }

        try {
            // 解析 Authorization 头：格式 "OSS {AccessKeyId}:{Signature}"
            String[] parts = authorizationHeader.split(" ");
            if (parts.length != 2) {
                return false;
            }
            String scheme = parts[0];
            if (!"OSS".equalsIgnoreCase(scheme)) {
                return false;
            }
            String[] credParts = parts[1].split(":");
            if (credParts.length != 2) {
                return false;
            }
            String accessKeyId = credParts[0];
            String signature = credParts[1];

            // 验证 AccessKey ID 是否匹配
            if (!properties.getAccessKeyId().equals(accessKeyId)) {
                return false;
            }

            // 计算期望签名：HMAC-SHA1，不依赖 OSS SDK
            String stringToSign = uri + "\n" + requestBody;
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(
                    properties.getAccessKeySecret().getBytes(StandardCharsets.UTF_8), "HmacSHA1"));
            byte[] signData = mac.doFinal(stringToSign.getBytes(StandardCharsets.UTF_8));
            String expectedSignature = Base64.getEncoder().encodeToString(signData);

            return expectedSignature.equals(signature);
        } catch (Exception ex) {
            log.warn("oss.callback.signature.verify.error", ex);
            return false;
        }
    }

    /**
     * 清理文件名中的不安全字符。
     */
    private String sanitizeFileName(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return "upload.bin";
        }
        String cleaned = fileName.replace('\\', '/');
        int slashIndex = cleaned.lastIndexOf('/');
        String baseName = slashIndex >= 0 ? cleaned.substring(slashIndex + 1) : cleaned;
        baseName = baseName.replaceAll("[\\p{Cntrl}]", "")
                .replaceAll("[\\/]+", "-")
                .replace("..", "")
                .trim();
        return baseName.isBlank() ? "upload.bin" : baseName;
    }

    /**
     * 安全解析 Long 值。
     */
    private Long parseLong(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    /**
     * 校验配置是否完整。
     */
    private void ensureConfigured() {
        if (!properties.isConfigured()) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "论文上传 OSS 配置不完整");
        }
    }
}
