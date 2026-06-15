package com.visualspider.service;

import com.visualspider.exception.BlockedAddressException;
import com.visualspider.exception.InvalidUrlException;
import com.visualspider.exception.StartUrlInvalidException;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

public final class UrlGuard {

    private static final Set<String> ALLOWED_SCHEMES = Set.of("http", "https");
    private static final Set<String> BLOCKED_HOSTS = Set.of("localhost");
    private static final Set<String> LOOPBACK_LITERALS = Set.of("127.0.0.1", "::1", "[::1]");

    /**
     * 进程级开关:启用后 {@link #validate} 跳过回环/被禁主机检查。
     * 由 {@code crawl.url-guard.allow-loopback} 配置(e2e 测试场景默认 true)。
     * 注:仅对 M4 新增的 service 层 {@code validate(url, fieldName)} 入口生效,
     * {@link #check} / {@link #checkUrlString} 仍按原语义拒绝(用于 M2 已有路径)。
     */
    private static final AtomicBoolean ALLOW_LOOPBACK = new AtomicBoolean(false);

    private UrlGuard() {
    }

    public static void setAllowLoopback(boolean allow) {
        ALLOW_LOOPBACK.set(allow);
    }

    public static URI checkUrlString(String url) {
        if (url == null || url.isBlank()) {
            throw new InvalidUrlException("URL 不能为空");
        }
        URI uri;
        try {
            uri = new URI(url);
        } catch (URISyntaxException e) {
            throw new InvalidUrlException("URL 格式不合法");
        }
        if (uri.getScheme() == null || uri.getHost() == null) {
            throw new InvalidUrlException("URL 格式不合法");
        }
        check(uri);
        return uri;
    }

    public static void check(URI uri) {
        checkScheme(uri);
        checkShape(uri);
        checkHost(uri.getHost());
    }

    static void checkScheme(URI uri) {
        String scheme = uri.getScheme();
        if (scheme == null || !ALLOWED_SCHEMES.contains(scheme.toLowerCase())) {
            throw new InvalidUrlException("URL 格式不合法");
        }
    }

    static void checkShape(URI uri) {
        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            throw new InvalidUrlException("URL 格式不合法");
        }
    }

    static void checkHost(String host) {
        if (host == null) {
            throw new InvalidUrlException("URL 格式不合法");
        }
        if (BLOCKED_HOSTS.stream().anyMatch(host::equalsIgnoreCase)) {
            throw new BlockedAddressException("目标地址被禁止访问");
        }
        if (isLoopbackLiteral(host)) {
            throw new BlockedAddressException("目标地址被禁止访问");
        }
    }

    static boolean isLoopbackLiteral(String host) {
        if (host == null) {
            return false;
        }
        return LOOPBACK_LITERALS.contains(host);
    }

    /**
     * M4 服务层入口:校验任意用户输入 URL,异常统一抛 StartUrlInvalidException(code=4007),
     * message 含 {@code fieldName} 便于排查(startUrl / detail_url 等)。
     * 当 {@link #ALLOW_LOOPBACK} 为 true 时,跳过回环主机检查(供 e2e fixture 场景)。
     */
    public static void validate(String url, String fieldName) {
        if (url == null || url.isBlank()) {
            throw new StartUrlInvalidException(fieldName + " 不能为空");
        }
        URI uri;
        try {
            uri = new URI(url);
        } catch (URISyntaxException e) {
            throw new StartUrlInvalidException(fieldName + " 格式不合法");
        }
        if (uri.getScheme() == null || uri.getHost() == null) {
            throw new StartUrlInvalidException(fieldName + " 格式不合法");
        }
        try {
            checkScheme(uri);
            checkShape(uri);
            if (!ALLOW_LOOPBACK.get()) {
                checkHost(uri.getHost());
            }
        } catch (BlockedAddressException e) {
            throw new StartUrlInvalidException(fieldName + " 指向回环或被禁止地址");
        } catch (InvalidUrlException e) {
            throw new StartUrlInvalidException(fieldName + " 格式不合法");
        }
    }
}