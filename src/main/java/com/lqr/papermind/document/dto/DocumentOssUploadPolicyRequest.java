package com.lqr.papermind.document.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 论文上传 OSS 直传凭证请求。
 */
@Data
public class DocumentOssUploadPolicyRequest {

    /** 文件名，例如 paper.pdf */
    @NotBlank(message = "文件名不能为空")
    @Size(max = 255, message = "文件名过长")
    private String fileName;

    /** 文件 MIME 类型，例如 application/pdf */
    @NotBlank(message = "文件类型不能为空")
    private String contentType;

    /** 文件大小（字节） */
    @Positive(message = "文件大小必须大于 0")
    private Long fileSize;

    /** 文档来源标识，可选 */
    @Size(max = 128, message = "来源标识过长")
    private String sourceId;

    /** 文档标题，可选 */
    @Size(max = 512, message = "标题过长")
    private String title;

    /** 来源类型：USER 或 REVIEW，可选。默认 USER */
    @Size(max = 32, message = "来源类型过长")
    private String sourceType;
}
