package com.lqr.papermind.review.audit;

import java.util.Map;
import java.util.UUID;

/**
 * 审计上下文，基于 ThreadLocal 在业务方法与 AOP 切面之间传递审计数据。
 * 业务方法通过 set() 设置审计数据，切面在方法执行后读取并记录审计日志。
 */
public class AuditContext {

    private static final ThreadLocal<AuditContext> HOLDER = new ThreadLocal<>();

    private UUID taskId;
    private UUID operatorUserId;
    private Map<String, Object> beforeSnapshot;
    private Map<String, Object> afterSnapshot;
    private Map<String, Object> clientInfo;

    public static AuditContext get() {
        return HOLDER.get();
    }

    public static void set(AuditContext context) {
        HOLDER.set(context);
    }

    public static void clear() {
        HOLDER.remove();
    }

    /**
     * 判断上下文中是否包含有效的审计数据（至少包含 taskId）。
     */
    public boolean hasData() {
        return taskId != null;
    }

    public AuditContext taskId(UUID taskId) {
        this.taskId = taskId;
        return this;
    }

    public AuditContext operatorUserId(UUID operatorUserId) {
        this.operatorUserId = operatorUserId;
        return this;
    }

    public AuditContext beforeSnapshot(Map<String, Object> beforeSnapshot) {
        this.beforeSnapshot = beforeSnapshot;
        return this;
    }

    public AuditContext afterSnapshot(Map<String, Object> afterSnapshot) {
        this.afterSnapshot = afterSnapshot;
        return this;
    }

    public AuditContext clientInfo(Map<String, Object> clientInfo) {
        this.clientInfo = clientInfo;
        return this;
    }

    public UUID getTaskId() {
        return taskId;
    }

    public UUID getOperatorUserId() {
        return operatorUserId;
    }

    public Map<String, Object> getBeforeSnapshot() {
        return beforeSnapshot;
    }

    public Map<String, Object> getAfterSnapshot() {
        return afterSnapshot;
    }

    public Map<String, Object> getClientInfo() {
        return clientInfo;
    }
}
