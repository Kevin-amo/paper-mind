package com.lqr.papermind.paperformat.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.lqr.papermind.common.typehandler.JsonbTypeHandler;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * 论文格式检查任务实体类，对应paper_format_check_job表
 */
@Data
@TableName(value = "public.paper_format_check_job", autoResultMap = true)
public class PaperFormatCheckJobEntity {
    /** 任务ID */
    @TableId(value = "id", type = IdType.INPUT)
    private UUID id;
    /** 所有者用户ID */
    private UUID ownerUserId;
    /** 使用的模板ID */
    private UUID templateId;
    /** 文档ID */
    private UUID documentId;
    /** 文档来源ID */
    private String sourceId;
    /** 关联的评审任务ID */
    private UUID reviewTaskId;
    /** 检查范围（USER_SELF_CHECK/REVIEW_PRECHECK） */
    private String scope;
    /** 检查状态（RUNNING/PASSED/FAILED/ERROR） */
    private String status;
    /** 违规统计摘要（JSON） */
    @TableField(value = "summary", typeHandler = JsonbTypeHandler.class)
    private Object summary;
    /** 违规详情列表（JSON） */
    @TableField(value = "violations", typeHandler = JsonbTypeHandler.class)
    private Object violations;
    /** 创建时间 */
    private OffsetDateTime createdAt;
    /** 更新时间 */
    private OffsetDateTime updatedAt;
}
