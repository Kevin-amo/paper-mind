package com.lqr.papermind.paperformat.dto;

import com.lqr.papermind.paperformat.entity.PaperFormatCheckJobEntity;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * 格式检查任务响应DTO，返回检查任务的详细信息
 *
 * @param id           任务ID
 * @param templateId   使用的模板ID
 * @param documentId   文档ID
 * @param sourceId     文档来源ID
 * @param reviewTaskId 关联的评审任务ID
 * @param scope        检查范围
 * @param status       检查状态
 * @param summary      违规统计摘要
 * @param violations   违规详情列表
 * @param createdAt    创建时间
 * @param updatedAt    更新时间
 */
public record PaperFormatCheckJobResponse(
        UUID id,
        UUID templateId,
        UUID documentId,
        String sourceId,
        UUID reviewTaskId,
        String scope,
        String status,
        Object summary,
        Object violations,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
    /**
     * 从实体对象构建响应DTO
     *
     * @param entity 检查任务实体
     * @return 检查任务响应DTO
     */
    public static PaperFormatCheckJobResponse from(PaperFormatCheckJobEntity entity) {
        return new PaperFormatCheckJobResponse(
                entity.getId(),
                entity.getTemplateId(),
                entity.getDocumentId(),
                entity.getSourceId(),
                entity.getReviewTaskId(),
                entity.getScope(),
                entity.getStatus(),
                entity.getSummary(),
                entity.getViolations(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}
