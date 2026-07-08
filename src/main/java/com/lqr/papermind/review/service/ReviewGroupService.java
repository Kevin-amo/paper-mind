package com.lqr.papermind.review.service;

import com.lqr.papermind.review.dto.AdminReviewTaskSummaryResponse;
import com.lqr.papermind.review.dto.ReviewGroupMemberResponse;
import com.lqr.papermind.review.dto.ReviewGroupMemberUpdateRequest;
import com.lqr.papermind.review.dto.ReviewGroupRequest;
import com.lqr.papermind.review.dto.ReviewGroupResponse;

import java.util.List;
import java.util.UUID;

public interface ReviewGroupService {

    /**
     * 查询评审组列表
     *
     * @return 评审组列表
     */
    List<ReviewGroupResponse> listGroups();

    /**
     * 创建评审组
     *
     * @param operatorUserId 操作人用户ID
     * @param request        创建评审组请求参数
     * @return 创建的评审组
     */
    ReviewGroupResponse createGroup(UUID operatorUserId, ReviewGroupRequest request);

    /**
     * 更新评审组
     *
     * @param groupId 评审组ID
     * @param request 更新评审组请求参数
     * @return 更新后的评审组
     */
    ReviewGroupResponse updateGroup(UUID groupId, ReviewGroupRequest request);

    /**
     * 查询评审组成员列表
     *
     * @param groupId 评审组ID
     * @return 评审组成员列表
     */
    List<ReviewGroupMemberResponse> listGroupMembers(UUID groupId);

    /**
     * 替换评审组成员
     *
     * @param operatorUserId 操作人用户ID
     * @param groupId        评审组ID
     * @param request        成员更新请求参数
     * @return 更新后的评审组成员列表
     */
    List<ReviewGroupMemberResponse> replaceGroupMembers(UUID operatorUserId, UUID groupId, ReviewGroupMemberUpdateRequest request);

    /**
     * 查询组长负责的评审组列表
     *
     * @param leaderUserId 组长用户ID
     * @return 评审组列表
     */
    List<ReviewGroupResponse> listLeaderGroups(UUID leaderUserId);

    /**
     * 组长查询评审组成员列表
     *
     * @param currentUserId 当前用户ID
     * @param groupId       评审组ID
     * @return 评审组成员列表
     */
    List<ReviewGroupMemberResponse> listGroupMembersForLeader(UUID currentUserId, UUID groupId);

    /**
     * 组长查询未分配的任务列表
     *
     * @param currentUserId 当前用户ID
     * @param groupId       评审组ID
     * @return 未分配任务列表
     */
    List<AdminReviewTaskSummaryResponse> listUnassignedTasksForLeader(UUID currentUserId, UUID groupId);

    /**
     * 组长查询评审组任务列表
     *
     * @param currentUserId 当前用户ID
     * @param groupId       评审组ID
     * @return 评审组任务列表
     */
    List<AdminReviewTaskSummaryResponse> listGroupTasksForLeader(UUID currentUserId, UUID groupId);
}