package com.visualspider.entity;

import com.visualspider.enums.DetailUrlStatus;
import jakarta.persistence.Column;
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

/**
 * DETAIL_ONLY 模式下用户提供的 URL(M4 引入):
 * - 创建 task 时拆分成 N 条 PENDING 记录
 * - CrawlEngine 逐条访问后写 article 并更新自身 status
 */
@Entity
@Table(name = "detail_url")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class DetailUrl {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "task_id", nullable = false)
    private CrawlTask task;

    @Column(nullable = false, length = 2048)
    private String url;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DetailUrlStatus status;

    @Column(name = "error_message", length = 1024)
    private String errorMessage;
}