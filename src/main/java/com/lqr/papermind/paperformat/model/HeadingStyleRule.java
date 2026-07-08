package com.lqr.papermind.paperformat.model;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class HeadingStyleRule extends ParagraphStyleRule {
    private int level;
}
