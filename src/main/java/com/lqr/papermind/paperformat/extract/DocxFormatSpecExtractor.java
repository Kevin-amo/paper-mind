package com.lqr.papermind.paperformat.extract;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lqr.papermind.paperformat.config.PaperFormatAiExtractionProperties;
import com.lqr.papermind.paperformat.extract.ai.AiExtractedRule;
import com.lqr.papermind.paperformat.extract.ai.AiRequirementExtractionInput;
import com.lqr.papermind.paperformat.extract.ai.AiRequirementExtractionResult;
import com.lqr.papermind.paperformat.extract.ai.AiRequirementResultMapper;
import com.lqr.papermind.paperformat.extract.ai.DisabledFormatRequirementAiExtractor;
import com.lqr.papermind.paperformat.extract.ai.FormatRequirementAiExtractor;
import com.lqr.papermind.paperformat.model.FormatSpec;
import com.lqr.papermind.paperformat.model.HeadingStyleRule;
import com.lqr.papermind.paperformat.model.HeaderFooterRule;
import com.lqr.papermind.paperformat.model.PageRule;
import com.lqr.papermind.paperformat.model.ParagraphStyleRule;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Function;

/** Extracts template format requirements from fixed text, optional AI evidence, and OOXML fallback. */
@Component
public class DocxFormatSpecExtractor {

    private static final double NUMERIC_TOLERANCE = 0.25;

    private final DocxTemplateEvidenceExtractor evidenceExtractor;
    private final FixedRequirementRuleParser ruleParser;
    private final FormatRequirementAiExtractor aiExtractor;
    private final PaperFormatAiExtractionProperties aiProperties;
    private final AiRequirementResultMapper aiResultMapper;

    public DocxFormatSpecExtractor() {
        this(new DocxTemplateEvidenceExtractor(new DocxRequirementTextExtractor()),
                new FixedRequirementRuleParser(),
                new DisabledFormatRequirementAiExtractor(),
                defaultAiProperties(),
                new AiRequirementResultMapper(new ObjectMapper()));
    }

    @Autowired
    public DocxFormatSpecExtractor(FormatRequirementAiExtractor aiExtractor,
                                   PaperFormatAiExtractionProperties aiProperties) {
        this(new DocxTemplateEvidenceExtractor(new DocxRequirementTextExtractor()),
                new FixedRequirementRuleParser(),
                aiExtractor,
                aiProperties,
                new AiRequirementResultMapper(new ObjectMapper()));
    }

    public DocxFormatSpecExtractor(DocxTemplateEvidenceExtractor evidenceExtractor,
                                   FixedRequirementRuleParser ruleParser,
                                   FormatRequirementAiExtractor aiExtractor,
                                   PaperFormatAiExtractionProperties aiProperties,
                                   AiRequirementResultMapper aiResultMapper) {
        this.evidenceExtractor = evidenceExtractor;
        this.ruleParser = ruleParser;
        this.aiExtractor = aiExtractor;
        this.aiProperties = aiProperties;
        this.aiResultMapper = aiResultMapper;
    }

    public FormatSpec extract(InputStream input) {
        try {
            byte[] bytes = input.readAllBytes();
            FormatSpec ooxmlSpec = extractOoxmlOnly(bytes);
            DocxTemplateEvidence evidence = evidenceExtractor.extract(bytes, ooxmlSpec);
            RequirementRuleExtractionResult fixedText = ruleParser.parse(evidence.bodyParagraphs());
            AiRequirementExtractionResult ai = aiProperties.isEnabled()
                    ? aiExtractor.extract(new AiRequirementExtractionInput(evidence, aiProperties.getMaxInputChars(), aiProperties.getModel()))
                    : AiRequirementExtractionResult.empty();
            FormatSpec spec = merge(fixedText, ai, ooxmlSpec, evidence);
            enrichRoleRules(spec, bytes);
            return spec;
        } catch (Exception ex) {
            throw new IllegalArgumentException("DOCX 格式规则解析失败", ex);
        }
    }

    FormatSpec extractOoxmlOnly(InputStream input) {
        try {
            return extractOoxmlOnly(input.readAllBytes());
        } catch (Exception ex) {
            throw new IllegalArgumentException("DOCX OOXML 格式规则解析失败", ex);
        }
    }

    FormatSpec extractOoxmlOnly(byte[] bytes) {
        try {
            Map<String, String> parts = DocxPackageReader.readXmlParts(new ByteArrayInputStream(bytes));
            FormatSpec spec = new FormatSpec();
            DocxStyleSupport.Styles styles = DocxStyleSupport.parse(parts.get("word/styles.xml"));
            spec.setPageRule(readPageRule(parts.get("word/document.xml"), parts.get("word/settings.xml")));
            spec.setHeaderFooterRule(readHeaderFooter(parts, styles));
            spec.setBodyRule(DocxStyleSupport.merge(styles.docDefaults(), styles.style("Normal")));
            for (int level = 1; level <= 3; level++) {
                ParagraphStyleRule style = DocxStyleSupport.merge(styles.docDefaults(), styles.style("Heading" + level));
                HeadingStyleRule heading = new HeadingStyleRule();
                copy(style, heading);
                heading.setLevel(level);
                spec.getHeadingRules().put(level, heading);
            }
            applyNamedStyleFallbacks(spec, styles);
            spec.getExtractionReport().put("source", "OOXML");
            return spec;
        } catch (Exception ex) {
            throw new IllegalArgumentException("DOCX OOXML 格式规则解析失败", ex);
        }
    }

