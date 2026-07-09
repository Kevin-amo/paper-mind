package com.lqr.papermind.paperformat.dto;

import jakarta.validation.constraints.Size;

/**
 * 管理员更新模板请求DTO，包含可选的名称、学校名称和公开状态字段
 *
 * @param name          模板名称（最多160个字符）
 * @param schoolName    学校名称（最多160个字符）
 * @param publicTemplate 是否公开模板
 */
public record AdminPaperFormatTemplateUpdateRequest(
        @Size(max = 160, message = "模板名称不能超过160个字符") String name,
        @Size(max = 160, message = "学校名称不能超过160个字符") String schoolName,
        Boolean publicTemplate
) {
}
