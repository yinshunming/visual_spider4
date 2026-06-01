package com.visualspider.controller;

import com.visualspider.dto.ApiResponse;
import com.visualspider.dto.request.CreateFieldRequest;
import com.visualspider.dto.response.FieldResponse;
import com.visualspider.entity.CrawlField;
import com.visualspider.enums.FieldPageType;
import com.visualspider.enums.FieldType;
import com.visualspider.exception.ConfigNotFoundException;
import com.visualspider.service.CrawlFieldService;
import lombok.RequiredArgsConstructor;
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
@RequiredArgsConstructor
public class FieldController {

    private final CrawlFieldService fieldService;

    @PostMapping("/api/v1/configs/{configId}/fields")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<FieldResponse> addField(@PathVariable Long configId,
                                               @RequestBody CreateFieldRequest request) {
        CrawlField input = new CrawlField();
        input.setPageType(request.pageType());
        input.setFieldName(request.fieldName());
        input.setFieldType(request.fieldType());
        input.setSelector(request.selector());
        CrawlField saved = fieldService.addField(configId, input);
        return ApiResponse.success(FieldResponse.from(saved));
    }

    @GetMapping("/api/v1/configs/{configId}/fields")
    public ApiResponse<List<FieldResponse>> listFields(@PathVariable Long configId) {
        List<CrawlField> fields = fieldService.listByConfigId(configId);
        return ApiResponse.success(fields.stream().map(FieldResponse::from).toList());
    }

    @PutMapping("/api/v1/fields/{id}")
    public ApiResponse<FieldResponse> update(@PathVariable Long id,
                                            @RequestBody CreateFieldRequest request) {
        CrawlField updates = new CrawlField();
        updates.setPageType(request.pageType());
        updates.setFieldName(request.fieldName());
        updates.setFieldType(request.fieldType());
        updates.setSelector(request.selector());
        CrawlField updated = fieldService.update(id, updates);
        return ApiResponse.success(FieldResponse.from(updated));
    }

    @DeleteMapping("/api/v1/fields/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        fieldService.deleteById(id);
    }
}