    private FormatSpec merge(RequirementRuleExtractionResult fixedText,
                             AiRequirementExtractionResult ai,
                             FormatSpec ooxmlSpec,
                             DocxTemplateEvidence evidence) {
        FormatSpec merged = fixedText.formatSpec();
        FormatSpec aiSpec = new FormatSpec();
        Set<String> aiFields = new LinkedHashSet<>();
        List<Map<String, Object>> aiRules = new ArrayList<>();
        List<Map<String, Object>> lowConfidenceAiRules = new ArrayList<>();
        List<String> warnings = new ArrayList<>(ai.warnings());

        for (AiExtractedRule rule : ai.rules()) {
            String fieldPath = aiResultMapper.canonicalPath(rule.fieldPath());
            if (rule.confidence() < aiProperties.getMinConfidence()) {
                lowConfidenceAiRules.add(ruleMap(rule, fieldPath));
                continue;
            }
            if (aiResultMapper.applyRule(aiSpec, new AiExtractedRule(fieldPath, rule.value(), rule.confidence(), rule.evidence(), rule.source()), warnings)) {
                aiFields.add(fieldPath);
                if ("pageRule.paperSize".equals(fieldPath)) {
                    aiFields.add("pageRule.pageWidthMm");
                    aiFields.add("pageRule.pageHeightMm");
                }
                aiRules.add(ruleMap(rule, fieldPath));
            }
        }

        List<String> fallbackFields = new ArrayList<>();
        List<Map<String, Object>> conflicts = new ArrayList<>();
        mergePage(fixedText, merged, aiSpec, aiFields, ooxmlSpec, fallbackFields, conflicts);
        mergeHeaderFooter(fixedText, merged, aiSpec, aiFields, ooxmlSpec, fallbackFields, conflicts);
        mergeParagraph(fixedText, "bodyRule", "bodyRule", merged.getBodyRule(), aiSpec.getBodyRule(), aiFields, ooxmlSpec.getBodyRule(), fallbackFields, conflicts);
        for (int level = 1; level <= 3; level++) {
            int headingLevel = level;
            HeadingStyleRule target = merged.getHeadingRules().computeIfAbsent(headingLevel, ignored -> {
                HeadingStyleRule heading = new HeadingStyleRule();
                heading.setLevel(headingLevel);
                return heading;
            });
            HeadingStyleRule aiHeading = aiSpec.getHeadingRules().get(headingLevel);
            HeadingStyleRule ooxmlHeading = ooxmlSpec.getHeadingRules().get(headingLevel);
            mergeParagraph(fixedText,
                    "headingRules." + headingLevel,
                    "headingRules[" + (headingLevel - 1) + "]",
                    target,
                    aiHeading,
                    aiFields,
                    ooxmlHeading,
                    fallbackFields,
                    conflicts,
                    headingLevel >= 2);
        }
        for (String section : sectionRuleKeys()) {
            ParagraphStyleRule targetExisting = merged.getSectionRules().get(section);
            ParagraphStyleRule sectionRule = aiSpec.getSectionRules().get(section);
            ParagraphStyleRule ooxmlSectionRule = ooxmlSpec.getSectionRules().get(section);
            if (targetExisting != null || sectionRule != null || ooxmlSectionRule != null) {
                ParagraphStyleRule targetRule = merged.getSectionRules().computeIfAbsent(section, ignored -> new ParagraphStyleRule());
                mergeParagraph(fixedText,
                        "sectionRules." + section,
                        "sectionRules." + section,
                        targetRule,
                        sectionRule,
                        aiFields,
                        ooxmlSectionRule,
                        fallbackFields,
                        conflicts);
            }
        }

        Map<String, Object> report = new LinkedHashMap<>();
        report.put("source", aiProperties.isEnabled() ? "text_requirement_with_ai_and_ooxml_fallback" : "text_requirement_with_ooxml_fallback");
        report.put("textRules", fixedText.textRules());
        report.put("textFields", new ArrayList<>(fixedText.textFields()));
        report.put("aiRules", aiRules);
        report.put("lowConfidenceAiRules", lowConfidenceAiRules);
        report.put("ooxmlFallbackFields", fallbackFields);
        report.put("conflicts", conflicts);
        List<Map<String, Object>> referenceRequirements = new ArrayList<>(fixedText.referenceRequirements());
        referenceRequirements.addAll(ai.referenceRequirements());
        report.put("referenceRequirements", referenceRequirements);
        report.put("notes", ai.notes());
        report.put("evidenceSummary", evidence.rawHints());
        report.put("warnings", warnings);
        merged.setExtractionReport(report);
        return merged;
    }

    private void enrichRoleRules(FormatSpec spec, byte[] bytes) {
        try {
            DocxPackageReader.PackageParts parts = DocxPackageReader.read(new ByteArrayInputStream(bytes));
            TemplateEvidenceExtractor extractor = new TemplateEvidenceExtractor(DocxStyleResolver.from(parts));
            List<TemplateEvidence> evidence = extractor.extract(parts);
            Map<String, Object> report = spec.getExtractionReport();
            Map<String, Object> sources = ensureMap(report, "sources");
            List<Map<String, Object>> conflicts = ensureList(report, "conflicts");
            List<Map<String, Object>> samples = ensureList(report, "templateParagraphSamples");
            List<String> warnings = ensureList(report, "warnings");

            for (TemplateEvidence item : evidence) {
                if (samples.size() < 20) {
                    samples.add(sample(item));
                }
                if (item.role() == null || item.role().isBlank()) {
                    continue;
                }
                ParagraphStyleRule instance = cloneRule(item.effectiveStyle());
                instance.setSourcePriority("TEMPLATE_INSTANCE");
                instance.setEvidenceText(item.text());
                if (isForbiddenBodyCandidate(item)) {
                    addIgnoredBodyCandidateWarning(warnings, item.role(), item.styleId());
                    if ("body".equals(item.role())) {
                        continue;
                    }
                }
                if ("body".equals(item.role()) && !isLegalBodyEvidence(item)) {
                    addIgnoredRoleCandidateWarning(warnings, item.role(), item.styleId(), "non-body evidence");
                    continue;
                }
                putPreferredInstance(spec.getRoleRules(), item.role(), instance);
            }

            Map<String, ParagraphStyleRule> textRules = textRoleRules(spec);
            for (Map.Entry<String, ParagraphStyleRule> entry : textRules.entrySet()) {
                String role = entry.getKey();
                ParagraphStyleRule textRule = cloneRule(entry.getValue());
                textRule.setSourcePriority("TEXT_REQUIREMENT");
                ParagraphStyleRule target = spec.getRoleRules().get(role);
                if (target != null) {
                    recordParagraphConflicts("roleRules." + role, textRule, target, conflicts, textRule.getEvidenceText());
                    spec.getRoleRules().put(role, DocxStyleResolver.merge(target, textRule));
                } else {
                    spec.getRoleRules().put(role, textRule);
                }
            }
            sanitizeHeadingRoleBold(spec, warnings);
            ensureKeywordFallbacks(spec, warnings);

            backfillLegacyFields(spec, warnings);
            sanitizeHeadingRuleBold(spec, warnings);
            spec.getRoleRules().forEach((role, rule) -> addSources("roleRules." + role, rule, sources));
            report.put("evidenceSummary", Map.of(
                    "paragraphEvidenceCount", evidence.size(),
                    "roles", new ArrayList<>(spec.getRoleRules().keySet())
            ));
        } catch (Exception ex) {
            ensureList(spec.getExtractionReport(), "warnings").add("角色规则增强失败：" + ex.getMessage());
        }
    }

