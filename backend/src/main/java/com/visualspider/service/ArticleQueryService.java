package com.visualspider.service;

import com.visualspider.entity.Article;
import com.visualspider.entity.ListItem;
import com.visualspider.entity.ListPage;
import com.visualspider.exception.ConfigNotFoundException;
import com.visualspider.repository.ArticleRepository;
import com.visualspider.repository.CrawlConfigRepository;
import com.visualspider.repository.ListItemRepository;
import com.visualspider.repository.ListPageRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 文章 / list_page / list_item 浏览与导出(M4 引入)。
 */
@Service
public class ArticleQueryService {

    private final ArticleRepository articleRepository;
    private final ListPageRepository listPageRepository;
    private final ListItemRepository listItemRepository;
    private final CrawlConfigRepository configRepository;

    public ArticleQueryService(ArticleRepository articleRepository,
                                ListPageRepository listPageRepository,
                                ListItemRepository listItemRepository,
                                CrawlConfigRepository configRepository) {
        this.articleRepository = articleRepository;
        this.listPageRepository = listPageRepository;
        this.listItemRepository = listItemRepository;
        this.configRepository = configRepository;
    }

    @Transactional(readOnly = true)
    public Page<Article> listArticles(Long taskId, Long configId, String keyword, Pageable pageable) {
        if (taskId != null) {
            return articleRepository.findByTaskId(taskId, pageable);
        }
        if (configId == null) {
            return articleRepository.findAll(pageable);
        }
        configRepository.findById(configId).orElseThrow(() -> new ConfigNotFoundException(configId));
        String kw = (keyword == null || keyword.isBlank()) ? null : keyword.trim();
        return articleRepository.findByConfigIdAndKeyword(configId, kw, pageable);
    }

    @Transactional(readOnly = true)
    public Article getArticle(Long id) {
        return articleRepository.findById(id).orElseThrow(() ->
                new com.visualspider.exception.ArticleNotFoundException(id));
    }

    @Transactional(readOnly = true)
    public Page<ListPage> listListPages(Long configId, Pageable pageable) {
        if (configId == null) {
            return listPageRepository.findAll(pageable);
        }
        configRepository.findById(configId).orElseThrow(() -> new ConfigNotFoundException(configId));
        return listPageRepository.findByConfigId(configId, pageable);
    }

    @Transactional(readOnly = true)
    public List<ListItem> listListItems(Long listPageId) {
        return listItemRepository.findByListPageId(listPageId);
    }

    /**
     * 聚合当前过滤结果的所有 custom_fields 键,作为列集合。
     * 按出现顺序保留(keySet 来自 LinkedHashMap 写入顺序)。
     */
    @Transactional(readOnly = true)
    public Set<String> aggregateCustomFieldKeys(Long configId, String keyword) {
        // 仅取最新 200 条做键聚合(导出场景下足够代表列集合)
        Pageable head = PageRequest.of(0, 200, Sort.by(Sort.Direction.DESC, "fetchedAt"));
        Page<Article> top = listArticles(null, configId, keyword, head);
        Set<String> keys = new LinkedHashSet<>();
        for (Article a : top) {
            if (a.getCustomFields() != null) {
                keys.addAll(a.getCustomFields().keySet());
            }
        }
        return keys;
    }

    /**
     * 按统一列集合导出当前过滤结果的 JSON 行(每篇文章缺失列填 null)。
     */
    @Transactional(readOnly = true)
    public List<Map<String, String>> exportJson(Long configId, String keyword) {
        Set<String> keys = aggregateCustomFieldKeys(configId, keyword);
        Pageable all = PageRequest.of(0, Integer.MAX_VALUE);
        Page<Article> page = listArticles(null, configId, keyword, all);
        List<Map<String, String>> rows = new java.util.ArrayList<>();
        for (Article a : page) {
            Map<String, String> row = new LinkedHashMap<>();
            row.put("id", a.getId() == null ? null : a.getId().toString());
            row.put("url", a.getUrl());
            row.put("status", a.getStatus() == null ? null : a.getStatus().name());
            row.put("fetched_at", a.getFetchedAt() == null ? null : a.getFetchedAt().toString());
            for (String k : keys) {
                row.put(k, a.getCustomFields() == null ? null : a.getCustomFields().get(k));
            }
            rows.add(row);
        }
        return rows;
    }
}