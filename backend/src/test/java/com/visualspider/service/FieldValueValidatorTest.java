package com.visualspider.service;

import com.visualspider.enums.FieldType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("FieldValueValidator")
class FieldValueValidatorTest {

    private final FieldValueValidator validator = new FieldValueValidator();

    @Nested
    @DisplayName("TEXT 校验")
    class TextType {

        @Test
        @DisplayName("trim 后非空 → 合法")
        void textNonBlank() {
            assertThat(validator.validate("hello", FieldType.TEXT)).isEqualTo("hello");
        }

        @Test
        @DisplayName("trim 后空字符串 → null")
        void textBlank() {
            assertThat(validator.validate("", FieldType.TEXT)).isNull();
        }

        @Test
        @DisplayName("纯空白 → null")
        void textWhitespace() {
            assertThat(validator.validate("   ", FieldType.TEXT)).isNull();
        }
    }

    @Nested
    @DisplayName("NUMBER 校验")
    class NumberType {

        @Test
        @DisplayName("整数 → 合法")
        void numberInteger() {
            assertThat(validator.validate("99", FieldType.NUMBER)).isEqualTo("99");
        }

        @Test
        @DisplayName("浮点数 → 合法")
        void numberDecimal() {
            assertThat(validator.validate("3.14", FieldType.NUMBER)).isEqualTo("3.14");
        }

        @Test
        @DisplayName("非数字 'abc' → null")
        void numberNotNumber() {
            assertThat(validator.validate("abc", FieldType.NUMBER)).isNull();
        }

        @Test
        @DisplayName("千分位 '1,234' → null(不接受千分位)")
        void numberThousandsSeparator() {
            assertThat(validator.validate("1,234", FieldType.NUMBER)).isNull();
        }
    }

    @Nested
    @DisplayName("DATE 校验(严格 ISO 8601)")
    class DateType {

        @Test
        @DisplayName("ISO_DATE '2026-06-12' → 合法")
        void dateIsoDate() {
            assertThat(validator.validate("2026-06-12", FieldType.DATE)).isEqualTo("2026-06-12");
        }

        @Test
        @DisplayName("ISO_DATE_TIME '2026-06-12T10:30:00Z' → 合法")
        void dateIsoDateTime() {
            assertThat(validator.validate("2026-06-12T10:30:00Z", FieldType.DATE))
                    .isEqualTo("2026-06-12T10:30:00Z");
        }

        @Test
        @DisplayName("非 ISO '2026/06/12' → null")
        void dateNonIsoSlash() {
            assertThat(validator.validate("2026/06/12", FieldType.DATE)).isNull();
        }

        @Test
        @DisplayName("非 ISO 'Jun 12, 2026' → null")
        void dateNonIsoEnglish() {
            assertThat(validator.validate("Jun 12, 2026", FieldType.DATE)).isNull();
        }
    }

    @Nested
    @DisplayName("URL 校验(仅 http/https 绝对 URL)")
    class UrlType {

        @Test
        @DisplayName("'https://example.com' → 合法")
        void urlHttps() {
            assertThat(validator.validate("https://example.com", FieldType.URL))
                    .isEqualTo("https://example.com");
        }

        @Test
        @DisplayName("'http://x' → 合法")
        void urlHttp() {
            assertThat(validator.validate("http://x", FieldType.URL)).isEqualTo("http://x");
        }

        @Test
        @DisplayName("相对路径 '/x' → null")
        void urlRelative() {
            assertThat(validator.validate("/x", FieldType.URL)).isNull();
        }

        @Test
        @DisplayName("'mailto:a@b.com' → null")
        void urlMailto() {
            assertThat(validator.validate("mailto:a@b.com", FieldType.URL)).isNull();
        }

        @Test
        @DisplayName("'ftp://x' → null")
        void urlFtp() {
            assertThat(validator.validate("ftp://x", FieldType.URL)).isNull();
        }
    }
}
