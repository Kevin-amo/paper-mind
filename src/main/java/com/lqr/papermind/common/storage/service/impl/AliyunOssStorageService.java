package com.lqr.papermind.common.storage.service.impl;

import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.aliyun.oss.model.ObjectMetadata;
import com.lqr.papermind.common.storage.config.OssProperties;
import com.lqr.papermind.common.storage.service.ObjectStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * 阿里云 OSS 对象存储实现。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AliyunOssStorageService implements ObjectStorageService {

    private final OssProperties properties;

    /**
     * 上传对象到阿里云 OSS。
     *
     * @param objectKey    对象键
     * @param inputStream  文件输入流
     * @param contentLength 文件内容长度（字节）
     * @param contentType  文件内容类型
     */
    @Override
    public void putObject(String objectKey, InputStream inputStream, long contentLength, String contentType) {
        ensureConfigured();
        OSS ossClient = createClient();
        try {
            ObjectMetadata metadata = new ObjectMetadata();
            metadata.setContentLength(contentLength);
            metadata.setContentType(contentType);
            ossClient.putObject(properties.bucket(), objectKey, inputStream, metadata);
        } finally {
            ossClient.shutdown();
        }
    }

    /**
     * 删除阿里云 OSS 上的指定对象。
     *
     * @param objectKey 对象键
     */
    @Override
    public void deleteObject(String objectKey) {
        if (objectKey == null || objectKey.isBlank()) {
            return;
        }
        ensureConfigured();
        OSS ossClient = createClient();
        try {
            ossClient.deleteObject(properties.bucket(), objectKey);
        } finally {
            ossClient.shutdown();
        }
    }

    /**
     * 拼接对象的公共访问地址。
     *
     * @param objectKey 对象键
     * @return 公共访问 URL，未配置基础地址时返回对象键本身
     */
    @Override
    public String publicUrl(String objectKey) {
        if (objectKey == null || objectKey.isBlank()) {
            return null;
        }
        String baseUrl = properties.normalizedPublicBaseUrl();
        if (baseUrl.isBlank()) {
            return objectKey;
        }
        return baseUrl + "/" + objectKey.replaceAll("^/+", "");
    }

    /**
     * 从阿里云 OSS 读取指定对象的全部字节内容。
     *
     * @param objectKey 对象键
     * @return 对象的二进制内容
     * @throws IOException 对象读取失败时抛出
     */
    @Override
    public byte[] getObjectContent(String objectKey) throws IOException {
        if (objectKey == null || objectKey.isBlank()) {
            throw new IllegalArgumentException("对象键不能为空");
        }
        ensureConfigured();
        OSS ossClient = createClient();
        try {
            var ossObject = ossClient.getObject(properties.bucket(), objectKey);
            try (InputStream inputStream = ossObject.getObjectContent();
                 ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
                inputStream.transferTo(outputStream);
                return outputStream.toByteArray();
            }
        } catch (Exception ex) {
            log.error("oss.getObjectContent.failed bucket={} objectKey={}", properties.bucket(), objectKey, ex);
            throw new IOException("从 OSS 读取对象失败：" + objectKey, ex);
        } finally {
            ossClient.shutdown();
        }
    }

    /**
     * 创建阿里云 OSS 客户端实例。
     *
     * @return 已初始化的 OSS 客户端
     */
    private OSS createClient() {
        return new OSSClientBuilder().build(
                properties.endpoint(),
                properties.accessKeyId(),
                properties.accessKeySecret()
        );
    }

    /**
     * 校验 OSS 配置是否完整，配置不完整时抛出内部服务器错误异常。
     *
     * @throws ResponseStatusException OSS 配置不完整时抛出
     */
    private void ensureConfigured() {
        if (!properties.isConfigured()) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "OSS 配置不完整");
        }
    }
}