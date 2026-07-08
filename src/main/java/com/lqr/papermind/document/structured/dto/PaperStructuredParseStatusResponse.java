package com.lqr.papermind.document.structured.dto;

import com.lqr.papermind.document.structured.entity.PaperStructuredParseEntity;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * 论文结构化解析状态响应。
 */
public record PaperStructuredParseStatusResponse(
        String sourceId,
        String status,
        List<String> missingFields,
        String errorMessage,
        OffsetDateTime parsedAt,
        OffsetDateTime updatedAt
) {
    /**
     * 从实体创建响应对象。
     *
     * @param entity 论文结构化解析实体
     * @return 状态响应对象
     */
    public static PaperStructuredParseStatusResponse from(PaperStructuredParseEntity entity) {
        return new PaperStructuredParseStatusResponse(
                entity.getSourceId(),
                entity.getStatus(),
                stringList(entity.getMissingFields()),
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