    private Map<String, ParagraphStyleRule> textRoleRules(FormatSpec spec) {
        Map<String, ParagraphStyleRule> rules = new LinkedHashMap<>();
        rules.put("body", spec.getBodyRule());
        spec.getRoleRules().forEach((role, rule) -> {
            if ("TEXT_REQUIREMENT".equals(rule.getSourcePriority())) {
                rules.put(role, rule);
            }
        });
        if (spec.getHeadingRules().get(1) != null) {
            rules.put("heading1", spec.getHeadingRules().get(1));
        }
        if (spec.getHeadingRules().get(2) != null) {
            rules.put("heading2", spec.getHeadingRules().get(2));
        }
        if (spec.getHeadingRules().get(3) != null) {
            rules.put("heading3", spec.getHeadingRules().get(3));
        }
        copySectionRole(spec, rules, "title", "paperTitle");
        copySectionRole(spec, rules, "tocTitle", "tocTitle");
        copySectionRole(spec, rules, "abstractContent", "cnAbstractContent");
        copySectionRole(spec, rules, "englishAbstractContent", "enAbstractContent");
        copySectionRole(spec, rules, "keywordsLabel", "cnKeywordsLabel");
        if (spec.getHeaderFooterRule().getHeaderFontEastAsia() != null || spec.getHeaderFooterRule().getHeaderFontSizePt() != null) {
            ParagraphStyleRule header = new ParagraphStyleRule();
            header.setEastAsiaFont(spec.getHeaderFooterRule().getHeaderFontEastAsia());
            header.setFontSizePt(spec.getHeaderFooterRule().getHeaderFontSizePt());
            header.setAlignment(spec.getHeaderFooterRule().isHeaderCentered() ? "CENTER" : null);
            rules.put("header", header);
        }
        return rules;
    }

    private void ensureKeywordFallbacks(FormatSpec spec, List<String> warnings) {
        ParagraphStyleRule cnKeywordsLabel = spec.getRoleRules().get("cnKeywordsLabel");
        if (cnKeywordsLabel != null) {
            cnKeywordsLabel.setBold(true);
            cnKeywordsLabel.setSourcePriority("TEXT_REQUIREMENT");
        }
        if (!spec.getRoleRules().containsKey("enKeywordsLabel")) {
            ParagraphStyleRule base = cloneRule(spec.getRoleRules().get("enAbstractContent"));
            if (base != null) {
                base.setBold(true);
                base.setSourcePriority("TEXT_REQUIREMENT");
                spec.getRoleRules().put("enKeywordsLabel", base);
            }
        }
        if (shouldBackfillCnKeywordContent(spec.getRoleRules().get("cnKeywordsContent")) && spec.getRoleRules().containsKey("cnKeywordsLabel")) {
            warnKeywordOverride(warnings, "cnKeywordsContent", spec.getRoleRules().get("cnKeywordsContent"));
            ParagraphStyleRule base = cloneRule(spec.getRoleRules().get("cnAbstractContent"));
            if (base == null) {
                base = cloneRule(spec.getRoleRules().get("cnKeywordsLabel"));
            }
            if (base.getEastAsiaFont() == null || "宋体".equals(base.getEastAsiaFont())) {
                base.setEastAsiaFont("楷体");
            }
            if (base.getFontSizePt() == null) {
                base.setFontSizePt(10.5);
            }
            base.setBold(null);
            base.setSourcePriority("TEXT_REQUIREMENT");
            spec.getRoleRules().put("cnKeywordsContent", base);
        }
        ParagraphStyleRule enKeywordsLabel = spec.getRoleRules().get("enKeywordsLabel");
        if (shouldBackfillEnKeywordLabel(enKeywordsLabel) && spec.getRoleRules().containsKey("enAbstractContent")) {
            warnKeywordOverride(warnings, "enKeywordsLabel", enKeywordsLabel);
            ParagraphStyleRule base = cloneRule(spec.getRoleRules().get("enAbstractContent"));
            if (base != null) {
                base.setBold(true);
                base.setSourcePriority("TEXT_REQUIREMENT");
                spec.getRoleRules().put("enKeywordsLabel", base);
            }
        }
        if (shouldBackfillKeywordContent(spec.getRoleRules().get("enKeywordsContent")) && spec.getRoleRules().containsKey("enKeywordsLabel")) {
            warnKeywordOverride(warnings, "enKeywordsContent", spec.getRoleRules().get("enKeywordsContent"));
            ParagraphStyleRule base = cloneRule(spec.getRoleRules().get("enKeywordsLabel"));
            base.setBold(null);
            base.setSourcePriority("TEXT_REQUIREMENT");
            spec.getRoleRules().put("enKeywordsContent", base);
        }
    }

    private boolean shouldBackfillKeywordContent(ParagraphStyleRule rule) {
        return rule == null || (rule.getEastAsiaFont() == null && rule.getAsciiFont() == null && rule.getFontSizePt() == null);
    }

    private boolean shouldBackfillCnKeywordContent(ParagraphStyleRule rule) {
        return rule == null || rule.getEastAsiaFont() == null || rule.getFontSizePt() == null;
    }

    private boolean shouldBackfillEnKeywordLabel(ParagraphStyleRule rule) {
        return rule == null
                || rule.getAsciiFont() == null
                || rule.getFontSizePt() == null
                || "宋体".equals(rule.getAsciiFont())
                || "宋体".equals(rule.getLatinFont());
    }

    private void copySectionRole(FormatSpec spec, Map<String, ParagraphStyleRule> rules, String section, String role) {
        ParagraphStyleRule rule = spec.getSectionRules().get(section);
        if (rule != null) {
            rules.put(role, rule);
        }
    }

    private void putPreferredInstance(Map<String, ParagraphStyleRule> roleRules, String role, ParagraphStyleRule candidate) {
        ParagraphStyleRule current = roleRules.get(role);
        if (current != null && "TEXT_REQUIREMENT".equals(current.getSourcePriority())) {
            return;
        }
        if (current == null || instanceScore(candidate) > instanceScore(current)) {
            roleRules.put(role, candidate);
        }
    }

    private int instanceScore(ParagraphStyleRule rule) {
        int score = 0;
        if (rule.getStyleId() != null) {
            score += 10;
        }
        if (rule.getEastAsiaFont() != null) {
            score += 2;
        }
        if (rule.getAsciiFont() != null || rule.getHAnsiFont() != null) {
            score += 2;
        }
        if (rule.getFontSizePt() != null) {
            score += 2;
        }
        if (rule.getLineSpacingRule() != null || rule.getLineSpacingPt() != null || rule.getLineSpacingMultiple() != null) {
            score += 1;
        }
        return score;
    }

    private void backfillLegacyFields(FormatSpec spec, List<String> warnings) {
        ParagraphStyleRule body = spec.getRoleRules().get("body");
        if (body != null && isLegalBodyRule(body)) {
            spec.setBodyRule(DocxStyleResolver.merge(spec.getBodyRule(), body));
        } else if (body != null) {
            addIgnoredBodyCandidateWarning(warnings, "body", body.getStyleId());
        }
        backfillHeading(spec, 1, "heading1");
        backfillHeading(spec, 2, "heading2");
        backfillHeading(spec, 3, "heading3");
        putSectionIfPresent(spec, "title", "paperTitle");
        putSectionIfPresent(spec, "tocTitle", "tocTitle");
        putSectionIfPresent(spec, "abstractContent", "cnAbstractContent");
        putSectionIfPresent(spec, "englishAbstractContent", "enAbstractContent");
        putSectionIfPresent(spec, "keywordsLabel", "cnKeywordsLabel");
    }

