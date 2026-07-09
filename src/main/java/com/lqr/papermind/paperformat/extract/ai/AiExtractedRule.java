package com.lqr.papermind.paperformat.extract.ai;

/** A single AI-extracted format requirement candidate. */
public record AiExtractedRule(
        String fieldPath,
        Object value,
        double confidence,
        String evidence,
        String source
) {
}
