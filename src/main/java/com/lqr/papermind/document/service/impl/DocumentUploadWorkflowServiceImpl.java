package com.lqr.papermind.document.service.impl;

import com.lqr.papermind.common.constant.MetadataKeys;
import com.lqr.papermind.document.entity.DocumentIngestionJob;
import com.lqr.papermind.document.model.DocumentIngestionMessage;
import com.lqr.papermind.document.service.DocumentIngestionJobService;
import com.lqr.papermind.document.service.DocumentIngestionProducer;
import com.lqr.papermind.document.service.DocumentUploadStorageService;
import com.lqr.papermind.document.service.DocumentUploadWorkflowService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;

/**
 * 文档上传工作流实现类。
 * 负责协调文件存储、摄入任务创建与消息发布等完整上传流程。
 */
@Service
@RequiredArgsConstructor
public class DocumentUploadWorkflowServiceImpl implements DocumentUploadWorkflowService {

    /** 文件存储服务，负责将上传文件持久化到存储层 */
    private final DocumentUploadStorageService documentUploadStorageService;
    /** 文档摄入任务服务，负责创建和管理摄入任务记录 */
    private final DocumentIngestionJobService documentIngestionJobService;
    /** 文档摄入消息生产者，负责将任务消息发布到消息队列 */
    private final DocumentIngestionProducer documentIngestionProducer;

    /**
     * 创建文档摄入任务并发布消息。
     * <p>完整流程：生成任务ID → 确定来源标识 → 确定文件名 → 存储文件 → 创建摄入任务记录 → 发布消息至队列。</p>
     *
     * @param ownerUserId      文档所属用户ID
     * @param file             上传的文件
     * @param sourceId         来源标识，为空时自动生成UUID
     * @param title            文档标题
     * @param fallbackFileName 备选文件名，为空时使用原始文件名
     * @param sourceType       来源类型，为空时默认为"USER"
     * @return 创建完成的文档摄入任务
     * @throws IOException 文件存储过程中发生IO异常
     */
    @Override
    public DocumentIngestionJob createAndPublishJob(UUID ownerUserId,
                                                    MultipartFile file,
                                                    String sourceId,
                                                    String title,
                                                    String fallbackFileName,
                                                    String sourceType) throws IOException {
        // 生成唯一任务ID
        UUID jobId = UUID.randomUUID();
        // 确定来源ID：若未提供则自动生成UUID
        String resolvedSourceId = sourceId == null || sourceId.isBlank() ? UUID.randomUUID().toString() : sourceId.trim();
        // 确定文件名：优先使用备选文件名，其次使用上传文件的原始文件名
        String fileName = fallbackFileName == null || fallbackFileName.isBlank() ? file.getOriginalFilename() : fallbackFileName;
        // 将文件存储到本地，获取存储结果
        DocumentUploadStorageService.StoredUpload upload = documentUploadStorageService.store(
                ownerUserId,
                resolvedSourceId,
                jobId,
                file,
                fileName
        );
        // 创建文档摄入任务记录，包含存储路径、文件名及元数据（来源类型）
        DocumentIngestionJob job = documentIngestionJobService.createJob(
                jobId,
                ownerUserId,
                resolvedSourceId,
                upload.fileName(),
                upload.filePath(),
                title,
                Map.of(MetadataKeys.SOURCE_TYPE, sourceType == null || sourceType.isBlank() ? MetadataKeys.SOURCE_TYPE_USER : sourceType)
        );
        // 将任务消息发布到消息队列，触发后续异步摄入流程
        documentIngestionProducer.publish(new DocumentIngestionMessage(job.getId(), ownerUserId, resolvedSourceId));
        // 从数据库重新查询并返回最新的任务状态
        return documentIngestionJobService.findJob(ownerUserId, job.getId()).orElse(job);
    }
}
