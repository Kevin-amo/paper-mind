package com.lqr.papermind.document.web;

import com.lqr.papermind.document.dto.DocumentOssUploadCallbackRequest;
import com.lqr.papermind.document.dto.DocumentOssUploadCallbackResponse;
import com.lqr.papermind.document.service.DocumentOssUploadService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.stream.Collectors;

/**
 * OSS 上传回调接口。
 * 此接口由阿里云 OSS 服务器在文件直传完成后回调，
 * 不需要 JWT 认证（通过 OSS 回调签名验证保证安全）。
 */
@Slf4j
@RestController
@RequestMapping("/documents")
@RequiredArgsConstructor
public class DocumentOssUploadCallbackController {

    private final DocumentOssUploadService documentOssUploadService;

    /**
     * 接收 OSS 上传回调通知。
     *
     * <p>OSS 上传完成后，会根据 PostObject 中配置的 callback 参数，
     * 将文件元数据和自定义参数以 POST 请求（application/x-www-form-urlencoded）
     * 发送到此端点。</p>
     *
     * <p>安全机制：
     * 1. OSS 回调携带 Authorization 签名头
     * 2. 后端使用 AccessKeySecret 验证签名，防止伪造回调</p>
     */
    @PostMapping("/upload-callback")
    public ResponseEntity<DocumentOssUploadCallbackResponse> uploadCallback(HttpServletRequest httpRequest) throws IOException {
        String authorizationHeader = httpRequest.getHeader("Authorization");
        String uri = httpRequest.getRequestURI();

        // 读取原始请求体用于签名验证
        String requestBody;
        try (BufferedReader reader = httpRequest.getReader()) {
            requestBody = reader.lines().collect(Collectors.joining("\n"));
        }

        // 从 URL-encoded form 参数中解析回调数据
        DocumentOssUploadCallbackRequest callbackRequest = parseCallbackFromRequest(httpRequest, requestBody);

        log.info("oss.upload.callback.received objectKey={} authorization={}", callbackRequest.getObjectKey(),
                authorizationHeader != null ? "present" : "missing");

        try {
            DocumentOssUploadCallbackResponse response = documentOssUploadService.handleUploadCallback(
                    callbackRequest, authorizationHeader, requestBody, uri);
            return ResponseEntity.ok(response);
        } catch (Exception ex) {
            log.error("oss.upload.callback.failed objectKey={}", callbackRequest.getObjectKey(), ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * 从 HttpServletRequest 的参数中解析 OSS 回调数据。
     * OSS 回调以 application/x-www-form-urlencoded 格式发送参数。
     */
    private DocumentOssUploadCallbackRequest parseCallbackFromRequest(HttpServletRequest request, String requestBody) {
        DocumentOssUploadCallbackRequest callbackRequest = new DocumentOssUploadCallbackRequest();
        callbackRequest.setObjectKey(request.getParameter("objectKey"));
        callbackRequest.setBucket(request.getParameter("bucket"));
        callbackRequest.setEtag(request.getParameter("etag"));
        callbackRequest.setSize(request.getParameter("size"));
        callbackRequest.setMimeType(request.getParameter("mimeType"));
        callbackRequest.setSourceId(request.getParameter("sourceId"));
        callbackRequest.setTitle(request.getParameter("title"));
        callbackRequest.setFileName(request.getParameter("fileName"));
        callbackRequest.setOwnerUserId(request.getParameter("ownerUserId"));
        callbackRequest.setContentType(request.getParameter("contentType"));
        callbackRequest.setFileSize(request.getParameter("fileSize"));
        callbackRequest.setSourceType(request.getParameter("sourceType"));
        return callbackRequest;
    }
}
