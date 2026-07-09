package com.lqr.papermind.paperformat.extract;

import com.lqr.papermind.paperformat.model.FormatSpec;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 格式规则文本提取结果，包含解析出的格式规则、已提取的字段集合和文本规则列表
 */
final class RequirementRuleExtractionResult {
    private final FormatSpec formatSpec = new FormatSpec();
    private final Set<String> textFields = new LinkedHashSet<>();
    private final List<String> textRules = new ArrayList<>();
    private final List<Map<String, Object>> referenceRequirements = new ArrayList<>();

    /** 获取格式规则 */
    FormatSpec formatSpec() {
        return formatSpec;
    }

    /** 获取已提取的文本字段集合 */
    Set<String> textFields() {
        return textFields;
    }

    /** 获取文本规则描述列表 */
    List<String> textRules() {
        return textRules;
    }

    /** 获取参考文献规则列表 */
    List<Map<String, Object>> referenceRequirements() {
        return referenceRequirements;
    }

    /** 标记一个字段已被文本解析提取，并记录对应的规则描述 */
    void mark(String field, String rule) {
        textFields.add(field);
        if (!textRules.contains(rule)) {
            textRules.add(rule);
        }
    }

    /** 判断指定字段是否已被文本解析提取 */
    boolean hasField(String field) {
        return textFields.contains(field);
    }
}