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
 * 论文格式模板实体类，对应paper_format_template表
 */
@Data
@TableName(value = "public.paper_format_template", autoResultMap = true)
public class PaperFormatTemplateEntity {
    /** 模板ID */
    @TableId(value = "id", type = IdType.INPUT)
    private UUID id;
    /** 所有者用户ID */
    private UUID ownerUserId;
    /** 模板名称 */
    private String name;
    /** 学校名称 */
    private String schoolName;
    /** 原始文件名 */
    private String fileName;
    /** 文件类型 */
    private String fileType;
    /** 存储路径标识 */
    private String storageKey;
    /** 模板状态（PARSING/READY/FAILED/NEED_CONFIRM） */
    private String status;
    /** 格式规则（JSON） */
    @TableField(value = "format_spec", typeHandler = JsonbTypeHandler.class)
    private Object formatSpec;
    /** 格式规则提取报告（JSON） */
    @TableField(value = "extraction_report", typeHandler = JsonbTypeHandler.class)
    private Object extractionReport;
    /** 是否已确认 */
    private Boolean confirmed;
    /** 是否为公共模板 */
    private Boolean publicTemplate;
    /** 创建时间 */
    private OffsetDateTime createdAt;
    /** 更新时间 */
    private OffsetDateTime updatedAt;
}
