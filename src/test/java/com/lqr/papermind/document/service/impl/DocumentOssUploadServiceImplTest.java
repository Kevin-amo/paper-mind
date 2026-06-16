package com.lqr.papermind.document.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lqr.papermind.common.storage.service.OssPostPolicyService;
import com.lqr.papermind.document.config.DocumentOssUploadProperties;
import com.lqr.papermind.document.dto.DocumentOssUploadCallbackRequest;
import com.lqr.papermind.document.dto.DocumentOssUploadCallbackResponse;
import com.lqr.papermind.document.dto.DocumentOssUploadPolicyRequest;
import com.lqr.papermind.document.dto.DocumentOssUploadPolicyResponse;
import com.lqr.papermind.document.entity.DocumentIngestionJob;
import com.lqr.papermind.document.service.DocumentIngestionJobService;
import com.lqr.papermind.document.service.DocumentIngestionProducer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.Date;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 论文上传 OSS 直传服务测试。
 * 业务类不再直接依赖 OSS SDK，凭证生成委托给 OssPostPolicyService，
 * 回调签名验证使用 javax.crypto 包。
 */
class DocumentOssUploadServiceImplTest {

    private DocumentOssUploadServiceImpl service;
    private DocumentOssUploadProperties properties;
    private DocumentIngestionJobService jobService;
    private DocumentIngestionProducer producer;
    private OssPostPolicyService ossPostPolicyService;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        properties = new DocumentOssUploadProperties();
        properties.setEndpoint("oss-cn-hangzhou.aliyuncs.com");
        properties.setBucket("test-bucket");
        properties.setAccessKeyId("test-access-key-id");
        properties.setAccessKeySecret("test-access-key-secret");
        properties.setCallbackUrl("https://example.com/api/documents/upload-callback");
        properties.setUploadPrefix("documents");

        jobService = mock(DocumentIngestionJobService.class);
        producer = mock(DocumentIngestionProducer.class);
        ossPostPolicyService = mock(OssPostPolicyService.class);
        objectMapper = new ObjectMapper();

