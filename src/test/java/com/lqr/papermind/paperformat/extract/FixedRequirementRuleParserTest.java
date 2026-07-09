package com.lqr.papermind.paperformat.extract;

import com.lqr.papermind.paperformat.model.FormatSpec;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class FixedRequirementRuleParserTest {

    @Test
    void parseShouldRecognizeHeadingLineSpacingVariantsAndFixedSubheadings() {
        RequirementRuleExtractionResult result = new FixedRequirementRuleParser().parse(List.of(
                "一级标题：小四号黑体（加粗），多倍行距 1.35。",
                "二级和三级节标题不用空行；中文宋体，英文Times New Roman，五号，两端对齐，固定值，16磅，段前、段后均为0磅。"
        ));

        FormatSpec spec = result.formatSpec();
        assertThat(spec.getHeadingRules().get(1).getLineSpacingMultiple()).isEqualTo(1.35);
        assertThat(spec.getHeadingRules().get(1).getLineSpacingRule()).isNull();
        assertThat(spec.getHeadingRules().get(2).getLineSpacingRule()).isEqualTo("FIXED");
        assertThat(spec.getHeadingRules().get(2).getLineSpacingPt()).isEqualTo(16.0);
        assertThat(spec.getHeadingRules().get(2).getLineSpacingMultiple()).isNull();
        assertThat(spec.getHeadingRules().get(2).getBold()).isNull();
        assertThat(spec.getHeadingRules().get(3).getLineSpacingRule()).isEqualTo("FIXED");
        assertThat(spec.getHeadingRules().get(3).getLineSpacingPt()).isEqualTo(16.0);
        assertThat(spec.getHeadingRules().get(3).getLineSpacingMultiple()).isNull();
        assertThat(spec.getHeadingRules().get(3).getBold()).isNull();
        assertThat(result.hasField("headingRules.2.lineSpacingRule")).isTrue();
        assertThat(result.hasField("headingRules.2.lineSpacingPt")).isTrue();
        assertThat(result.hasField("headingRules.3.lineSpacingRule")).isTrue();
        assertThat(result.hasField("headingRules.3.lineSpacingPt")).isTrue();
    }
}
