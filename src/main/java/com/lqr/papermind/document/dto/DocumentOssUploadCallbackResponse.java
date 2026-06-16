package com.lqr.papermind.document.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * OSS 上传回调响应，返回给 OSS 服务器。
 */
@Data
@AllArgsConstructor
public class DocumentOssUploadCallbackResponse {

    /** 任务 ID */
    private String jobId;

    /** 文档来源标识 */
    private String sourceId;

    /** 任务状态 */
    private String status;

    /** 提示消息 */
    private String message;

    public static DocumentOssUploadCallbackResponse success(String jobId, String sourceId) {
        return new DocumentOssUploadCallbackResponse(jobId, sourceId, "PENDING", "文档已进入异步入库队列");
    }
}
