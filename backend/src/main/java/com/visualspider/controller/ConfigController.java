package com.visualspider.controller;

import com.visualspider.dto.ApiResponse;
import com.visualspider.dto.request.CreateConfigRequest;
import com.visualspider.dto.request.CreateFieldRequest;
import com.visualspider.dto.request.UpdateConfigRequest;
import com.visualspider.dto.response.ConfigResponse;
import com.visualspider.entity.CrawlConfig;
import com.visualspider.entity.CrawlField;
import com.visualspider.service.CrawlConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/configs")
@RequiredArgsConstructor
public class ConfigController {

    private final CrawlConfigService configService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ConfigResponse> create(@RequestBody CreateConfigRequest request) {
        CrawlConfig input = new CrawlConfig();
        input.setName(request.name());
        input.setPageType(request.pageType());
        input.setSelectorType(request.selectorType());
        CrawlConfig saved = configService.create(input);
        return ApiResponse.success(ConfigResponse.from(saved));
    }

    @GetMapping
    public ApiResponse<Page<ConfigResponse>> list(Pageable pageable) {
        Page<CrawlConfig> page = configService.list(pageable);
        return ApiResponse.success(page.map(ConfigResponse::from));
    }

    @GetMapping("/{id}")
    public ApiResponse<ConfigResponse> getById(@PathVariable Long id) {
        CrawlConfig config = configService.getById(id);
        return ApiResponse.success(ConfigResponse.from(config));
    }

    @PutMapping("/{id}")
    public ApiResponse<ConfigResponse> update(@PathVariable Long id,
                                              @RequestBody UpdateConfigRequest request) {
        List<CrawlField> fields = request.fields() == null ? List.of()
                : request.fields().stream().map(this::toField).toList();
        CrawlConfig updated = configService.updateWithFields(
                id, request.name(), request.pageType(), request.selectorType(), fields);
        return ApiResponse.success(ConfigResponse.from(updated));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        configService.deleteById(id);
    }

    private CrawlField toField(CreateFieldRequest req) {
        CrawlField f = new CrawlField();
        f.setPageType(req.pageType());
        f.setFieldName(req.fieldName());
        f.setFieldType(req.fieldType());
        f.setSelector(req.selector());
        return f;
    }
}
