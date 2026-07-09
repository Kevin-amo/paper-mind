package com.lqr.papermind.paperformat.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/** Configuration for optional AI-assisted paper format template extraction. */
@Data
@ConfigurationProperties(prefix = "paperformat.ai-extraction")
public class PaperFormatAiExtractionProperties {
    private boolean enabled = false;
    private String provider = "existing";
    private String model;
    private Duration timeout = Duration.ofSeconds(10);
    private int maxInputChars = 12000;
    private double minConfidence = 0.70;
}
