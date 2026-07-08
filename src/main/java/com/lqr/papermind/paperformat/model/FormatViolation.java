package com.lqr.papermind.paperformat.model;

import lombok.Data;

@Data
public class FormatViolation {
    private String code;
    private String severity;
    private String location;
    private String expected;
    private String actual;
    private String message;
    private String suggestion;

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
