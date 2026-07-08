package com.lqr.papermind.paperformat.dto;

import com.lqr.papermind.paperformat.entity.PaperFormatTemplateEntity;

import java.time.OffsetDateTime;
import java.util.UUID;

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
