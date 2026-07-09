package com.lqr.papermind.paperformat.extract.ai;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Result returned by AI-assisted template requirement extraction. */
public class AiRequirementExtractionResult {
    private final List<AiExtractedRule> rules = new ArrayList<>();
    private final List<Map<String, Object>> referenceRequirements = new ArrayList<>();
    private final List<String> notes = new ArrayList<>();
    private final List<String> warnings = new ArrayList<>();
    private String rawOutput;

    public static AiRequirementExtractionResult empty() {
        return new AiRequirementExtractionResult();
    }

    public List<AiExtractedRule> rules() {
        return rules;
    }

    public List<Map<String, Object>> referenceRequirements() {
        return referenceRequirements;
    }

    public List<String> notes() {
        return notes;
    }

    public List<String> warnings() {
        return warnings;
    }

    public String rawOutput() {
        return rawOutput;
    }

    public void setRawOutput(String rawOutput) {
        this.rawOutput = rawOutput;
    }

    public void addRule(AiExtractedRule rule) {
        rules.add(rule);
    }
}
