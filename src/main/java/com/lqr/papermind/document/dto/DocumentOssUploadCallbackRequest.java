package com.lqr.papermind.document.dto;

import lombok.Data;

/**
 * OSS 上传回调请求，由阿里云 OSS 服务器发起。
 *
 * <p>OSS 回调会将自定义参数和系统参数合并 POST 到回调地址。
 * 自定义参数通过 callbackBody 中的变量传递，系统参数由 OSS 自动填充。</p>
 */
@Data
public class DocumentOssUploadCallbackRequest {

    /** OSS 对象键 */
    private String objectKey;

    /** OSS Bucket 名称 */
    private String bucket;

    /** 对象 ETag */
    private String etag;

    /** 文件大小（字节） */
    private String size;

    /** 文件 MIME 类型 */
    private String mimeType;

    /** 用户自定义参数：来源标识 */
    private String sourceId;

    /** 用户自定义参数：文档标题 */
    private String title;

    /** 用户自定义参数：原始文件名 */
    private String fileName;

    /** 用户自定义参数：所属用户 ID */
    private String ownerUserId;

    /** 用户自定义参数：文件内容类型 */
    private String contentType;

    /** 用户自定义参数：文件大小 */
    private String fileSize;

    /** 用户自定义参数：来源类型（USER 或 REVIEW） */
    private String sourceType;
}
