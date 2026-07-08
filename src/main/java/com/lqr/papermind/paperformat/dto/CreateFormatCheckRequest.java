package com.lqr.papermind.paperformat.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record CreateFormatCheckRequest(
        @NotNull UUID templateId,
        String sourceId
) {
}