    private boolean isForbiddenBodyCandidate(TemplateEvidence item) {
        String role = item.role();
        String styleId = item.styleId();
        if (isForbiddenBodyRole(role) || isForbiddenBodyStyleId(styleId)) {
            return true;
        }
        String evidenceText = item.text() == null ? "" : item.text();
        return styleId != null && styleId.startsWith("TOC") && evidenceText.contains("目录");
    }

    private boolean isLegalBodyRule(ParagraphStyleRule rule) {
        if (rule.getSourcePriority() != null && "TEXT_REQUIREMENT".equals(rule.getSourcePriority())) {
            return true;
        }
        if (rule.getStyleId() == null || rule.getStyleId().isBlank() || "Normal".equals(rule.getStyleId())) {
            return true;
        }
        if ("aff2".equals(rule.getStyleId())) {
            return true;
        }
        return rule.getStyleName() != null && rule.getStyleName().contains("论文正文");
    }

    private boolean isLegalBodyEvidence(TemplateEvidence item) {
        String text = item.text() == null ? "" : item.text().strip();
        if (looksLikeNonBodyRequirement(text)) {
            return false;
        }
        return "aff2".equals(item.styleId())
                || item.styleId() == null
                || item.styleId().isBlank()
                || "Normal".equals(item.styleId())
                || (item.styleName() != null && item.styleName().contains("论文正文"));
    }

    private boolean looksLikeNonBodyRequirement(String text) {
        if (text.isBlank()) {
            return false;
        }
        if (text.startsWith("正文文字") || text.startsWith("这是正文") || text.startsWith("Body paragraph")) {
            return false;
        }
        return text.startsWith("一级标题")
                || text.startsWith("二级")
                || text.startsWith("三级")
                || text.startsWith("标题")
                || text.startsWith("中文摘要")
                || text.startsWith("英文摘要")
                || text.startsWith("中文关键词")
                || text.startsWith("英文关键词")
                || text.startsWith("关键词")
                || text.startsWith("Keywords")
                || text.startsWith("目录");
    }

    private boolean isForbiddenBodyRole(String role) {
        return "tocTitle".equals(role)
                || "tocEntry1".equals(role)
                || "tocEntry2".equals(role)
                || "tocEntry3".equals(role)
                || "paperTitle".equals(role)
                || "heading1".equals(role)
                || "heading2".equals(role)
                || "heading3".equals(role);
    }

    private boolean isForbiddenBodyStyleId(String styleId) {
        return "TOC10".equals(styleId)
                || "TOC1".equals(styleId)
                || "TOC2".equals(styleId)
                || "TOC3".equals(styleId)
                || "tocTitle".equals(styleId)
                || "aff1".equals(styleId)
                || "aff3".equals(styleId)
                || "aff4".equals(styleId)
                || "aff5".equals(styleId)
                || "Heading1".equals(styleId)
                || "Heading2".equals(styleId)
                || "Heading3".equals(styleId);
    }

    private void addIgnoredBodyCandidateWarning(List<String> warnings, String role, String styleId) {
        String message = "ignored body candidate from role=" + role + " styleId=" + styleId;
        if (!warnings.contains(message)) {
            warnings.add(message);
        }
    }

    private void addIgnoredRoleCandidateWarning(List<String> warnings, String role, String styleId, String reason) {
        String message = "ignored role candidate from role=" + role + " styleId=" + styleId + " reason=" + reason;
        if (!warnings.contains(message)) {
            warnings.add(message);
        }
    }

    private void warnKeywordOverride(List<String> warnings, String role, ParagraphStyleRule rule) {
        if (rule == null || "TEXT_REQUIREMENT".equals(rule.getSourcePriority())) {
            return;
        }
        String message = "overrode low-confidence keyword candidate role=" + role + " styleId=" + rule.getStyleId();
        if (!warnings.contains(message)) {
            warnings.add(message);
        }
    }

    private void sanitizeHeadingRoleBold(FormatSpec spec, List<String> warnings) {
        clearImplicitHeadingBold(spec, spec.getRoleRules().get("heading2"), 2, "roleRules.heading2.bold", warnings);
        clearImplicitHeadingBold(spec, spec.getRoleRules().get("heading3"), 3, "roleRules.heading3.bold", warnings);
    }

    private void sanitizeHeadingRuleBold(FormatSpec spec, List<String> warnings) {
        clearImplicitHeadingBold(spec, spec.getHeadingRules().get(2), 2, "headingRules.2.bold", warnings);
        clearImplicitHeadingBold(spec, spec.getHeadingRules().get(3), 3, "headingRules.3.bold", warnings);
    }

    private void clearImplicitHeadingBold(FormatSpec spec, ParagraphStyleRule rule, int level, String fieldPath, List<String> warnings) {
        if (rule == null || rule.getBold() == null || hasExplicitHeadingBold(spec, level)) {
            return;
        }
        rule.setBold(null);
        String message = "ignored inherited/template bold for " + fieldPath;
        if (!warnings.contains(message)) {
            warnings.add(message);
        }
    }

    @SuppressWarnings("unchecked")
    private boolean hasExplicitHeadingBold(FormatSpec spec, int level) {
        Object textFields = spec.getExtractionReport().get("textFields");
        if (textFields instanceof List<?> list
                && list.contains("headingRules." + level + ".bold")) {
            return true;
        }
        Object aiRules = spec.getExtractionReport().get("aiRules");
        if (aiRules instanceof List<?> list) {
            String canonical = "headingRules[" + (level - 1) + "].bold";
            for (Object item : list) {
                if (item instanceof Map<?, ?> map && canonical.equals(map.get("fieldPath"))) {
                    return true;
                }
            }
        }
        return false;
    }

    private void backfillHeading(FormatSpec spec, int level, String role) {
        ParagraphStyleRule roleRule = spec.getRoleRules().get(role);
        if (roleRule == null) {
            return;
        }
        HeadingStyleRule heading = spec.getHeadingRules().computeIfAbsent(level, ignored -> {
            HeadingStyleRule created = new HeadingStyleRule();
            created.setLevel(level);
            return created;
        });
        copy(DocxStyleResolver.merge(heading, roleRule), heading);
        heading.setLevel(level);
    }

    private void putSectionIfPresent(FormatSpec spec, String section, String role) {
        ParagraphStyleRule roleRule = spec.getRoleRules().get(role);
        if (roleRule != null) {
            spec.getSectionRules().put(section, roleRule);
        }
    }

