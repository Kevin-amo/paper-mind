package com.lqr.papermind.document.structured.service.impl;

import com.lqr.papermind.document.structured.model.PaperStructuredContent;
import com.lqr.papermind.document.structured.model.PaperStructuredContentSupport;
import com.lqr.papermind.document.structured.model.StructuredFieldEvidence;
import com.lqr.papermind.document.structured.model.StructuredParseResult;
import com.lqr.papermind.document.structured.service.PaperStructuredMergePolicy;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 规则优先、模型补齐的结构化结果合并策略。
 */
@Component
public class PaperStructuredMergePolicyImpl implements PaperStructuredMergePolicy {

    /**
     * 合并规则解析结果和模型补全结果。
     * 规则结果非空时优先保留规则结果，否则使用模型结果。
     *
     * @param ruleResult  规则解析结果
     * @param modelResult 模型补全结果
     * @return 合并后的结构化解析结果
     */
    @Override
    public StructuredParseResult merge(StructuredParseResult ruleResult, StructuredParseResult modelResult) {
        PaperStructuredContent content = ruleResult.content();
        Map<String, StructuredFieldEvidence> evidence = new LinkedHashMap<>();
        for (String field : PaperStructuredContentSupport.ALL_FIELDS) {
            StructuredFieldEvidence ruleEvidence = ruleResult.evidence().get(field);
            StructuredFieldEvidence modelEvidence = modelResult.evidence().get(field);
            Object ruleValue = PaperStructuredContentSupport.value(ruleResult.content(), field);
            Object modelValue = PaperStructuredContentSupport.value(modelResult.content(), field);
            // 规则结果非空时，保留规则结果
            if (!PaperStructuredContentSupport.isEmpty(ruleValue) || PaperStructuredContentSupport.isEmpty(modelValue)) {
                evidence.put(field, mergedEvidence(field, ruleEvidence, PaperStructuredContentSupport.isEmpty(ruleValue)));
                continue;
            }
            // 模型结果非空时，使用模型结果
            content = PaperStructuredContentSupport.withValue(content, field, modelValue);
            evidence.put(field, mergedEvidence(field, modelEvidence, false));
        }
        List<String> missingFields = PaperStructuredContentSupport.emptyFields(content);
        return new StructuredParseResult(content, evidence, missingFields);
    }

    /**
     * 合并字段证据信息。
     *
     * @param field   字段名
     * @param source  源证据
     * @param missing 是否缺失
     * @return 合并后的字段证据
     */
    private StructuredFieldEvidence mergedEvidence(String field, StructuredFieldEvidence source, boolean missing) {
        if (source == null) {
            return new StructuredFieldEvidence(field, "MERGED", true, null);
        }
        return new StructuredFieldEvidence(field, source.source(), missing, source.evidence());
    }
}