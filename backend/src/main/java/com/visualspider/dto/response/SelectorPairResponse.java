package com.visualspider.dto.response;

/**
 * 可视化造字段的候选选择器对。
 * nested=true 表示命中的是 iframe / shadow host,本期不支持深入选择(见 openspec page-visual-selection)。
 */
public record SelectorPairResponse(SelectorCandidate css, SelectorCandidate xpath, boolean nested) {
    public SelectorPairResponse(SelectorCandidate css, SelectorCandidate xpath) {
        this(css, xpath, false);
    }
}
