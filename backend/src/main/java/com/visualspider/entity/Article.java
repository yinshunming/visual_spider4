package com.visualspider.entity;

import com.visualspider.enums.ItemStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 文章(M4 引入):详情页抽取产物。
 * LIST_DETAIL 模式下通过 listItemId 关联到 list_item;
 * DETAIL_ONLY 模式下通过 detailUrlId 关联到 detail_url。
 * customFields 仅含 DETAIL 模板下的业务字段(不含 detail_url)。
 */
@Entity
@Table(name = "article")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Article {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "task_id", nullable = false)
    private CrawlTask task;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "config_id", nullable = false)
    private CrawlConfig config;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "list_item_id")
    private ListItem listItem;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "detail_url_id")
    private DetailUrl detailUrl;

    @Column(nullable = false, length = 2048)
    private String url;

    @Column(name = "raw_html", columnDefinition = "TEXT")
    private String rawHtml;

    @Convert(converter = StringMapJsonConverter.class)
    @Column(name = "custom_fields", columnDefinition = "TEXT")
    private Map<String, String> customFields = new LinkedHashMap<>();

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ItemStatus status;

    @Column(name = "error_message", length = 1024)
    private String errorMessage;

    @Column(name = "fetched_at", nullable = false)
    private Instant fetchedAt;

    @PrePersist
    void onCreate() {
        if (fetchedAt == null) {
            fetchedAt = Instant.now();
        }
    }
}