package com.lqr.papermind.document.structured.model;

/**
 * 结构化字段的来源与证据信息。
 */
public record StructuredFieldEvidence(
        String fieldName,
        String source,
        boolean missing,
        String evidence
) {
}