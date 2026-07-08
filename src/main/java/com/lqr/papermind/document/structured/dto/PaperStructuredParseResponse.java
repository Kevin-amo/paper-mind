package com.lqr.papermind.document.structured.dto;

import com.lqr.papermind.document.structured.entity.PaperStructuredParseEntity;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 论文结构化解析完整响应。
 */
public record PaperStructuredParseResponse(
        UUID id,
        UUID documentId,
        String sourceId,
        String status,
        Object ruleResult,
        Object modelResult,
        Object mergedResult,
        List<String> missingFields,
        String rawModelOutput,
        String parserVersion,
        String modelVersion,
        String promptVersion,
        Object qualityMetrics,
        String errorMessage,
        OffsetDateTime parsedAt,
        OffsetDateTime updatedAt
) {
    /**
     * 从实体创建完整响应对象。
     *
     * @param entity 论文结构化解析实体
     * @return 完整响应对象
     */
    public static PaperStructuredParseResponse from(PaperStructuredParseEntity entity) {
        if (entity == null) {
            return null;
        }
        return new PaperStructuredParseResponse(
                entity.getId(),
                entity.getDocumentId(),
                entity.getSourceId(),
                entity.getStatus(),
                entity.getRuleResult(),
                entity.getModelResult(),
                entity.getMergedResult(),
                stringList(entity.getMissingFields()),
                entity.getRawModelOutput(),
                entity.getParserVersion(),
                entity.getModelVersion(),
                entity.getPromptVersion(),
                entity.getQualityMetrics(),
                entity.getErrorMessage(),
                entity.getParsedAt(),
                entity.getUpdatedAt()
        );
    }

    /**
     * 将对象转换为字符串列表。
     *
     * @param value 可能为列表的对象
     * @return 字符串列表
     */
    private static List<String> stringList(Object value) {
        if (value instanceof List<?> list) {
            return list.stream().map(String::valueOf).toList();
        }
        return List.of();
    }
}