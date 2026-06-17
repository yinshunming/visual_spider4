package com.visualspider.controller;

import com.visualspider.dto.ApiResponse;
import com.visualspider.dto.response.ArticleDetail;
import com.visualspider.dto.response.ArticleSummary;
import com.visualspider.entity.Article;
import com.visualspider.service.ArticleQueryService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/articles")
public class ArticleController {

    private final ArticleQueryService queryService;

    public ArticleController(ArticleQueryService queryService) {
        this.queryService = queryService;
    }

    @GetMapping
    public ApiResponse<Page<ArticleSummary>> list(@RequestParam(name = "task_id", required = false) Long taskId,
                                                  @RequestParam(name = "config_id", required = false) Long configId,
                                                  @RequestParam(required = false) String keyword,
                                                  Pageable pageable) {
        Page<Article> page = queryService.listArticles(taskId, configId, keyword, pageable);
        return ApiResponse.success(page.map(ArticleSummary::from));
    }

    @GetMapping("/{id}")
    public ApiResponse<ArticleDetail> getById(@PathVariable Long id) {
        return ApiResponse.success(ArticleDetail.from(queryService.getArticle(id)));
    }
}