package com.visualspider.dto.ws;

import java.util.List;

public record PreviewResultPayload(int matchCount, List<String> samples) {
}
