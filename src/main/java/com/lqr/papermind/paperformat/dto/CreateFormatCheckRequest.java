package com.lqr.papermind.paperformat.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * 创建格式检查请求DTO，指定模板ID和可选的文档来源ID
 *
 * @param templateId 使用的模板ID（必填）
 * @param sourceId   文档来源ID（用户自检时使用）
 */
public record CreateFormatCheckRequest(
        @NotNull UUID templateId,
        String sourceId
) {
}
