package com.visualspider.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 实体层冒烟:CrawlConfig 在 M4 起必须承载 startUrl 字段。
 * 1.1 RED 阶段:`getStartUrl()` 当前不存在 → 编译失败 → 视为红。
 */
@DisplayName("CrawlConfig")
class CrawlConfigTest {

    @Test
    @DisplayName("getStartUrl 返回创建时设置的值")
    void getStartUrl_returnsSetValue() {
        CrawlConfig config = new CrawlConfig();
        config.setStartUrl("https://example.com/list");
        assertThat(config.getStartUrl()).isEqualTo("https://example.com/list");
    }
}