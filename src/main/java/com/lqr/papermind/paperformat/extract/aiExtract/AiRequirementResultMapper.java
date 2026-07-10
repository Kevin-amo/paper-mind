package com.lqr.papermind.paperformat.extract.aiExtract;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lqr.papermind.paperformat.model.FormatSpec;
import com.lqr.papermind.paperformat.model.HeadingStyleRule;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;

/** 解析 AI 的 JSON 输出并通过白名单映射为格式规则候选项。 */
@Component
@RequiredArgsConstructor
public class AiRequirementResultMapper {

    private static final Set<String> ALLOWED_FIELD_PATHS = Set.of(
            "pageRule.paperSize",
            "pageRule.marginTopMm",
            "pageRule.marginBottomMm",
            "pageRule.marginLeftMm",
            "pageRule.marginRightMm",
            "pageRule.insideMarginMm",
            "pageRule.outsideMarginMm",
            "pageRule.gutterMm",
            "pageRule.headerDistanceMm",
            "pageRule.footerDistanceMm",
            "pageRule.mirrorMargins",
            "pageRule.duplexPrint",
            "headerFooterRule.headerText",
            "headerFooterRule.headerCentered",
            "headerFooterRule.headerFontEastAsia",
            "headerFooterRule.headerFontSizePt",
            "headerFooterRule.footerPageNumber",
            "headerFooterRule.footerCentered",
            "bodyRule.eastAsiaFont",
            "bodyRule.asciiFont",
            "bodyRule.latinFont",
            "bodyRule.fontSizePt",
            "bodyRule.lineSpacingRule",
            "bodyRule.lineSpacingPt",
            "bodyRule.spaceBeforePt",
            "bodyRule.spaceAfterPt",
            "headingRules[0].level",
            "headingRules[0].numberingPattern",
            "headingRules[0].eastAsiaFont",
            "headingRules[0].asciiFont",
            "headingRules[0].fontSizePt",
            "headingRules[0].bold",
            "headingRules[0].alignment",
            "headingRules[1].level",
            "headingRules[1].numberingPattern",
            "headingRules[1].eastAsiaFont",
            "headingRules[1].asciiFont",
            "headingRules[1].fontSizePt",
            "headingRules[1].bold",
            "headingRules[1].alignment",
            "headingRules[2].level",
            "headingRules[2].numberingPattern",
            "headingRules[2].eastAsiaFont",
            "headingRules[2].asciiFont",
            "headingRules[2].fontSizePt",
            "headingRules[2].bold",
            "headingRules[2].alignment",
            "sectionRules.title.eastAsiaFont",
            "sectionRules.title.asciiFont",
            "sectionRules.title.latinFont",
            "sectionRules.title.fontSizePt",
            "sectionRules.title.lineSpacingRule",
            "sectionRules.title.lineSpacingPt",
            "sectionRules.title.spaceBeforePt",
            "sectionRules.title.spaceAfterPt",
            "sectionRules.title.bold",
            "sectionRules.title.alignment",
            "sectionRules.tocTitle.eastAsiaFont",
            "sectionRules.tocTitle.asciiFont",
            "sectionRules.tocTitle.latinFont",
            "sectionRules.tocTitle.fontSizePt",
            "sectionRules.tocTitle.lineSpacingRule",
            "sectionRules.tocTitle.lineSpacingPt",
            "sectionRules.tocTitle.spaceBeforePt",
            "sectionRules.tocTitle.spaceAfterPt",
            "sectionRules.tocTitle.bold",
            "sectionRules.tocTitle.alignment",
            "sectionRules.abstractLabel.eastAsiaFont",
            "sectionRules.abstractLabel.fontSizePt",
            "sectionRules.abstractLabel.bold",
            "sectionRules.abstractContent.eastAsiaFont",
            "sectionRules.abstractContent.asciiFont",
            "sectionRules.abstractContent.latinFont",
            "sectionRules.abstractContent.fontSizePt",
            "sectionRules.abstractContent.lineSpacingRule",
            "sectionRules.abstractContent.lineSpacingPt",
            "sectionRules.abstractContent.spaceBeforePt",
            "sectionRules.abstractContent.spaceAfterPt",
            "sectionRules.keywordsLabel.eastAsiaFont",
            "sectionRules.keywordsLabel.fontSizePt",
            "sectionRules.keywordsLabel.bold",
            "sectionRules.englishAbstractContent.asciiFont",
            "sectionRules.englishAbstractContent.latinFont",
            "sectionRules.englishAbstractContent.fontSizePt",
            "sectionRules.englishAbstractContent.lineSpacingRule",
            "sectionRules.englishAbstractContent.lineSpacingPt",
            "sectionRules.englishAbstractContent.spaceBeforePt",
            "sectionRules.englishAbstractContent.spaceAfterPt"
    );

    private final ObjectMapper objectMapper;

