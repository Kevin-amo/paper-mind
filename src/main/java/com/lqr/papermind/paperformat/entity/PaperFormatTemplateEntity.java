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
@TableName(value = "public.paper_format_template", autoResultMap = true)
public class PaperFormatTemplateEntity {
    @TableId(value = "id", type = IdType.INPUT)
    private UUID id;
    private UUID ownerUserId;
    private String name;
    private String schoolName;
    private String fileName;
    private String fileType;
    private String storageKey;
    private String status;
    @TableField(value = "format_spec", typeHandler = JsonbTypeHandler.class)
    private Object formatSpec;
    @TableField(value = "extraction_report", typeHandler = JsonbTypeHandler.class)
    private Object extractionReport;
    private Boolean confirmed;
    private Boolean publicTemplate;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
