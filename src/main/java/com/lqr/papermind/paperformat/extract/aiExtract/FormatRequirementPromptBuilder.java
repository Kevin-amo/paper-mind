package com.lqr.papermind.paperformat.extract.aiExtract;

import com.lqr.papermind.ai.service.PromptConstructionService;
import com.lqr.papermind.paperformat.extract.DocxTemplateEvidence;
import org.springframework.stereotype.Component;

import java.util.List;

/** 构建用于 AI 辅助模板格式需求提取的提示词。 */
@Component
public class FormatRequirementPromptBuilder {

    private static final List<String> ALLOWED_FIELDS = List.of(
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

    public PromptConstructionService.Prompt build(AiRequirementExtractionInput input) {
        String system = "你是毕业设计 DOCX 模板格式规则抽取助手。只从输入证据中抽取，不要编造。只输出严格 JSON 对象，禁止 Markdown、代码围栏和解释文字。";
        String user = "请从证据中抽取可结构化表达的 FormatSpec 草稿规则。\n"
                + "只允许使用这些 fieldPath：" + ALLOWED_FIELDS + "\n"
                + "单位统一：cm 转 mm；磅/pt 保持 pt；小五/小5=9，五号/5号=10.5，小四=12，四号=14，小三=15，三号=16，小二=18，二号=22。\n"
                + "标题、目录标题、摘要标签、摘要内容、关键词标签、Abstract/英文摘要内容、Keywords、正文、页眉、页脚是不同对象；优先把模板标注中的这些对象写入 sectionRules.*，不要混入 bodyRule。识别居中、两端对齐、加粗、宋体、黑体、Times New Roman、固定值16磅、段前段后0磅。\n"
                + "不确定时降低 confidence 并写入 notes。当前模型无法表达的规则写入 referenceRequirements 或 notes，不要放入 rules。\n"
                + "输出 JSON 形如：{\"rules\":[{\"fieldPath\":\"headerFooterRule.headerText\",\"value\":\"...\",\"confidence\":0.92,\"evidence\":\"...\",\"source\":\"TEXT_BOX\"}],\"referenceRequirements\":[],\"notes\":[]}\n\n"
                + evidenceText(input.evidence(), input.maxInputChars());
        return new PromptConstructionService.Prompt(system, user);
    }

    private String evidenceText(DocxTemplateEvidence evidence, int limit) {
        String text = "BODY_PARAGRAPHS:\n" + evidence.bodyParagraphs()
                + "\nTEXT_BOX_TEXTS:\n" + evidence.textBoxTexts()
                + "\nCOMMENT_TEXTS:\n" + evidence.commentTexts()
                + "\nHEADER_TEXTS:\n" + evidence.headerTexts()
                + "\nFOOTER_TEXTS:\n" + evidence.footerTexts()
                + "\nOOXML_SPEC:\n" + evidence.ooxmlSpec();
        return text.length() <= limit ? text : text.substring(0, limit) + "\n[TRUNCATED]";
    }
}
