package com.visualspider.controller;

import com.visualspider.dto.ApiResponse;
import com.visualspider.dto.request.PageFetchRequest;
import com.visualspider.dto.response.PageFetchResponse;
import com.visualspider.exception.InvalidUrlException;
import com.visualspider.service.PageFetchService;
import com.visualspider.service.UrlGuard;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

@RestController
@RequestMapping("/api/v1/page-fetch")
@RequiredArgsConstructor
public class PageFetchController {

    private final PageFetchService pageFetchService;

    @PostMapping
    public ApiResponse<PageFetchResponse> fetch(@Valid @RequestBody PageFetchRequest request) {
        URI uri = UrlGuard.checkUrlString(request.url());
        PageFetchResponse response = pageFetchService.fetch(uri);
        return ApiResponse.success(response);
    }
}