    private void recordParagraphConflicts(String prefix,
                                          ParagraphStyleRule preferred,
                                          ParagraphStyleRule other,
                                          List<Map<String, Object>> conflicts,
                                          String evidence) {
        compareParagraphField(prefix + ".asciiFont", preferred.getAsciiFont(), other.getAsciiFont(), conflicts, evidence);
        compareParagraphField(prefix + ".hAnsiFont", preferred.getHAnsiFont(), other.getHAnsiFont(), conflicts, evidence);
        compareParagraphField(prefix + ".eastAsiaFont", preferred.getEastAsiaFont(), other.getEastAsiaFont(), conflicts, evidence);
        compareParagraphField(prefix + ".fontSizePt", preferred.getFontSizePt(), other.getFontSizePt(), conflicts, evidence);
        compareParagraphField(prefix + ".lineSpacingRule", preferred.getLineSpacingRule(), other.getLineSpacingRule(), conflicts, evidence);
        compareParagraphField(prefix + ".lineSpacingPt", preferred.getLineSpacingPt(), other.getLineSpacingPt(), conflicts, evidence);
        compareParagraphField(prefix + ".alignment", preferred.getAlignment(), other.getAlignment(), conflicts, evidence);
        compareParagraphField(prefix + ".bold", preferred.getBold(), other.getBold(), conflicts, evidence);
    }

    private void compareParagraphField(String field,
                                       Object preferred,
                                       Object other,
                                       List<Map<String, Object>> conflicts,
                                       String evidence) {
        if (hasValue(preferred) && hasValue(other) && !equivalent(preferred, other)) {
            conflicts.add(conflict(field, "TEXT_REQUIREMENT", preferred, "TEMPLATE_INSTANCE", other, evidence));
        }
    }

    private void addSources(String prefix, ParagraphStyleRule rule, Map<String, Object> sources) {
        String source = rule.getSourcePriority() == null ? "TEMPLATE_INSTANCE" : rule.getSourcePriority();
        if (rule.getAsciiFont() != null) sources.put(prefix + ".asciiFont", source);
        if (rule.getHAnsiFont() != null) sources.put(prefix + ".hAnsiFont", source);
        if (rule.getEastAsiaFont() != null) sources.put(prefix + ".eastAsiaFont", source);
        if (rule.getFontSizePt() != null) sources.put(prefix + ".fontSizePt", source);
        if (rule.getLineSpacingRule() != null) sources.put(prefix + ".lineSpacingRule", source);
        if (rule.getLineSpacingPt() != null) sources.put(prefix + ".lineSpacingPt", source);
        if (rule.getLineSpacingMultiple() != null) sources.put(prefix + ".lineSpacingMultiple", source);
        if (rule.getAlignment() != null) sources.put(prefix + ".alignment", source);
        if (rule.getBold() != null) sources.put(prefix + ".bold", source);
    }

