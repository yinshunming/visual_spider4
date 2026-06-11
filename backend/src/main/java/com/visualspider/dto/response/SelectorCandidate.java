package com.visualspider.dto.response;

import java.util.List;

public record SelectorCandidate(String selector, int matchCount, List<String> samples) {
}
