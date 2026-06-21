package com.lqr.papermind.review.audit;

import com.lqr.papermind.review.audit.impl.ReviewAuditServiceImpl;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

/**
 * 评审审计日志切面，拦截 @ReviewAudit 注解标记的业务方法，
 * 在方法执行成功后自动记录审计日志。
 * <p>
 * 业务方法通过 AuditContext 设置审计数据（taskId、操作人、快照等），
 * 切面负责读取上下文并调用 ReviewAuditService.append() 完成日志持久化。
 */
@Aspect
@Component
@RequiredArgsConstructor
public class ReviewAuditAspect {

    private final ReviewAuditServiceImpl reviewAuditService;

    @Around("@annotation(reviewAudit)")
    public Object audit(ProceedingJoinPoint pjp, ReviewAudit reviewAudit) throws Throwable {
        try {
            Object result = pjp.proceed();
            AuditContext ctx = AuditContext.get();
            if (ctx != null && ctx.hasData()) {
                String message = reviewAudit.message().isEmpty()
                        ? reviewAudit.action().getDefaultMessage()
                        : reviewAudit.message();
                reviewAuditService.append(
                        ctx.getTaskId(),
                        ctx.getOperatorUserId(),
                        reviewAudit.action().name(),
                        message,
                        ctx.getBeforeSnapshot(),
                        ctx.getAfterSnapshot(),
                        ctx.getClientInfo()
                );
            }
            return result;
        } finally {
            AuditContext.clear();
        }
    }
}