        service = new DocumentOssUploadServiceImpl(properties, jobService, producer, ossPostPolicyService, objectMapper);
    }

    @Test
    void generateUploadPolicy_shouldRejectUnsupportedContentType() {
        DocumentOssUploadPolicyRequest request = new DocumentOssUploadPolicyRequest();
        request.setFileName("test.exe");
        request.setContentType("application/x-msdownload");
        request.setFileSize(1024L);

        UUID userId = UUID.randomUUID();
        try {
            service.generateUploadPolicy(userId, request);
        } catch (ResponseStatusException ex) {
            assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            return;
        }
        assertThat(true).isFalse(); // Should have thrown
    }

    @Test
    void generateUploadPolicy_shouldRejectExceedingFileSize() {
        DocumentOssUploadPolicyRequest request = new DocumentOssUploadPolicyRequest();
        request.setFileName("large.pdf");
        request.setContentType("application/pdf");
        request.setFileSize(100L * 1024 * 1024); // 100 MB, exceeds 50 MB limit

        UUID userId = UUID.randomUUID();
        try {
            service.generateUploadPolicy(userId, request);
        } catch (ResponseStatusException ex) {
            assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            return;
        }
        assertThat(true).isFalse(); // Should have thrown
    }

    @Test
    void generateUploadPolicy_shouldDelegateToOssPostPolicyService() {
        UUID userId = UUID.randomUUID();
        DocumentOssUploadPolicyRequest request = new DocumentOssUploadPolicyRequest();
        request.setFileName("test.pdf");
        request.setContentType("application/pdf");
        request.setFileSize(1024L);
        request.setSourceId("src-1");

        // Mock OssPostPolicyService 返回签名结果
        OssPostPolicyService.PostPolicyResult mockResult = new OssPostPolicyService.PostPolicyResult(
                "encodedPolicy123", "signature456",
                "https://test-bucket.oss-cn-hangzhou.aliyuncs.com",
                "documents/" + userId + "/src-1/",
                "documents/" + userId + "/src-1/uuid-test.pdf"
        );
        when(ossPostPolicyService.generatePostPolicy(
                eq(properties.getEndpoint()),
                eq(properties.getBucket()),
                eq(properties.getAccessKeyId()),
                eq(properties.getAccessKeySecret()),
                any(String.class),
                any(String.class),
                eq(properties.getMaxSize().toBytes()),
                any(Date.class)
        )).thenReturn(mockResult);

        DocumentOssUploadPolicyResponse response = service.generateUploadPolicy(userId, request);

        assertThat(response).isNotNull();
        assertThat(response.getAccessKeyId()).isEqualTo(properties.getAccessKeyId());
        assertThat(response.getPolicy()).isEqualTo("encodedPolicy123");
        assertThat(response.getSignature()).isEqualTo("signature456");
        assertThat(response.getHost()).isEqualTo("https://test-bucket.oss-cn-hangzhou.aliyuncs.com");
        assertThat(response.getSourceId()).isEqualTo("src-1");
        assertThat(response.getSourceType()).isEqualTo("USER"); // 默认值
        assertThat(response.getCallback()).isNotBlank(); // Base64 编码的 callback JSON
        assertThat(response.getCallbackVar()).isNotBlank(); // Base64 编码的 callback-var JSON

        verify(ossPostPolicyService).generatePostPolicy(
                eq(properties.getEndpoint()),
                eq(properties.getBucket()),
                eq(properties.getAccessKeyId()),
                eq(properties.getAccessKeySecret()),
                any(String.class),
                any(String.class),
                eq(properties.getMaxSize().toBytes()),
                any(Date.class)
        );
    }

    @Test
    void generateUploadPolicy_shouldAutoGenerateSourceIdWhenBlank() {
        UUID userId = UUID.randomUUID();
        DocumentOssUploadPolicyRequest request = new DocumentOssUploadPolicyRequest();
        request.setFileName("test.pdf");
        request.setContentType("application/pdf");
        request.setFileSize(1024L);
        request.setSourceId(null);  // 没有提供 sourceId

        OssPostPolicyService.PostPolicyResult mockResult = new OssPostPolicyService.PostPolicyResult(
                "encodedPolicy", "signature",
                "https://test-bucket.oss-cn-hangzhou.aliyuncs.com",
                "documents/" + userId + "/",
                "documents/" + userId + "/uuid-test.pdf"
        );
        when(ossPostPolicyService.generatePostPolicy(any(), any(), any(), any(), any(), any(), anyLong(), any(Date.class)))
                .thenReturn(mockResult);

        DocumentOssUploadPolicyResponse response = service.generateUploadPolicy(userId, request);

        assertThat(response).isNotNull();
        assertThat(response.getSourceId()).isNotBlank(); // 自动生成
    }

    @Test
    void generateUploadPolicy_shouldPassThroughSourceTypeReview() {
        UUID userId = UUID.randomUUID();
        DocumentOssUploadPolicyRequest request = new DocumentOssUploadPolicyRequest();
        request.setFileName("review.pdf");
        request.setContentType("application/pdf");
        request.setFileSize(1024L);
        request.setSourceId("review-src-1");
        request.setSourceType("REVIEW");

        OssPostPolicyService.PostPolicyResult mockResult = new OssPostPolicyService.PostPolicyResult(
                "encodedPolicy", "signature",
                "https://test-bucket.oss-cn-hangzhou.aliyuncs.com",
                "documents/" + userId + "/review-src-1/",
                "documents/" + userId + "/review-src-1/uuid-review.pdf"
        );
        when(ossPostPolicyService.generatePostPolicy(
                eq(properties.getEndpoint()),
                eq(properties.getBucket()),
                eq(properties.getAccessKeyId()),
                eq(properties.getAccessKeySecret()),
                any(String.class),
                any(String.class),
                eq(properties.getMaxSize().toBytes()),
                any(Date.class)
        )).thenReturn(mockResult);

        DocumentOssUploadPolicyResponse response = service.generateUploadPolicy(userId, request);

        assertThat(response).isNotNull();
        assertThat(response.getSourceType()).isEqualTo("REVIEW");
        assertThat(response.getSourceId()).isEqualTo("review-src-1");
    }

    @Test
    void handleUploadCallback_shouldRejectInvalidSignature() {
        DocumentOssUploadCallbackRequest request = new DocumentOssUploadCallbackRequest();
        request.setObjectKey("documents/test/test.pdf");
        request.setBucket("test-bucket");
        request.setSourceId("test-source-id");
        request.setOwnerUserId(UUID.randomUUID().toString());
        request.setFileName("test.pdf");

        try {
            service.handleUploadCallback(request, null, "body", "/documents/upload-callback");
        } catch (ResponseStatusException ex) {
            assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
            return;
        }
        assertThat(true).isFalse(); // Should have thrown
    }

    @Test
    void handleUploadCallback_shouldUseSourceTypeFromCallback() {
        UUID ownerUserId = UUID.randomUUID();
        String sourceId = "review-source-id";

        DocumentIngestionJob createdJob = new DocumentIngestionJob();
        createdJob.setId(UUID.randomUUID());
        createdJob.setOwnerUserId(ownerUserId);
        createdJob.setSourceId(sourceId);
        createdJob.setStorageProvider("OSS");

        when(jobService.createJob(any(), any(), any(), any(), any(), any(), any())).thenReturn(createdJob);

        DocumentOssUploadCallbackRequest callbackRequest = new DocumentOssUploadCallbackRequest();
        callbackRequest.setObjectKey("documents/" + ownerUserId + "/" + sourceId + "/review.pdf");
        callbackRequest.setBucket("test-bucket");
        callbackRequest.setEtag("abc123");
        callbackRequest.setSize("2048");
        callbackRequest.setMimeType("application/pdf");
        callbackRequest.setSourceId(sourceId);
        callbackRequest.setTitle("Review Paper");
        callbackRequest.setFileName("review.pdf");
        callbackRequest.setOwnerUserId(ownerUserId.toString());
        callbackRequest.setContentType("application/pdf");
        callbackRequest.setFileSize("2048");
        callbackRequest.setSourceType("REVIEW");

        // 构造有效签名
        String requestBody = "objectKey=documents/test/review.pdf&bucket=test-bucket";
        String uri = "/documents/upload-callback";
        String validAuth = computeValidAuth(uri, requestBody);

        DocumentOssUploadCallbackResponse response = service.handleUploadCallback(callbackRequest, validAuth, requestBody, uri);

        assertThat(response).isNotNull();
        assertThat(response.getSourceId()).isEqualTo(sourceId);
        assertThat(response.getStatus()).isEqualTo("PENDING");

        // 验证 createJob 被调用，sourceType=REVIEW 传入 metadata
        verify(jobService).createJob(any(), eq(ownerUserId), eq(sourceId), any(), any(), any(), any());
        verify(producer).publish(any());
    }

    @Test
    void handleUploadCallback_shouldCreateJobAndPublishMessage_whenSignatureValid() {
        // 准备数据
        UUID ownerUserId = UUID.randomUUID();
        String sourceId = "test-source-id";

        DocumentIngestionJob createdJob = new DocumentIngestionJob();
        createdJob.setId(UUID.randomUUID());
        createdJob.setOwnerUserId(ownerUserId);
        createdJob.setSourceId(sourceId);
        createdJob.setStorageProvider("OSS");
        createdJob.setObjectKey("documents/" + ownerUserId + "/" + sourceId + "/test.pdf");
        createdJob.setBucketName("test-bucket");

        when(jobService.createJob(any(), any(), any(), any(), any(), any(), any())).thenReturn(createdJob);

        DocumentOssUploadCallbackRequest callbackRequest = new DocumentOssUploadCallbackRequest();
        callbackRequest.setObjectKey("documents/" + ownerUserId + "/" + sourceId + "/test.pdf");
        callbackRequest.setBucket("test-bucket");
        callbackRequest.setEtag("abc123");
        callbackRequest.setSize("1024");
        callbackRequest.setMimeType("application/pdf");
        callbackRequest.setSourceId(sourceId);
        callbackRequest.setTitle("Test Paper");
        callbackRequest.setFileName("test.pdf");
        callbackRequest.setOwnerUserId(ownerUserId.toString());
        callbackRequest.setContentType("application/pdf");
        callbackRequest.setFileSize("1024");

        // 构造有效的签名
        String requestBody = "objectKey=documents/test/test.pdf&bucket=test-bucket";
        String uri = "/documents/upload-callback";
        String validAuth = computeValidAuth(uri, requestBody);

        DocumentOssUploadCallbackResponse response = service.handleUploadCallback(callbackRequest, validAuth, requestBody, uri);

        assertThat(response).isNotNull();
        assertThat(response.getSourceId()).isEqualTo(sourceId);
        assertThat(response.getStatus()).isEqualTo("PENDING");

        verify(jobService).createJob(any(), eq(ownerUserId), eq(sourceId), any(), any(), any(), any());
        verify(producer).publish(any());
    }

    @Test
    void handleUploadCallback_shouldRejectWrongAccessKeyIdInSignature() {
        UUID ownerUserId = UUID.randomUUID();

        DocumentOssUploadCallbackRequest callbackRequest = new DocumentOssUploadCallbackRequest();
        callbackRequest.setObjectKey("documents/test.pdf");
        callbackRequest.setBucket("test-bucket");
        callbackRequest.setSourceId("src-1");
        callbackRequest.setOwnerUserId(ownerUserId.toString());
        callbackRequest.setFileName("test.pdf");

        String requestBody = "objectKey=documents/test.pdf&bucket=test-bucket";
        String uri = "/documents/upload-callback";

        // 用错误的 AccessKeyId 构造签名
        String wrongKeyId = "wrong-key-id";
        String validSignature = computeSignature(uri, requestBody);
        String invalidAuth = "OSS " + wrongKeyId + ":" + validSignature;

        try {
            service.handleUploadCallback(callbackRequest, invalidAuth, requestBody, uri);
        } catch (ResponseStatusException ex) {
            assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
            return;
        }
        assertThat(true).isFalse(); // Should have thrown
    }

    @Test
    void isConfigured_shouldReturnFalse_whenPropertiesIncomplete() {
        DocumentOssUploadProperties incomplete = new DocumentOssUploadProperties();
        assertThat(incomplete.isConfigured()).isFalse();
    }

    @Test
    void isConfigured_shouldReturnTrue_whenAllPropertiesSet() {
        assertThat(properties.isConfigured()).isTrue();
    }

    /**
     * 计算与阿里云 OSS 一致的 HMAC-SHA1 签名，构造 Authorization 头。
     */
    private String computeValidAuth(String uri, String body) {
        String signature = computeSignature(uri, body);
        return "OSS " + properties.getAccessKeyId() + ":" + signature;
    }

    /**
     * 计算 HMAC-SHA1 签名值。
     */
    private String computeSignature(String uri, String body) {
        try {
            String stringToSign = uri + "\n" + body;
            javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA1");
            mac.init(new javax.crypto.spec.SecretKeySpec(
                    properties.getAccessKeySecret().getBytes(java.nio.charset.StandardCharsets.UTF_8), "HmacSHA1"));
            byte[] signData = mac.doFinal(stringToSign.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return java.util.Base64.getEncoder().encodeToString(signData);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
