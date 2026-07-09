package com.lqr.papermind.paperformat.model;

import lombok.Data;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 格式检查报告，包含检查状态、违规统计和违规详情列表
 */
@Data
public class FormatCheckReport {
    /** 检查状态（PASSED/FAILED） */
    private String status = "PASSED";
    /** 各严重级别违规数量统计 */
    private Map<String, Integer> summary = new LinkedHashMap<>();
    /** 违规详情列表 */
    private List<FormatViolation> violations = new ArrayList<>();
}
