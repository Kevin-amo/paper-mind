package com.lqr.papermind.paperformat.dto;

import com.lqr.papermind.paperformat.entity.PaperFormatTemplateEntity;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * 格式模板响应DTO，返回模板的详细信息
 *
 * @param id              模板ID
 * @param ownerUserId     所有者用户ID
 * @param name            模板名称
 * @param schoolName      学校名称
 * @param fileName        原始文件名
 * @param fileType        文件类型
 * @param storageKey      存储路径标识
 * @param status          模板状态
 * @param formatSpec      格式规则
 * @param extractionReport 格式规则提取报告
 * @param confirmed       是否已确认
 * @param publicTemplate  是否为公共模板
 * @param createdAt       创建时间
 * @param updatedAt       更新时间
 */
public record PaperFormatTemplateResponse(
        UUID id,
        UUID ownerUserId,
        String name,
        String schoolName,
        String fileName,
        String fileType,
        String storageKey,
        String status,
        Object formatSpec,
        Object extractionReport,
        boolean confirmed,
        boolean publicTemplate,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
    /**
     * 从实体对象构建响应DTO
     *
     * @param entity 模板实体
     * @return 模板响应DTO
     */
    public static PaperFormatTemplateResponse from(PaperFormatTemplateEntity entity) {
        return new PaperFormatTemplateResponse(
                entity.getId(),
                entity.getOwnerUserId(),
                entity.getName(),
                entity.getSchoolName(),
                entity.getFileName(),
                entity.getFileType(),
                entity.getStorageKey(),
                entity.getStatus(),
                entity.getFormatSpec(),
                entity.getExtractionReport(),
                Boolean.TRUE.equals(entity.getConfirmed()),
                Boolean.TRUE.equals(entity.getPublicTemplate()),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}
