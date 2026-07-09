package com.lqr.papermind.aigc.dto;

/**
 * 改写质量评分项。
 *
 * <p>每项为 0-10 整数，overall 为综合分。</p>
 */
public record AigcQualityScoreResponse(
        Integer directness,
        Integer rhythm,
        Integer academicTone,
        Integer informationDensity,
        Integer meaningPreservation,
        Integer overall
) {

    /**
     * 返回全零的默认评分，用于模型输出异常时的降级响应。
     */
    public static AigcQualityScoreResponse defaultScore() {
        return new AigcQualityScoreResponse(0, 0, 0, 0, 0, 0);
    }
}
