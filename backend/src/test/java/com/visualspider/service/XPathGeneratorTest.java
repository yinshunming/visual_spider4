package com.visualspider.service;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("XPathGenerator")
class XPathGeneratorTest {

    @Nested
    @DisplayName("id 终止")
    class WithId {

        @Test
        @DisplayName("有 id 终止于 //*[@id='x']")
        void idTerminates() {
            Document doc = Jsoup.parse("<html><body><div id='root'><span>hi</span></div></body></html>");
            Element span = doc.selectFirst("span");
            String xpath = XPathGenerator.generate(span, doc);
            assertThat(xpath).isEqualTo("//*[@id='root']//span");
        }
    }

    @Nested
    @DisplayName("唯一 tag 不带谓词")
    class UniqueTag {

        @Test
        @DisplayName("同级中唯一 tag 不写 [n] 谓词")
        void uniqueTagNoPredicate() {
            Document doc = Jsoup.parse("<html><body><div><h1>title</h1></div></body></html>");
            Element h1 = doc.selectFirst("h1");
            String xpath = XPathGenerator.generate(h1, doc);
            assertThat(xpath).isEqualTo("//div/h1");
        }
    }

    @Nested
    @DisplayName("同级重复带 [n] 谓词")
    class RepeatedSiblings {

        @Test
        @DisplayName("同级重复元素带 1-based 索引")
        void repeatedSiblingsGetIndex() {
            Document doc = Jsoup.parse("<html><body><ul><li>a</li><li>b</li><li>c</li></ul></body></html>");
            Element secondLi = doc.select("li").get(1);
            String xpath = XPathGenerator.generate(secondLi, doc);
            assertThat(xpath).isEqualTo("//ul/li[2]");
        }
    }

    @Nested
    @DisplayName("class 用 contains 谓词")
    class WithClass {

        @Test
        @DisplayName("有 class 用 contains(@class,'x') 谓词")
        void withClassUsesContains() {
            Document doc = Jsoup.parse("<html><body><div class='title'>x</div></body></html>");
            Element div = doc.selectFirst("div");
            String xpath = XPathGenerator.generate(div, doc);
            assertThat(xpath).isEqualTo("//div[contains(@class,'title')]");
        }
    }
}
