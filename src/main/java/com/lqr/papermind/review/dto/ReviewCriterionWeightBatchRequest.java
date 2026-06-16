package com.lqr.papermind.review.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

/**
 * 批量更新评审指标权重请求DTO
 */
public record ReviewCriterionWeightBatchRequest(
        @NotEmpty(message = "权重列表不能为空") List<@Valid WeightItem> weights
) {
    public record WeightItem(
            @NotNull(message = "指标ID不能为空") UUID id,
            @NotNull(message = "权重不能为空") Integer weight
    ) {
    }
}
