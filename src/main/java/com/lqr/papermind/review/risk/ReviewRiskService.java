package com.lqr.papermind.review.risk;

import com.lqr.papermind.review.dto.ReviewRiskItemResponse;
import com.lqr.papermind.review.entity.ReviewRiskItemEntity;

import java.util.List;
import java.util.UUID;

/**
 * 评审风险项服务。
 */
public interface ReviewRiskService {

    /**
     * 根据风险项ID查询单个风险项。
     *
     * @param riskId 风险项唯一标识
     * @return 匹配的风险项实体，不存在时返回 null
     */
    ReviewRiskItemEntity findById(UUID riskId);

    /**
     * 根据评审报告ID查询所有关联的风险项，并转换为响应DTO。
     *
     * @param reportId 评审报告唯一标识
     * @return 该报告下所有风险项的响应列表
     */
    List<ReviewRiskItemResponse> listByReportId(UUID reportId);

    /**
     * 替换指定评审报告下的全部风险项。
     *
     * @param reportId 评审报告唯一标识
     * @param taskId   关联任务唯一标识
     * @param risks    风险项列表
     */
    void replaceReportRisks(UUID reportId, UUID taskId, Object risks);

    /**
     * 更新风险项的状态及评审备注。
     *
     * @param riskId       风险项唯一标识
     * @param status       新状态
     * @param reviewerNote 评审人备注
     * @return 更新后的风险项响应DTO
     */
    ReviewRiskItemResponse updateStatus(UUID riskId, String status, String reviewerNote);
}
