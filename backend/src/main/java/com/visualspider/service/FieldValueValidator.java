package com.visualspider.service;

import com.visualspider.enums.FieldType;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

/**
 * 字段抽取阶段后的类型校验器,纯函数,无 Spring 依赖。
 *
 * <p>严格按 spec 规则:
 * <ul>
 *   <li>TEXT:trim 后非空 → 合法</li>
 *   <li>NUMBER:Double.parseDouble 不抛 → 合法;<b>不接受千分位</b>如 {@code "1,234"}</li>
 *   <li>DATE:能被 {@link DateTimeFormatter#ISO_DATE} 或 {@link DateTimeFormatter#ISO_DATE_TIME} 之一解析即合法;<b>严格 ISO 8601</b></li>
 *   <li>URL:URI 绝对且 scheme 为 http/https → 合法</li>
 * </ul>
 *
 * <p>非法值一律返回 {@code null};调用方据此判 {@code TYPE_MISMATCH}。
 */
@Component
public class FieldValueValidator {

    /**
     * 校验抽取出的原始字符串是否符合字段类型规则。
     *
     * @param raw 浏览器从 DOM 取出的原始字符串(URL 类型已由浏览器 .href 绝对化)
     * @param type 字段类型
     * @return 合法返回原值(trim 后),非法返回 {@code null}
     */
    public String validate(String raw, FieldType type) {
        if (raw == null) {
            return null;
        }
        return switch (type) {
            case TEXT -> validateText(raw);
            case NUMBER -> validateNumber(raw);
            case DATE -> validateDate(raw);
            case URL -> validateUrl(raw);
        };
    }

    private String validateText(String raw) {
        String trimmed = raw.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String validateNumber(String raw) {
        try {
            Double.parseDouble(raw);
            return raw;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String validateDate(String raw) {
        try {
            DateTimeFormatter.ISO_DATE.parse(raw);
            return raw;
        } catch (DateTimeParseException ignored) {
            // fall through
        }
        try {
            DateTimeFormatter.ISO_DATE_TIME.parse(raw);
            return raw;
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private String validateUrl(String raw) {
        try {
            URI uri = new URI(raw);
            if (!uri.isAbsolute()) {
                return null;
            }
            String scheme = uri.getScheme();
            if (scheme == null) {
                return null;
            }
            String lower = scheme.toLowerCase();
            return "http".equals(lower) || "https".equals(lower) ? raw : null;
        } catch (URISyntaxException e) {
            return null;
        }
    }
}