    public AiRequirementExtractionResult parse(String rawOutput) {
        AiRequirementExtractionResult result = new AiRequirementExtractionResult();
        result.setRawOutput(rawOutput);
        try {
            Map<String, Object> root = objectMapper.readValue(extractJson(rawOutput), new TypeReference<>() {
            });
            Object rules = root.get("rules");
            if (rules instanceof List<?> list) {
                for (Object item : list) {
                    parseRule(item, result);
                }
            }
            Object refs = root.get("referenceRequirements");
            if (refs instanceof List<?> list) {
                for (Object item : list) {
                    if (item instanceof Map<?, ?> map) {
                        result.referenceRequirements().add(stringMap(map));
                    }
                }
            }
            Object notes = root.get("notes");
            if (notes instanceof List<?> list) {
                list.forEach(item -> result.notes().add(String.valueOf(item)));
            }
        } catch (RuntimeException | JsonProcessingException ex) {
            result.warnings().add("AI output is not valid requirement JSON: " + ex.getMessage());
        }
        return result;
    }

    public boolean applyRule(FormatSpec spec, AiExtractedRule rule, List<String> warnings) {
        FieldWriter writer = writers().get(canonicalPath(rule.fieldPath()));
        if (writer == null) {
            warnings.add("Ignored unknown AI fieldPath: " + rule.fieldPath());
            return false;
        }
        try {
            writer.accept(spec, rule.value());
            return true;
        } catch (RuntimeException ex) {
            warnings.add("Ignored AI rule with incompatible value for " + rule.fieldPath() + ": " + rule.value());
            return false;
        }
    }

    public boolean isAllowed(String fieldPath) {
        return ALLOWED_FIELD_PATHS.contains(canonicalPath(fieldPath));
    }

    public String canonicalPath(String fieldPath) {
        if (fieldPath == null) {
            return null;
        }
        String value = fieldPath.trim();
        for (int level = 1; level <= 3; level++) {
            value = value.replace("headingRules." + level + ".", "headingRules[" + (level - 1) + "].");
        }
        return value;
    }

    private void parseRule(Object item, AiRequirementExtractionResult result) {
        if (!(item instanceof Map<?, ?> map)) {
            result.warnings().add("Ignored non-object AI rule");
            return;
        }
        String fieldPath = stringValue(map.get("fieldPath"));
        String canonical = canonicalPath(fieldPath);
        if (!ALLOWED_FIELD_PATHS.contains(canonical)) {
            result.warnings().add("Ignored unknown AI fieldPath: " + fieldPath);
            return;
        }
        double confidence = confidence(map.get("confidence"), result, canonical);
        result.addRule(new AiExtractedRule(
                canonical,
                map.get("value"),
                confidence,
                stringValue(map.get("evidence")),
                stringValue(map.get("source"))
        ));
    }

    private double confidence(Object value, AiRequirementExtractionResult result, String fieldPath) {
        if (value instanceof Number number) {
            double confidence = number.doubleValue();
            if (confidence >= 0.0 && confidence <= 1.0) {
                return confidence;
            }
        }
        result.warnings().add("Invalid confidence for " + fieldPath + ", treated as 0");
        return 0.0;
    }

