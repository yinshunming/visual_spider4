package com.visualspider.service;

import com.microsoft.playwright.Page;
import com.visualspider.dto.response.ExtractionPreviewResponse;
import com.visualspider.dto.response.FieldPreviewResult;
import com.visualspider.entity.CrawlConfig;
import com.visualspider.entity.CrawlField;
import com.visualspider.enums.FieldPageType;
import com.visualspider.enums.FieldType;
import com.visualspider.enums.PageType;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 按模板批量提取:在某 pageType(LIST / DETAIL)下,对 config 所有字段执行选择器抽取并校验。
 * 与 WebSocket 协议解耦,供 M4 爬取执行直接复用。
 */
@Service
public class ExtractionService {

    private static final String DETAIL_URL_FIELD_NAME = "detail_url";
    private static final String MISSING_DETAIL_URL_WARNING =
            "LIST_DETAIL 配置缺少 detail_url 字段,M4 启动爬取时会被拦截";
    private static final String EMPTY_TEMPLATE_WARNING_FORMAT = "该模板未定义任何 %s 字段";

    private final CrawlConfigService configService;
    private final FieldValueValidator validator;

    public ExtractionService(CrawlConfigService configService, FieldValueValidator validator) {
        this.configService = configService;
        this.validator = validator;
    }

    public ExtractionPreviewResponse extractByTemplate(Page page, Long configId, FieldPageType pageType) {
        CrawlConfig config = configService.getByIdWithFields(configId);
        List<CrawlField> fields = config.getFields().stream()
                .filter(f -> f.getPageType() == pageType)
                .sorted(Comparator.comparing(CrawlField::getCreatedAt,
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();

        List<String> warnings = new ArrayList<>();
        if (fields.isEmpty()) {
            warnings.add(String.format(EMPTY_TEMPLATE_WARNING_FORMAT, pageType.name()));
        }
        if (shouldWarnMissingDetailUrl(config, fields, pageType)) {
            warnings.add(MISSING_DETAIL_URL_WARNING);
        }

        List<FieldPreviewResult> results = new ArrayList<>(fields.size());
        for (CrawlField field : fields) {
            results.add(executeField(page, field));
        }
        return new ExtractionPreviewResponse(results, warnings);
    }

    private boolean shouldWarnMissingDetailUrl(CrawlConfig config, List<CrawlField> pageTypeFields, FieldPageType pageType) {
        if (config.getPageType() != PageType.LIST_DETAIL) {
            return false;
        }
        if (pageType != FieldPageType.LIST) {
            return false;
        }
        return pageTypeFields.stream().noneMatch(f ->
                DETAIL_URL_FIELD_NAME.equals(f.getFieldName()) && f.getFieldType() == FieldType.URL);
    }

    private FieldPreviewResult executeField(Page page, CrawlField field) {
        String selector = field.getSelector();
        List<String> rawValues;
        try {
            @SuppressWarnings("unchecked")
            List<String> result = (List<String>) page.evaluate(buildEvaluateScript(), selector);
            rawValues = result == null ? List.of() : result;
        } catch (Exception e) {
            return new FieldPreviewResult(
                    field.getId(), field.getFieldName(), field.getFieldType(), selector,
                    0, List.of(), List.of(),
                    com.visualspider.enums.FieldPreviewStatus.SELECTOR_INVALID,
                    e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
        }

        if (rawValues.isEmpty()) {
            return new FieldPreviewResult(
                    field.getId(), field.getFieldName(), field.getFieldType(), selector,
                    0, List.of(), List.of(),
                    com.visualspider.enums.FieldPreviewStatus.NO_MATCH,
                    null);
        }

        List<String> validated = new ArrayList<>(rawValues.size());
        boolean anyInvalid = false;
        String firstInvalidMessage = null;
        for (String raw : rawValues) {
            String v = validator.validate(raw, field.getFieldType());
            validated.add(v);
            if (v == null && !anyInvalid) {
                anyInvalid = true;
                firstInvalidMessage = typeMismatchMessage(field.getFieldType(), raw);
            }
        }

        if (anyInvalid) {
            return new FieldPreviewResult(
                    field.getId(), field.getFieldName(), field.getFieldType(), selector,
                    rawValues.size(), rawValues, validated,
                    com.visualspider.enums.FieldPreviewStatus.TYPE_MISMATCH,
                    firstInvalidMessage);
        }
        return new FieldPreviewResult(
                field.getId(), field.getFieldName(), field.getFieldType(), selector,
                rawValues.size(), rawValues, validated,
                com.visualspider.enums.FieldPreviewStatus.OK,
                null);
    }

    private String buildEvaluateScript() {
        return "(sel) => {"
                + " const nodes = Array.from(document.querySelectorAll(sel));"
                + " return nodes.map(n => {"
                + "   if (n && typeof n.href === 'string' && n.href.length > 0) return n.href;"
                + "   return (n && n.textContent) ? n.textContent.trim() : '';"
                + " });"
                + "}";
    }

    private String typeMismatchMessage(FieldType type, String raw) {
        return switch (type) {
            case TEXT -> "文本为空或缺失";
            case NUMBER -> "非数字: \"" + raw + "\"";
            case DATE -> "非 ISO 8601 日期: \"" + raw + "\"";
            case URL -> "非绝对 http(s) URL: \"" + raw + "\"";
        };
    }
}
