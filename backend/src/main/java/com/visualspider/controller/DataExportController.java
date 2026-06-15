package com.visualspider.controller;

import com.visualspider.service.ArticleQueryService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/articles/export")
public class DataExportController {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final ArticleQueryService queryService;

    public DataExportController(ArticleQueryService queryService) {
        this.queryService = queryService;
    }

    @PostMapping
    public ResponseEntity<?> export(@RequestParam String format,
                                    @RequestParam(name = "config_id", required = false) Long configId,
                                    @RequestParam(required = false) String keyword) throws JsonProcessingException {
        String fmt = format == null ? "JSON" : format.toUpperCase();
        switch (fmt) {
            case "JSON":
                return exportJson(configId, keyword);
            case "XLSX":
                return ResponseEntity.status(org.springframework.http.HttpStatus.NOT_IMPLEMENTED)
                        .body(Map.of("message", "xlsx 导出本期未实现,见 OpenSpec M4"));
            default:
                return ResponseEntity.badRequest()
                        .body(Map.of("message", "不支持的 format: " + format));
        }
    }

    private ResponseEntity<?> exportJson(Long configId, String keyword) throws JsonProcessingException {
        List<Map<String, String>> rows = queryService.exportJson(configId, keyword);
        String json = MAPPER.writeValueAsString(rows);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=articles.json");
        return ResponseEntity.ok().headers(headers).body(json);
    }
}