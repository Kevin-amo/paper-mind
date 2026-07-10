package com.lqr.papermind.paperformat.config;

import com.lqr.papermind.ai.service.LlmService;
import com.lqr.papermind.paperformat.extract.aiExtract.AiFormatRequirementExtractor;
import com.lqr.papermind.paperformat.extract.aiExtract.AiRequirementResultMapper;
import com.lqr.papermind.paperformat.extract.aiExtract.DisabledFormatRequirementAiExtractor;
import com.lqr.papermind.paperformat.extract.aiExtract.FormatRequirementAiExtractor;
import com.lqr.papermind.paperformat.extract.aiExtract.FormatRequirementPromptBuilder;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Bean wiring for optional AI-assisted format requirement extraction. */
@Configuration
public class PaperFormatAiExtractionConfiguration {

    @Bean
    @ConditionalOnMissingBean(FormatRequirementAiExtractor.class)
    public FormatRequirementAiExtractor formatRequirementAiExtractor(PaperFormatAiExtractionProperties properties,
                                                                     ObjectProvider<LlmService> llmService,
                                                                     FormatRequirementPromptBuilder promptBuilder,
                                                                     AiRequirementResultMapper resultMapper) {
        if (!properties.isEnabled()) {
            return new DisabledFormatRequirementAiExtractor();
        }
        return new AiFormatRequirementExtractor(llmService.getObject(), promptBuilder, resultMapper);
    }
}
