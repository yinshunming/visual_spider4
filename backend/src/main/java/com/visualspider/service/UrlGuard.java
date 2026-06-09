package com.visualspider.service;

import com.visualspider.exception.BlockedAddressException;
import com.visualspider.exception.InvalidUrlException;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Set;

public final class UrlGuard {

    private static final Set<String> ALLOWED_SCHEMES = Set.of("http", "https");
    private static final Set<String> BLOCKED_HOSTS = Set.of("localhost");
    private static final Set<String> LOOPBACK_LITERALS = Set.of("127.0.0.1", "::1", "[::1]");

    private UrlGuard() {
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
}
