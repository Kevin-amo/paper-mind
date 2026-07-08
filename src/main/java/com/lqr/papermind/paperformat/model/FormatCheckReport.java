package com.lqr.papermind.paperformat.model;

import lombok.Data;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Data
public class FormatCheckReport {
    private String status = "PASSED";
    private Map<String, Integer> summary = new LinkedHashMap<>();
    private List<FormatViolation> violations = new ArrayList<>();
}
