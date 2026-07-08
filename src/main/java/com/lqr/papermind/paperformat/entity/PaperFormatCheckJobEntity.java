package com.lqr.papermind.paperformat.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.lqr.papermind.common.typehandler.JsonbTypeHandler;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.UUID;

@Data
@TableName(value = "public.paper_format_check_job", autoResultMap = true)
public class PaperFormatCheckJobEntity {
    @TableId(value = "id", type = IdType.INPUT)
    private UUID id;
    private UUID ownerUserId;
    private UUID templateId;
    private UUID documentId;
    private String sourceId;
    private UUID reviewTaskId;
    private String scope;
    private String status;
    @TableField(value = "summary", typeHandler = JsonbTypeHandler.class)
    private Object summary;
    @TableField(value = "violations", typeHandler = JsonbTypeHandler.class)
    private Object violations;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
