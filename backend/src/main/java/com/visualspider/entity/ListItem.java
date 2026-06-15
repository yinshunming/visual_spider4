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
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 列表项(M4 引入):列表页抽出的单个条目,关联到目标详情 URL。
 * customFields 仅用于本地记录上下文;真实业务字段在关联的 article 上。
 */
@Entity
@Table(name = "list_item")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ListItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "list_page_id", nullable = false)
    private ListPage listPage;

    @Column(name = "detail_url", nullable = false, length = 2048)
    private String detailUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ItemStatus status;

    @Column(name = "error_message", length = 1024)
    private String errorMessage;

    @Convert(converter = StringMapJsonConverter.class)
    @Column(name = "custom_fields", columnDefinition = "TEXT")
    private Map<String, String> customFields = new LinkedHashMap<>();
}