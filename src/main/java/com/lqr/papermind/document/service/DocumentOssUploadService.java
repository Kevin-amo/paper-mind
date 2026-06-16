package com.lqr.papermind.document.service;

import com.lqr.papermind.document.dto.DocumentOssUploadCallbackRequest;
import com.lqr.papermind.document.dto.DocumentOssUploadCallbackResponse;
import com.lqr.papermind.document.dto.DocumentOssUploadPolicyRequest;
import com.lqr.papermind.document.dto.DocumentOssUploadPolicyResponse;

import java.util.UUID;

/**
 * 论文上传 OSS 直传服务。
 * 负责生成上传凭证、验证回调签名并处理回调以创建入库任务。
 */
public interface DocumentOssUploadService {

    /**
     * 为指定用户生成 OSS PostObject 直传凭证。
     *
     * @param ownerUserId 文档所属用户 ID
     * @param request 上传凭证请求参数
     * @return 前端直传 OSS 所需的凭证信息
     */
    DocumentOssUploadPolicyResponse generateUploadPolicy(UUID ownerUserId, DocumentOssUploadPolicyRequest request);

    /**
     * 处理 OSS 上传回调，验证签名后创建入库任务并投递 MQ 消息。
     *
     * @param callbackRequest 回调请求参数
     * @param authorizationHeader OSS 回调签名头
     * @param requestBody 原始请求体字符串（用于签名验证）
     * @param uri 回调请求 URI（用于签名验证）
     * @return 回调处理结果
     */
    DocumentOssUploadCallbackResponse handleUploadCallback(
            DocumentOssUploadCallbackRequest callbackRequest,
            String authorizationHeader,
            String requestBody,
            String uri);
}
