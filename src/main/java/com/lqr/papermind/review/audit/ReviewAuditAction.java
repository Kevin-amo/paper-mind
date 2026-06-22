package com.lqr.papermind.review.audit;

/**
 * 评审审计操作类型枚举，替代业务代码中的硬编码字符串。
 */
public enum ReviewAuditAction {

    CREATE_TASK("创建评审任务"),
    AI_REVIEW("生成 AI 辅助评审报告"),
    ADJUST_REPORT("人工调整评审报告"),
    DISPATCH_TO_GROUP("管理员派发评审任务到小组"),
    ASSIGN_BY_ADMIN_OVERRIDE("管理员兜底分配评审任务"),
    ASSIGN_BY_LEADER("组长分配本组评审任务"),
    JOIN_REVIEW_BY_LEADER("组长加入本组评审任务"),
    SUBMIT_ASSIGNMENT("提交个人评审任务"),
    UPDATE_CONSENSUS("保存最终评分与共识意见"),
    CONFIRM_CONSENSUS("确认最终评分与共识意见"),
    RECALCULATE_CONSENSUS("重新计算最终评分与共识草稿");

    private final String defaultMessage;

    ReviewAuditAction(String defaultMessage) {
        this.defaultMessage = defaultMessage;
    }

    public String getDefaultMessage() {
        return defaultMessage;
    }
}