    private Map<String, Object> sample(TemplateEvidence item) {
        Map<String, Object> sample = new LinkedHashMap<>();
        sample.put("text", item.text().length() > 80 ? item.text().substring(0, 80) : item.text());
        sample.put("role", item.role());
        sample.put("styleId", item.styleId());
        sample.put("styleName", item.styleName());
        sample.put("partName", item.partName());
        sample.put("paragraphIndex", item.paragraphIndex());
        return sample;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> ensureMap(Map<String, Object> report, String key) {
        Object value = report.get(key);
        if (value instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        Map<String, Object> created = new LinkedHashMap<>();
        report.put(key, created);
        return created;
    }

    @SuppressWarnings("unchecked")
    private <T> List<T> ensureList(Map<String, Object> report, String key) {
        Object value = report.get(key);
        if (value instanceof List<?> list) {
            return (List<T>) list;
        }
        List<T> created = new ArrayList<>();
        report.put(key, created);
        return created;
    }

    private ParagraphStyleRule cloneRule(ParagraphStyleRule source) {
        if (source == null) {
            return null;
        }
        ParagraphStyleRule copy = new ParagraphStyleRule();
        copy(source, copy);
        copy.setHAnsiFont(source.getHAnsiFont());
        copy.setStyleId(source.getStyleId());
        copy.setStyleName(source.getStyleName());
        copy.setSourcePart(source.getSourcePart());
        copy.setSourcePriority(source.getSourcePriority());
        copy.setEvidenceText(source.getEvidenceText());
        return copy;
    }

    private void applyNamedStyleFallbacks(FormatSpec spec, DocxStyleSupport.Styles styles) {
        copyNamedStyle(styles, "论文题目", rule -> spec.getSectionRules().putIfAbsent("title", rule));
        copyNamedStyle(styles, "目录", rule -> spec.getSectionRules().putIfAbsent("tocTitle", rule));
        applyNamedHeadingStyle(spec, styles, 1, "一级标题");
        applyNamedHeadingStyle(spec, styles, 2, "二级标题");
        applyNamedHeadingStyle(spec, styles, 3, "三级标题");
    }

    private void applyNamedHeadingStyle(FormatSpec spec, DocxStyleSupport.Styles styles, int level, String styleNameToken) {
        copyNamedStyle(styles, styleNameToken, rule -> {
            HeadingStyleRule heading = spec.getHeadingRules().computeIfAbsent(level, ignored -> {
                HeadingStyleRule created = new HeadingStyleRule();
                created.setLevel(level);
                return created;
            });
            copy(rule, heading);
            heading.setLevel(level);
        });
    }

    private void copyNamedStyle(DocxStyleSupport.Styles styles, String nameToken, java.util.function.Consumer<ParagraphStyleRule> consumer) {
        String styleId = styles.styleIdByNameContaining(nameToken);
        if (styleId == null) {
            return;
        }
        ParagraphStyleRule style = DocxStyleSupport.merge(styles.docDefaults(), styles.style(styleId));
        if (style != null && hasParagraphRuleValue(style)) {
            consumer.accept(style);
        }
    }

    private boolean hasParagraphRuleValue(ParagraphStyleRule rule) {
        return rule.getAsciiFont() != null
                || rule.getEastAsiaFont() != null
                || rule.getFontSizePt() != null
                || rule.getLineSpacingMultiple() != null
                || rule.getLineSpacingRule() != null
                || rule.getLineSpacingPt() != null
                || rule.getSpaceBeforePt() != null
                || rule.getSpaceAfterPt() != null
                || rule.getFirstLineIndentMm() != null
                || rule.getAlignment() != null
                || rule.getBold() != null
                || rule.getLatinFont() != null;
    }

    private List<String> sectionRuleKeys() {
        return List.of("title", "tocTitle", "abstractLabel", "abstractContent", "keywordsLabel", "englishAbstractContent");
    }

    private void mergePage(RequirementRuleExtractionResult fixedText,
                           FormatSpec target,
                           FormatSpec aiSpec,
                           Set<String> aiFields,
                           FormatSpec ooxmlSpec,
                           List<String> fallbackFields,
                           List<Map<String, Object>> conflicts) {
        mergeField(fixedText, "pageRule.pageWidthMm", "pageRule.pageWidthMm", target.getPageRule(), aiSpec.getPageRule(), aiFields, ooxmlSpec.getPageRule(), PageRule::getPageWidthMm, PageRule::setPageWidthMm, fallbackFields, conflicts);
        mergeField(fixedText, "pageRule.pageHeightMm", "pageRule.pageHeightMm", target.getPageRule(), aiSpec.getPageRule(), aiFields, ooxmlSpec.getPageRule(), PageRule::getPageHeightMm, PageRule::setPageHeightMm, fallbackFields, conflicts);
        mergeField(fixedText, "pageRule.marginTopMm", "pageRule.marginTopMm", target.getPageRule(), aiSpec.getPageRule(), aiFields, ooxmlSpec.getPageRule(), PageRule::getMarginTopMm, PageRule::setMarginTopMm, fallbackFields, conflicts);
        mergeField(fixedText, "pageRule.marginRightMm", "pageRule.marginRightMm", target.getPageRule(), aiSpec.getPageRule(), aiFields, ooxmlSpec.getPageRule(), PageRule::getMarginRightMm, PageRule::setMarginRightMm, fallbackFields, conflicts);
        mergeField(fixedText, "pageRule.marginBottomMm", "pageRule.marginBottomMm", target.getPageRule(), aiSpec.getPageRule(), aiFields, ooxmlSpec.getPageRule(), PageRule::getMarginBottomMm, PageRule::setMarginBottomMm, fallbackFields, conflicts);
        mergeField(fixedText, "pageRule.marginLeftMm", "pageRule.marginLeftMm", target.getPageRule(), aiSpec.getPageRule(), aiFields, ooxmlSpec.getPageRule(), PageRule::getMarginLeftMm, PageRule::setMarginLeftMm, fallbackFields, conflicts);
        mergeField(fixedText, "pageRule.mirrorMargins", "pageRule.mirrorMargins", target.getPageRule(), aiSpec.getPageRule(), aiFields, ooxmlSpec.getPageRule(), PageRule::getMirrorMargins, PageRule::setMirrorMargins, fallbackFields, conflicts);
        mergeField(fixedText, "pageRule.duplexPrint", "pageRule.duplexPrint", target.getPageRule(), aiSpec.getPageRule(), aiFields, ooxmlSpec.getPageRule(), PageRule::getDuplexPrint, PageRule::setDuplexPrint, fallbackFields, conflicts);
        mergeField(fixedText, "pageRule.insideMarginMm", "pageRule.insideMarginMm", target.getPageRule(), aiSpec.getPageRule(), aiFields, ooxmlSpec.getPageRule(), PageRule::getInsideMarginMm, PageRule::setInsideMarginMm, fallbackFields, conflicts);
        mergeField(fixedText, "pageRule.outsideMarginMm", "pageRule.outsideMarginMm", target.getPageRule(), aiSpec.getPageRule(), aiFields, ooxmlSpec.getPageRule(), PageRule::getOutsideMarginMm, PageRule::setOutsideMarginMm, fallbackFields, conflicts);
        mergeField(fixedText, "pageRule.gutterMm", "pageRule.gutterMm", target.getPageRule(), aiSpec.getPageRule(), aiFields, ooxmlSpec.getPageRule(), PageRule::getGutterMm, PageRule::setGutterMm, fallbackFields, conflicts);
        mergeField(fixedText, "pageRule.headerDistanceMm", "pageRule.headerDistanceMm", target.getPageRule(), aiSpec.getPageRule(), aiFields, ooxmlSpec.getPageRule(), PageRule::getHeaderDistanceMm, PageRule::setHeaderDistanceMm, fallbackFields, conflicts);
        mergeField(fixedText, "pageRule.footerDistanceMm", "pageRule.footerDistanceMm", target.getPageRule(), aiSpec.getPageRule(), aiFields, ooxmlSpec.getPageRule(), PageRule::getFooterDistanceMm, PageRule::setFooterDistanceMm, fallbackFields, conflicts);
    }

    private void mergeHeaderFooter(RequirementRuleExtractionResult fixedText,
                                   FormatSpec target,
                                   FormatSpec aiSpec,
                                   Set<String> aiFields,
                                   FormatSpec ooxmlSpec,
                                   List<String> fallbackFields,
                                   List<Map<String, Object>> conflicts) {
        mergeField(fixedText, "headerFooterRule.headerText", "headerFooterRule.headerText", target.getHeaderFooterRule(), aiSpec.getHeaderFooterRule(), aiFields, ooxmlSpec.getHeaderFooterRule(), HeaderFooterRule::getHeaderText, HeaderFooterRule::setHeaderText, fallbackFields, conflicts);
        mergeField(fixedText, "headerFooterRule.headerCentered", "headerFooterRule.headerCentered", target.getHeaderFooterRule(), aiSpec.getHeaderFooterRule(), aiFields, ooxmlSpec.getHeaderFooterRule(), HeaderFooterRule::isHeaderCentered, HeaderFooterRule::setHeaderCentered, fallbackFields, conflicts);
        mergeField(fixedText, "headerFooterRule.headerFontEastAsia", "headerFooterRule.headerFontEastAsia", target.getHeaderFooterRule(), aiSpec.getHeaderFooterRule(), aiFields, ooxmlSpec.getHeaderFooterRule(), HeaderFooterRule::getHeaderFontEastAsia, HeaderFooterRule::setHeaderFontEastAsia, fallbackFields, conflicts);
        mergeField(fixedText, "headerFooterRule.headerFontSizePt", "headerFooterRule.headerFontSizePt", target.getHeaderFooterRule(), aiSpec.getHeaderFooterRule(), aiFields, ooxmlSpec.getHeaderFooterRule(), HeaderFooterRule::getHeaderFontSizePt, HeaderFooterRule::setHeaderFontSizePt, fallbackFields, conflicts);
        mergeField(fixedText, "headerFooterRule.footerPageNumber", "headerFooterRule.footerPageNumber", target.getHeaderFooterRule(), aiSpec.getHeaderFooterRule(), aiFields, ooxmlSpec.getHeaderFooterRule(), HeaderFooterRule::isFooterPageNumber, HeaderFooterRule::setFooterPageNumber, fallbackFields, conflicts);
        mergeField(fixedText, "headerFooterRule.footerCentered", "headerFooterRule.footerCentered", target.getHeaderFooterRule(), aiSpec.getHeaderFooterRule(), aiFields, ooxmlSpec.getHeaderFooterRule(), HeaderFooterRule::isFooterCentered, HeaderFooterRule::setFooterCentered, fallbackFields, conflicts);
    }

    private void mergeParagraph(RequirementRuleExtractionResult fixedText,
                                String fixedPrefix,
                                String canonicalPrefix,
                                ParagraphStyleRule target,
                                ParagraphStyleRule ai,
                                Set<String> aiFields,
                                ParagraphStyleRule ooxml,
                                List<String> fallbackFields,
                                List<Map<String, Object>> conflicts) {
        mergeParagraph(fixedText, fixedPrefix, canonicalPrefix, target, ai, aiFields, ooxml, fallbackFields, conflicts, false);
    }

    private void mergeParagraph(RequirementRuleExtractionResult fixedText,
                                String fixedPrefix,
                                String canonicalPrefix,
                                ParagraphStyleRule target,
                                ParagraphStyleRule ai,
                                Set<String> aiFields,
                                ParagraphStyleRule ooxml,
                                List<String> fallbackFields,
                                List<Map<String, Object>> conflicts,
                                boolean suppressOoxmlBoldFallback) {
        mergeField(fixedText, fixedPrefix + ".asciiFont", canonicalPrefix + ".asciiFont", target, ai, aiFields, ooxml, ParagraphStyleRule::getAsciiFont, ParagraphStyleRule::setAsciiFont, fallbackFields, conflicts);
        mergeField(fixedText, fixedPrefix + ".eastAsiaFont", canonicalPrefix + ".eastAsiaFont", target, ai, aiFields, ooxml, ParagraphStyleRule::getEastAsiaFont, ParagraphStyleRule::setEastAsiaFont, fallbackFields, conflicts);
        mergeField(fixedText, fixedPrefix + ".fontSizePt", canonicalPrefix + ".fontSizePt", target, ai, aiFields, ooxml, ParagraphStyleRule::getFontSizePt, ParagraphStyleRule::setFontSizePt, fallbackFields, conflicts);
        mergeField(fixedText, fixedPrefix + ".lineSpacingMultiple", canonicalPrefix + ".lineSpacingMultiple", target, ai, aiFields, ooxml, ParagraphStyleRule::getLineSpacingMultiple, ParagraphStyleRule::setLineSpacingMultiple, fallbackFields, conflicts);
        boolean hasPreferredFixedLineSpacing = fixedText.hasField(fixedPrefix + ".lineSpacingRule")
                || fixedText.hasField(fixedPrefix + ".lineSpacingPt")
                || aiFields.contains(canonicalPrefix + ".lineSpacingRule")
                || aiFields.contains(canonicalPrefix + ".lineSpacingPt");
        if (target.getLineSpacingMultiple() == null || hasPreferredFixedLineSpacing) {
            if (hasPreferredFixedLineSpacing) {
                target.setLineSpacingMultiple(null);
            }
            mergeField(fixedText, fixedPrefix + ".lineSpacingRule", canonicalPrefix + ".lineSpacingRule", target, ai, aiFields, ooxml, ParagraphStyleRule::getLineSpacingRule, ParagraphStyleRule::setLineSpacingRule, fallbackFields, conflicts);
            mergeField(fixedText, fixedPrefix + ".lineSpacingPt", canonicalPrefix + ".lineSpacingPt", target, ai, aiFields, ooxml, ParagraphStyleRule::getLineSpacingPt, ParagraphStyleRule::setLineSpacingPt, fallbackFields, conflicts);
        }
        mergeField(fixedText, fixedPrefix + ".spaceBeforePt", canonicalPrefix + ".spaceBeforePt", target, ai, aiFields, ooxml, ParagraphStyleRule::getSpaceBeforePt, ParagraphStyleRule::setSpaceBeforePt, fallbackFields, conflicts);
        mergeField(fixedText, fixedPrefix + ".spaceAfterPt", canonicalPrefix + ".spaceAfterPt", target, ai, aiFields, ooxml, ParagraphStyleRule::getSpaceAfterPt, ParagraphStyleRule::setSpaceAfterPt, fallbackFields, conflicts);
        mergeField(fixedText, fixedPrefix + ".firstLineIndentMm", canonicalPrefix + ".firstLineIndentMm", target, ai, aiFields, ooxml, ParagraphStyleRule::getFirstLineIndentMm, ParagraphStyleRule::setFirstLineIndentMm, fallbackFields, conflicts);
        mergeField(fixedText, fixedPrefix + ".alignment", canonicalPrefix + ".alignment", target, ai, aiFields, ooxml, ParagraphStyleRule::getAlignment, ParagraphStyleRule::setAlignment, fallbackFields, conflicts);
        ParagraphStyleRule boldOoxml = suppressOoxmlBoldFallback && !fixedText.hasField(fixedPrefix + ".bold") && !aiFields.contains(canonicalPrefix + ".bold")
                ? null
                : ooxml;
        mergeField(fixedText, fixedPrefix + ".bold", canonicalPrefix + ".bold", target, ai, aiFields, boldOoxml, ParagraphStyleRule::getBold, ParagraphStyleRule::setBold, fallbackFields, conflicts);
        mergeField(fixedText, fixedPrefix + ".latinFont", canonicalPrefix + ".latinFont", target, ai, aiFields, ooxml, ParagraphStyleRule::getLatinFont, ParagraphStyleRule::setLatinFont, fallbackFields, conflicts);
    }

    private <T, V> void mergeField(RequirementRuleExtractionResult fixedText,
                                   String fixedPath,
                                   String canonicalPath,
                                   T target,
                                   T ai,
                                   Set<String> aiFields,
                                   T ooxml,
                                   Function<T, V> getter,
                                   BiConsumer<T, V> setter,
                                   List<String> fallbackFields,
                                   List<Map<String, Object>> conflicts) {
        V fixedValue = getter.apply(target);
        V aiValue = ai == null ? null : getter.apply(ai);
        V ooxmlValue = ooxml == null ? null : getter.apply(ooxml);
        boolean hasFixed = fixedText.hasField(fixedPath);
        boolean hasAi = aiFields.contains(canonicalPath);

        if (hasFixed) {
            if (hasAi && !equivalent(fixedValue, aiValue)) {
                conflicts.add(conflict(canonicalPath, "TEXT_REQUIREMENT", fixedValue, "AI_REQUIREMENT", aiValue, null));
            }
            if (hasValue(ooxmlValue) && !equivalent(fixedValue, ooxmlValue)) {
                conflicts.add(conflict(canonicalPath, "TEXT_REQUIREMENT", fixedValue, "OOXML_FALLBACK", ooxmlValue, null));
            }
            return;
        }
        if (hasAi) {
            setter.accept(target, aiValue);
            if (hasValue(ooxmlValue) && !equivalent(aiValue, ooxmlValue)) {
                conflicts.add(conflict(canonicalPath, "AI_REQUIREMENT", aiValue, "OOXML_FALLBACK", ooxmlValue, null));
            }
            return;
        }
        if (hasValue(ooxmlValue)) {
            setter.accept(target, ooxmlValue);
            fallbackFields.add(canonicalPath);
        }
    }

    private boolean hasValue(Object value) {
        if (value == null) {
            return false;
        }
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof String text) {
            return !text.isBlank();
        }
        return true;
    }

    private boolean equivalent(Object left, Object right) {
        if (left instanceof Number leftNumber && right instanceof Number rightNumber) {
            return Math.abs(leftNumber.doubleValue() - rightNumber.doubleValue()) <= NUMERIC_TOLERANCE;
        }
        return Objects.equals(left, right);
    }

    private Map<String, Object> conflict(String fieldPath,
                                         String preferredSource,
                                         Object preferredValue,
                                         String otherSource,
                                         Object otherValue,
                                         String evidence) {
        Map<String, Object> conflict = new LinkedHashMap<>();
        conflict.put("fieldPath", fieldPath);
        conflict.put("field", fieldPath);
        conflict.put("preferredSource", preferredSource);
        conflict.put("preferredValue", preferredValue);
        conflict.put("otherSource", otherSource);
        conflict.put("otherValue", otherValue);
        if ("TEXT_REQUIREMENT".equals(preferredSource) && "OOXML_FALLBACK".equals(otherSource)) {
            conflict.put("textValue", preferredValue);
            conflict.put("ooxmlValue", otherValue);
        }
        conflict.put("evidence", evidence);
        return conflict;
    }

    private Map<String, Object> ruleMap(AiExtractedRule rule, String fieldPath) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("fieldPath", fieldPath);
        map.put("value", rule.value());
        map.put("confidence", rule.confidence());
        map.put("evidence", rule.evidence());
        map.put("source", rule.source());
        return map;
    }

