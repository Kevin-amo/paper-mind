package com.lqr.papermind.paperformat.model;

import lombok.Data;

/**
 * 格式违规记录，描述一处格式不符合要求的具体信息
 */
@Data
public class FormatViolation {
    /** 违规编码（如PAGE_SIZE、HEADER_TEXT等） */
    private String code;
    /** 严重级别（ERROR/WARNING/REVIEW） */
    private String severity;
    /** 违规位置描述 */
    private String location;
    /** 期望值 */
    private String expected;
    /** 实际值 */
    private String actual;
    /** 违规描述信息 */
    private String message;
    /** 修复建议 */
    private String suggestion;

    /**
     * 创建格式违规记录
     *
     * @param code       违规编码
     * @param severity   严重级别
     * @param location   违规位置
     * @param expected   期望值
     * @param actual     实际值
     * @param message    描述信息
     * @param suggestion 修复建议
     * @return 格式违规对象
     */
    public static FormatViolation of(String code,
                                     String severity,
                                     String location,
                                     String expected,
                                     String actual,
                                     String message,
                                     String suggestion) {
        FormatViolation violation = new FormatViolation();
        violation.setCode(code);
        violation.setSeverity(severity);
        violation.setLocation(location);
        violation.setExpected(expected);
        violation.setActual(actual);
        violation.setMessage(message);
        violation.setSuggestion(suggestion);
        return violation;
    }
}
