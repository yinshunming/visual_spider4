package com.visualspider.service;

import com.visualspider.dto.response.SelectorCandidate;
import com.visualspider.dto.response.SelectorPairResponse;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("SelectorCraftService")
class SelectorCraftServiceTest {

    private final SelectorCraftService service = new SelectorCraftService();

    @Nested
    @DisplayName("craft(element, document)")
    class Craft {

        @Test
        @DisplayName("生成 css + xpath + matchCount + samples")
        void generatesBothWithCountsAndSamples() {
            Document doc = Jsoup.parse(
                    "<html><body><div class='title'>示例一</div><div class='title'>示例二</div></body></html>");
            Element target = doc.select("div.title").first();
            SelectorPairResponse pair = service.craft(target, doc);

            assertThat(pair.css()).isNotNull();
            assertThat(pair.css().selector()).isNotEmpty();
            assertThat(pair.css().matchCount()).isGreaterThanOrEqualTo(1);
            assertThat(pair.css().samples()).isNotEmpty();

            assertThat(pair.xpath()).isNotNull();
            assertThat(pair.xpath().selector()).isNotEmpty();
            assertThat(pair.xpath().matchCount()).isGreaterThanOrEqualTo(0);
        }

        @Test
        @DisplayName("element 为 null 时返回 css/xpath 都为 null")
        void nullElementReturnsNulls() {
            Document doc = Jsoup.parse("<html><body></body></html>");
            SelectorPairResponse pair = service.craft(null, doc);

            assertThat(pair.css()).isNull();
            assertThat(pair.xpath()).isNull();
        }

        @Test
        @DisplayName("无文本元素用 outerHtml 前 80 字符")
        void noTextUsesOuterHtml() {
            Document doc = Jsoup.parse(
                    "<html><body><svg viewBox='0 0 100 100'><rect x='0' y='0' width='50' height='50'/></svg></body></html>");
            Element target = doc.selectFirst("rect");
            SelectorPairResponse pair = service.craft(target, doc);
            assertThat(pair.css()).isNotNull();
            assertThat(pair.css().samples()).isNotEmpty();
            assertThat(pair.css().samples().get(0)).contains("<rect");
        }
    }
}
