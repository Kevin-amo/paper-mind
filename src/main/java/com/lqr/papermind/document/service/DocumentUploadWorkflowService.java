package com.lqr.papermind.document.service;

import com.lqr.papermind.document.entity.DocumentIngestionJob;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.UUID;

/**
 * 文档上传工作流服务接口。
 * <p>定义文档上传的完整工作流：文件存储 → 任务创建 → 消息发布。</p>
 */
public interface DocumentUploadWorkflowService {

    /**
     * 创建文档摄入任务并发布消息。
     *
     * @param ownerUserId      文档所属用户ID
     * @param file             上传的文件
     * @param sourceId         来源标识，为空时自动生成UUID
     * @param title            文档标题
     * @param fallbackFileName 备选文件名，为空时使用上传文件的原始文件名
     * @param sourceType       来源类型，为空时默认为"USER"
     * @return 创建完成的文档摄入任务
     * @throws IOException 文件存储过程中发生IO异常
     */
    DocumentIngestionJob createAndPublishJob(UUID ownerUserId,
                                             MultipartFile file,
                                             String sourceId,
                                             String title,
                                             String fallbackFileName,
                                             String sourceType) throws IOException;
}