    private PageRule readPageRule(String documentXml, String settingsXml) {
        PageRule page = new PageRule();
        if (documentXml == null) {
            return page;
        }
        Document document = DocxPackageReader.parse(documentXml);
        Element section = DocxPackageReader.first(document.getDocumentElement(), "w:sectPr");
        Element size = DocxPackageReader.first(section, "w:pgSz");
        Element margins = DocxPackageReader.first(section, "w:pgMar");
        page.setPageWidthMm(DocxPackageReader.twipsToMm(DocxPackageReader.attr(size, "w")));
        page.setPageHeightMm(DocxPackageReader.twipsToMm(DocxPackageReader.attr(size, "h")));
        page.setMarginTopMm(DocxPackageReader.twipsToMm(DocxPackageReader.attr(margins, "top")));
        page.setMarginRightMm(DocxPackageReader.twipsToMm(DocxPackageReader.attr(margins, "right")));
        page.setMarginBottomMm(DocxPackageReader.twipsToMm(DocxPackageReader.attr(margins, "bottom")));
        page.setMarginLeftMm(DocxPackageReader.twipsToMm(DocxPackageReader.attr(margins, "left")));
        page.setGutterMm(DocxPackageReader.twipsToMm(DocxPackageReader.attr(margins, "gutter")));
        page.setHeaderDistanceMm(DocxPackageReader.twipsToMm(DocxPackageReader.attr(margins, "header")));
        page.setFooterDistanceMm(DocxPackageReader.twipsToMm(DocxPackageReader.attr(margins, "footer")));
        if (settingsXml != null && settingsXml.contains("mirrorMargins")) {
            page.setMirrorMargins(true);
            page.setInsideMarginMm(page.getMarginLeftMm());
            page.setOutsideMarginMm(page.getMarginRightMm());
        }
        return page;
    }

