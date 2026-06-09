package com.visualspider.service;

import com.visualspider.dto.response.PageFetchResponse;
import com.visualspider.enums.PageFetchStatus;
import com.visualspider.exception.BlockedAddressException;
import com.visualspider.exception.FetchFailedException;
import com.visualspider.exception.FetchTimeoutException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.ConnectException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("PageFetchService")
class PageFetchServiceTest {

    private HttpClient httpClient;
    private PageFetchService service;

    @BeforeEach
    void setUp() {
        httpClient = mock(HttpClient.class);
        service = new PageFetchService(httpClient, 2L * 1024 * 1024, "VisualSpider4/0.1", 8);
    }

    private HttpResponse<InputStream> mockResponse(String body, URI finalUri) {
        @SuppressWarnings("unchecked")
        HttpResponse<InputStream> resp = mock(HttpResponse.class);
        when(resp.body()).thenReturn(new ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8)));
        when(resp.uri()).thenReturn(finalUri);
        return resp;
    }

    @Nested
    @DisplayName("§8 成功抓取")
    class SuccessPath {

        @Test
        @DisplayName("返回 SUCCESS + title + contentLength + finalUrl")
        void fetchSuccess() throws Exception {
            String html = "<html><head><title>Example Domain</title></head><body>hi</body></html>";
            URI uri = URI.create("https://example.com");
            HttpResponse<InputStream> response = mockResponse(html, uri);
            when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                    .thenReturn(response);

            PageFetchResponse resp = service.fetch(uri);

            assertThat(resp.status()).isEqualTo(PageFetchStatus.SUCCESS);
            assertThat(resp.title()).isEqualTo("Example Domain");
            assertThat(resp.finalUrl()).isEqualTo("https://example.com");
            assertThat(resp.contentLength()).isEqualTo(html.getBytes(StandardCharsets.UTF_8).length);
            assertThat(resp.fetchedAt()).isNotNull();
        }

        @Test
        @DisplayName("重定向后 finalUrl 等于最终 URL")
        void fetchFollowsRedirect() throws Exception {
            URI request = URI.create("https://example.com/in");
            URI finalUri = URI.create("https://example.com/out");
            HttpResponse<InputStream> response = mockResponse("<title>x</title>", finalUri);
            when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                    .thenReturn(response);

            PageFetchResponse resp = service.fetch(request);

            assertThat(resp.finalUrl()).isEqualTo("https://example.com/out");
        }
    }

    @Nested
    @DisplayName("§9 大小限制")
    class SizeLimit {

        @Test
        @DisplayName("响应体超过 max-size 抛 FetchFailedException(code=4005)")
        void rejectsOversized() throws Exception {
            PageFetchService small = new PageFetchService(httpClient, 100L, "ua", 8);
            byte[] big = new byte[200];
            for (int i = 0; i < big.length; i++) big[i] = 'a';
            @SuppressWarnings("unchecked")
            HttpResponse<InputStream> resp = mock(HttpResponse.class);
            when(resp.body()).thenReturn(new ByteArrayInputStream(big));
            when(resp.uri()).thenReturn(URI.create("https://example.com"));
            when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                    .thenReturn(resp);

            assertThatThrownBy(() -> small.fetch(URI.create("https://example.com")))
                    .isInstanceOf(FetchFailedException.class)
                    .satisfies(e -> assertThat(((FetchFailedException) e).getCode()).isEqualTo(4005))
                    .hasMessageContaining("超过大小限制");
        }
    }

    @Nested
    @DisplayName("§10 超时")
    class Timeout {

        @Test
        @DisplayName("HttpTimeoutException → FetchTimeoutException(code=4004)")
        void mapsTimeout() throws Exception {
            when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                    .thenThrow(new HttpTimeoutException("timed out"));

            assertThatThrownBy(() -> service.fetch(URI.create("https://example.com")))
                    .isInstanceOf(FetchTimeoutException.class)
                    .satisfies(e -> assertThat(((FetchTimeoutException) e).getCode()).isEqualTo(4004));
        }
    }

    @Nested
    @DisplayName("§11 网络失败")
    class NetworkFailure {

        @Test
        @DisplayName("IOException → FetchFailedException(code=4002)")
        void mapsIoException() throws Exception {
            when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                    .thenThrow(new ConnectException("Connection refused"));

            assertThatThrownBy(() -> service.fetch(URI.create("https://example.com")))
                    .isInstanceOf(FetchFailedException.class)
                    .satisfies(e -> assertThat(((FetchFailedException) e).getCode()).isEqualTo(4002))
                    .hasMessageContaining("无法访问目标地址");
        }
    }

    @Nested
    @DisplayName("UrlGuard 集成")
    class GuardIntegration {

        @Test
        @DisplayName("拒绝 localhost")
        void rejectsLocalhost() {
            assertThatThrownBy(() -> service.fetch(URI.create("http://localhost/x")))
                    .isInstanceOf(BlockedAddressException.class);
        }
    }
}
