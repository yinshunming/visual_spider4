package com.visualspider.service;

import com.visualspider.dto.response.PageFetchResponse;
import com.visualspider.enums.PageFetchStatus;
import com.visualspider.exception.FetchFailedException;
import com.visualspider.exception.FetchTimeoutException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.convert.DurationUnit;
import org.springframework.stereotype.Service;
import org.springframework.util.unit.DataSize;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class PageFetchService {

    private static final Pattern TITLE_PATTERN =
            Pattern.compile("<title[^>]*>([\\s\\S]*?)</title>", Pattern.CASE_INSENSITIVE);
    private static final int BUFFER_SIZE = 8 * 1024;

    private final HttpClient httpClient;
    private final long maxSizeBytes;
    private final String userAgent;
    private final Duration timeout;

    @Autowired
    public PageFetchService(HttpClient httpClient,
                            @Value("${page-fetch.max-size:2MB}") DataSize maxSize,
                            @Value("${page-fetch.user-agent:VisualSpider4/0.1}") String userAgent,
                            @DurationUnit(ChronoUnit.SECONDS)
                            @Value("${page-fetch.timeout:8s}") Duration timeout) {
        this(httpClient, maxSize.toBytes(), userAgent, (int) Math.max(1, timeout.toSeconds()));
    }

    public PageFetchService(HttpClient httpClient, long maxSizeBytes, String userAgent, int timeoutSeconds) {
        this.httpClient = httpClient;
        this.maxSizeBytes = maxSizeBytes;
        this.userAgent = userAgent;
        this.timeout = Duration.ofSeconds(timeoutSeconds);
    }

    public PageFetchResponse fetch(URI uri) {
        UrlGuard.check(uri);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(uri)
                .timeout(timeout)
                .header("User-Agent", userAgent)
                .GET()
                .build();

        HttpResponse<InputStream> response;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
        } catch (HttpTimeoutException e) {
            throw new FetchTimeoutException("页面加载超时（" + timeout.toSeconds() + "s）");
        } catch (IOException e) {
            throw FetchFailedException.unreachable(e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw FetchFailedException.unreachable("请求被中断");
        }

        byte[] body = readBodyWithLimit(response.body());
        String html = new String(body, StandardCharsets.UTF_8);
        String title = extractTitle(html);
        URI finalUri = response.uri() != null ? response.uri() : uri;

        return new PageFetchResponse(
                PageFetchStatus.SUCCESS,
                finalUri.toString(),
                title,
                body.length,
                Instant.now()
        );
    }

    private byte[] readBodyWithLimit(InputStream input) {
        try (InputStream in = input;
             ByteArrayOutputStream buffer = new ByteArrayOutputStream()) {
            byte[] chunk = new byte[BUFFER_SIZE];
            long total = 0;
            int n;
            while ((n = in.read(chunk)) > 0) {
                total += n;
                if (total > maxSizeBytes) {
                    throw FetchFailedException.tooLarge();
                }
                buffer.write(chunk, 0, n);
            }
            return buffer.toByteArray();
        } catch (FetchFailedException e) {
            throw e;
        } catch (IOException e) {
            throw FetchFailedException.unreachable(e.getMessage());
        }
    }

    private String extractTitle(String html) {
        Matcher m = TITLE_PATTERN.matcher(html);
        if (m.find()) {
            return m.group(1).trim();
        }
        return "";
    }
}
