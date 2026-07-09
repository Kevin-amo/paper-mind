package com.lqr.papermind.paperformat.model;

/**
 * 格式规则来源枚举，表示规则的提取方式
 */
public enum FormatRuleSource {
    /** 从OOXML结构中确认提取 */
    STRUCTURAL_CONFIRMED,
    /** 从文本描述中推断提取 */
    TEXT_INFERRED,
    /** 文本规则与OOXML规则存在冲突 */
    CONFLICT,
    /** 人工确认的规则 */
    MANUAL_CONFIRMED
}