    private String extractJson(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("empty output");
        }
        String text = value.trim();
        if (text.startsWith("```")) {
            text = text.replaceFirst("^```[a-zA-Z]*", "").replaceFirst("```$", "").trim();
        }
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start < 0 || end <= start) {
            throw new IllegalStateException("missing JSON object");
        }
        return text.substring(start, end + 1);
    }

    private Map<String, Object> stringMap(Map<?, ?> map) {
        Map<String, Object> result = new LinkedHashMap<>();
        map.forEach((key, value) -> result.put(String.valueOf(key), value));
        return result;
    }

    private String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private Map<String, FieldWriter> writers() {
        Map<String, FieldWriter> writers = new LinkedHashMap<>();
        writers.put("pageRule.paperSize", (spec, value) -> {
            if ("A4".equalsIgnoreCase(asString(value))) {
                spec.getPageRule().setPageWidthMm(210.0);
                spec.getPageRule().setPageHeightMm(297.0);
            }
        });
        writers.put("pageRule.marginTopMm", (spec, value) -> spec.getPageRule().setMarginTopMm(asDouble(value)));
        writers.put("pageRule.marginBottomMm", (spec, value) -> spec.getPageRule().setMarginBottomMm(asDouble(value)));
        writers.put("pageRule.marginLeftMm", (spec, value) -> spec.getPageRule().setMarginLeftMm(asDouble(value)));
        writers.put("pageRule.marginRightMm", (spec, value) -> spec.getPageRule().setMarginRightMm(asDouble(value)));
        writers.put("pageRule.insideMarginMm", (spec, value) -> spec.getPageRule().setInsideMarginMm(asDouble(value)));
        writers.put("pageRule.outsideMarginMm", (spec, value) -> spec.getPageRule().setOutsideMarginMm(asDouble(value)));
        writers.put("pageRule.gutterMm", (spec, value) -> spec.getPageRule().setGutterMm(asDouble(value)));
        writers.put("pageRule.headerDistanceMm", (spec, value) -> spec.getPageRule().setHeaderDistanceMm(asDouble(value)));
        writers.put("pageRule.footerDistanceMm", (spec, value) -> spec.getPageRule().setFooterDistanceMm(asDouble(value)));
        writers.put("pageRule.mirrorMargins", (spec, value) -> spec.getPageRule().setMirrorMargins(asBoolean(value)));
        writers.put("pageRule.duplexPrint", (spec, value) -> spec.getPageRule().setDuplexPrint(asBoolean(value)));
        writers.put("headerFooterRule.headerText", (spec, value) -> spec.getHeaderFooterRule().setHeaderText(asString(value)));
        writers.put("headerFooterRule.headerCentered", (spec, value) -> spec.getHeaderFooterRule().setHeaderCentered(asBoolean(value)));
        writers.put("headerFooterRule.headerFontEastAsia", (spec, value) -> spec.getHeaderFooterRule().setHeaderFontEastAsia(asString(value)));
        writers.put("headerFooterRule.headerFontSizePt", (spec, value) -> spec.getHeaderFooterRule().setHeaderFontSizePt(asDouble(value)));
        writers.put("headerFooterRule.footerPageNumber", (spec, value) -> spec.getHeaderFooterRule().setFooterPageNumber(asBoolean(value)));
        writers.put("headerFooterRule.footerCentered", (spec, value) -> spec.getHeaderFooterRule().setFooterCentered(asBoolean(value)));
        paragraphWriters(writers, "bodyRule", (spec, ignored) -> spec.getBodyRule());
        for (int index = 0; index < 3; index++) {
            int level = index + 1;
            paragraphWriters(writers, "headingRules[" + index + "]", (spec, ignored) -> spec.getHeadingRules().computeIfAbsent(level, key -> {
                HeadingStyleRule heading = new HeadingStyleRule();
                heading.setLevel(level);
                return heading;
            }));
            writers.put("headingRules[" + index + "].level", (spec, value) -> spec.getHeadingRules().computeIfAbsent(level, key -> new HeadingStyleRule()).setLevel(asInt(value)));
            writers.put("headingRules[" + index + "].numberingPattern", (spec, value) -> spec.getHeadingRules().computeIfAbsent(level, key -> new HeadingStyleRule()).setNumberingPattern(asString(value)));
        }
        for (String section : List.of("title", "tocTitle", "abstractLabel", "abstractContent", "keywordsLabel", "englishAbstractContent")) {
            paragraphWriters(writers, "sectionRules." + section, (spec, ignored) -> spec.getSectionRules().computeIfAbsent(section, key -> new com.lqr.papermind.paperformat.model.ParagraphStyleRule()));
        }
        return writers;
    }

    private void paragraphWriters(Map<String, FieldWriter> writers, String prefix, RuleAccessor accessor) {
        writers.put(prefix + ".eastAsiaFont", (spec, value) -> accessor.rule(spec, value).setEastAsiaFont(asString(value)));
        writers.put(prefix + ".asciiFont", (spec, value) -> accessor.rule(spec, value).setAsciiFont(asString(value)));
        writers.put(prefix + ".latinFont", (spec, value) -> accessor.rule(spec, value).setLatinFont(asString(value)));
        writers.put(prefix + ".fontSizePt", (spec, value) -> accessor.rule(spec, value).setFontSizePt(asDouble(value)));
        writers.put(prefix + ".lineSpacingRule", (spec, value) -> accessor.rule(spec, value).setLineSpacingRule(asString(value)));
        writers.put(prefix + ".lineSpacingPt", (spec, value) -> accessor.rule(spec, value).setLineSpacingPt(asDouble(value)));
        writers.put(prefix + ".spaceBeforePt", (spec, value) -> accessor.rule(spec, value).setSpaceBeforePt(asDouble(value)));
        writers.put(prefix + ".spaceAfterPt", (spec, value) -> accessor.rule(spec, value).setSpaceAfterPt(asDouble(value)));
        writers.put(prefix + ".bold", (spec, value) -> accessor.rule(spec, value).setBold(asBoolean(value)));
        writers.put(prefix + ".alignment", (spec, value) -> accessor.rule(spec, value).setAlignment(asString(value)));
    }

    private String asString(Object value) {
        if (value == null) {
            throw new IllegalArgumentException("null string");
        }
        return String.valueOf(value);
    }

    private Double asDouble(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        return Double.parseDouble(asString(value));
    }

    private Integer asInt(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        return Integer.parseInt(asString(value));
    }

    private Boolean asBoolean(Object value) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        return Boolean.parseBoolean(asString(value));
    }

    private interface FieldWriter extends BiConsumer<FormatSpec, Object> {
    }

    private interface RuleAccessor {
        com.lqr.papermind.paperformat.model.ParagraphStyleRule rule(FormatSpec spec, Object value);
    }
}
