package com.visualspider.service;

import com.visualspider.exception.BlockedAddressException;
import com.visualspider.exception.InvalidUrlException;
import com.visualspider.exception.StartUrlInvalidException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.net.URI;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("UrlGuard")
class UrlGuardTest {

    @Nested
    @DisplayName("§2 协议白名单")
    class SchemeCheck {

        @Test
        @DisplayName("file:// 协议被拒绝")
        void rejectsFileScheme() {
            assertThatThrownBy(() -> UrlGuard.check(URI.create("file:///etc/passwd")))
                    .isInstanceOf(InvalidUrlException.class);
        }

        @Test
        @DisplayName("ftp:// 协议被拒绝")
        void rejectsFtpScheme() {
            assertThatThrownBy(() -> UrlGuard.check(URI.create("ftp://example.com/x")))
                    .isInstanceOf(InvalidUrlException.class);
        }

        @Test
        @DisplayName("http://example.com 通过协议校验")
        void acceptsHttp() {
            assertThatCode(() -> UrlGuard.check(URI.create("http://example.com")))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("https://example.com 通过协议校验")
        void acceptsHttps() {
            assertThatCode(() -> UrlGuard.check(URI.create("https://example.com")))
                    .doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("§3 形态校验")
    class ShapeCheck {

        @Test
        @DisplayName("http:// 无 host 被拒绝")
        void rejectsMissingHost() {
            assertThatThrownBy(() -> UrlGuard.checkUrlString("http://"))
                    .isInstanceOf(InvalidUrlException.class);
        }

        @Test
        @DisplayName("不是合法 URI 字符串被拒绝")
        void rejectsMalformedUri() {
            assertThatThrownBy(() -> UrlGuard.checkUrlString("not-a-url"))
                    .isInstanceOf(InvalidUrlException.class);
        }
    }

    @Nested
    @DisplayName("§4 字面回环 IP")
    class LoopbackLiteral {

        @Test
        @DisplayName("拒绝 127.0.0.1")
        void rejectsIpv4Loopback() {
            assertThatThrownBy(() -> UrlGuard.check(URI.create("http://127.0.0.1/x")))
                    .isInstanceOf(BlockedAddressException.class);
        }

        @Test
        @DisplayName("拒绝 ::1（带方括号）")
        void rejectsIpv6Loopback() {
            assertThatThrownBy(() -> UrlGuard.check(URI.create("http://[::1]/x")))
                    .isInstanceOf(BlockedAddressException.class);
        }
    }

    @Nested
    @DisplayName("§5 localhost 主机名")
    class LocalhostHost {

        @Test
        @DisplayName("拒绝小写 localhost")
        void rejectsLowercaseLocalhost() {
            assertThatThrownBy(() -> UrlGuard.check(URI.create("http://localhost/x")))
                    .isInstanceOf(BlockedAddressException.class);
        }

        @Test
        @DisplayName("拒绝大写 LOCALHOST")
        void rejectsUppercaseLocalhost() {
            assertThatThrownBy(() -> UrlGuard.check(URI.create("http://LOCALHOST/x")))
                    .isInstanceOf(BlockedAddressException.class);
        }

        @Test
        @DisplayName("拒绝混合大小写 LocalHost")
        void rejectsMixedCaseLocalhost() {
            assertThatThrownBy(() -> UrlGuard.check(URI.create("http://LocalHost/x")))
                    .isInstanceOf(BlockedAddressException.class);
        }

        @Test
        @DisplayName("公网域名 example.com 通过")
        void acceptsPublicDomain() {
            assertThatCode(() -> UrlGuard.check(URI.create("https://example.com")))
                    .doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("§6 validate(url, fieldName) - M4 service 层入口")
    class ValidateWithFieldName {

        @Test
        @DisplayName("startUrl=null 抛 StartUrlInvalidException,message 含 startUrl")
        void nullUrl_throwsWithFieldName() {
            assertThatThrownBy(() -> UrlGuard.validate(null, "startUrl"))
                    .isInstanceOf(StartUrlInvalidException.class)
                    .hasMessageContaining("startUrl");
        }

        @Test
        @DisplayName("空字符串被拒,message 含 fieldName")
        void emptyUrl_throwsWithFieldName() {
            assertThatThrownBy(() -> UrlGuard.validate("   ", "startUrl"))
                    .isInstanceOf(StartUrlInvalidException.class)
                    .hasMessageContaining("startUrl");
        }

        @Test
        @DisplayName("非 http(s) 协议被拒,message 含 fieldName")
        void nonHttpScheme_throwsWithFieldName() {
            assertThatThrownBy(() -> UrlGuard.validate("ftp://example.com/x", "startUrl"))
                    .isInstanceOf(StartUrlInvalidException.class)
                    .hasMessageContaining("startUrl");
        }

        @Test
        @DisplayName("回环地址被拒,message 含 fieldName 与 回环")
        void loopback_throwsWithFieldName() {
            assertThatThrownBy(() -> UrlGuard.validate("http://localhost:8080/list", "startUrl"))
                    .isInstanceOf(StartUrlInvalidException.class)
                    .hasMessageContaining("startUrl")
                    .hasMessageContaining("回环");
        }

        @Test
        @DisplayName("合法公网 URL 通过")
        void publicUrl_passes() {
            assertThatCode(() -> UrlGuard.validate("https://example.com/list", "startUrl"))
                    .doesNotThrowAnyException();
        }
    }
}
