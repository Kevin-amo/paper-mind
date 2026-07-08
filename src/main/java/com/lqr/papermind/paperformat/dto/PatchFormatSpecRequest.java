package com.lqr.papermind.paperformat.dto;

import com.lqr.papermind.paperformat.model.FormatSpec;

/**
 * 更新格式规则请求DTO，包含可选的格式规则和确认状态
 *
 * @param formatSpec 新的格式规则
 * @param confirmed  是否确认
 */
public record PatchFormatSpecRequest(
        FormatSpec formatSpec,
        Boolean confirmed
) {
}
