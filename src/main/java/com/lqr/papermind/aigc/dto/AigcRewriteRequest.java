package com.lqr.papermind.aigc.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 段落学术润色请求。
 *
 * <p>Prompt rules adapted from Yezery/aigc-down-skill, MIT License.
 * Repository: https://github.com/Yezery/aigc-down-skill</p>
 */
public record AigcRewriteRequest(

        @NotBlank(message = "待改写段落不能为空")
        @Size(min = 20, max = 5000, message = "段落长度须在 20-5000 字符之间")
        String paragraph,

        @Size(max = 100, message = "学科领域不能超过 100 个字符")
        String discipline,

        @Pattern(regexp = "light|standard|strong", message = "改写强度只允许 light、standard、strong")
        String rewriteStrength,

        @Size(max = 30, message = "保留术语最多 30 个")
        List<@Size(max = 80, message = "每个术语不能超过 80 个字符") String> keepTerms,

        @Size(max = 1000, message = "额外要求不能超过 1000 个字符")
        String extraRequirements
) {
}
