package com.lqr.papermind.review.audit;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 声明式审计日志注解，标记需要进行审计记录的业务方法。
 * 由 ReviewAuditAspect 切面拦截并自动记录审计日志。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface ReviewAudit {

    /**
     * 审计操作类型
     */
    ReviewAuditAction action();

    /**
     * 操作备注信息，为空时使用 action 的默认描述
     */
    String message() default "";
}
