package com.lqr.papermind.paperformat.dto;

import com.lqr.papermind.paperformat.model.FormatSpec;

public record PatchFormatSpecRequest(
        FormatSpec formatSpec,
        Boolean confirmed
) {
}