    private HeaderFooterRule readHeaderFooter(Map<String, String> parts, DocxStyleSupport.Styles styles) {
        HeaderFooterRule rule = new HeaderFooterRule();
        String headerXml = parts.get("word/header1.xml");
        if (headerXml != null) {
            Document header = DocxPackageReader.parse(headerXml);
            Element paragraph = DocxPackageReader.first(header.getDocumentElement(), "w:p");
            ParagraphStyleRule effective = effectiveParagraphRule(paragraph, styles);
            rule.setHeaderText(DocxPackageReader.text(header.getDocumentElement()));
            rule.setHeaderCentered("CENTER".equals(effective.getAlignment()));
            rule.setHeaderFontEastAsia(effective.getEastAsiaFont());
            rule.setHeaderFontSizePt(effective.getFontSizePt());
        }
        String footerXml = parts.get("word/footer1.xml");
        if (footerXml != null) {
            Document footer = DocxPackageReader.parse(footerXml);
            Element paragraph = DocxPackageReader.first(footer.getDocumentElement(), "w:p");
            ParagraphStyleRule effective = effectiveParagraphRule(paragraph, styles);
            rule.setFooterPageNumber(DocxPackageReader.hasField(footer.getDocumentElement(), "PAGE"));
            rule.setFooterCentered("CENTER".equals(effective.getAlignment()));
        }
        return rule;
    }

    private ParagraphStyleRule effectiveParagraphRule(Element paragraph, DocxStyleSupport.Styles styles) {
        String styleId = paragraphStyleId(paragraph);
        return DocxStyleSupport.merge(styles.style(styleId), DocxStyleSupport.readRule(paragraph));
    }

    private String paragraphStyleId(Element paragraph) {
        Element pPr = DocxPackageReader.first(paragraph, "w:pPr");
        Element pStyle = DocxPackageReader.first(pPr, "w:pStyle");
        return DocxPackageReader.attr(pStyle, "val");
    }

    private void copy(ParagraphStyleRule source, ParagraphStyleRule target) {
        target.setAsciiFont(source.getAsciiFont());
        target.setEastAsiaFont(source.getEastAsiaFont());
        target.setFontSizePt(source.getFontSizePt());
        target.setLineSpacingMultiple(source.getLineSpacingMultiple());
        target.setLineSpacingRule(source.getLineSpacingRule());
        target.setLineSpacingPt(source.getLineSpacingPt());
        target.setSpaceBeforePt(source.getSpaceBeforePt());
        target.setSpaceAfterPt(source.getSpaceAfterPt());
        target.setFirstLineIndentMm(source.getFirstLineIndentMm());
        target.setAlignment(source.getAlignment());
        target.setBold(source.getBold());
        target.setLatinFont(source.getLatinFont());
        target.setSource(source.getSource());
    }

    private static PaperFormatAiExtractionProperties defaultAiProperties() {
        PaperFormatAiExtractionProperties properties = new PaperFormatAiExtractionProperties();
        properties.setEnabled(false);
        return properties;
    }
}
