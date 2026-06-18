package com.visualspider.repository;

import com.visualspider.entity.Article;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface ArticleRepository extends JpaRepository<Article, Long> {

    Page<Article> findByConfigId(Long configId, Pageable pageable);

    Page<Article> findByTaskId(Long taskId, Pageable pageable);

    /**
     * 删除指定任务下所有 article。删 task 前必须先调,避免 article 的
     * list_item_id / detail_url_id 外键约束违反(JPA 级联链未覆盖 article)。
     */
    @Modifying(clearAutomatically = true)
    @Query("delete from Article a where a.task.id = :taskId")
    void deleteByTaskId(@Param("taskId") Long taskId);

    /**
     * 在 custom_fields JSON 文本上做 LIKE 关键词搜索(大小写敏感)。
     * 注:这是 PG JSON-as-TEXT 的简化做法;M5 可替换为 jsonb 索引 + lower。
     * 用 native SQL + 显式 cast 避免 bytea vs text 类型不匹配。
     */
    @Query(value = "select * from article a "
            + "where a.config_id = :configId "
            + "and (cast(:keyword as text) is null or cast(a.custom_fields as text) like '%' || :keyword || '%')",
            countQuery = "select count(*) from article a "
                    + "where a.config_id = :configId "
                    + "and (cast(:keyword as text) is null or cast(a.custom_fields as text) like '%' || :keyword || '%')",
            nativeQuery = true)
    Page<Article> findByConfigIdAndKeyword(@Param("configId") Long configId,
                                            @Param("keyword") String keyword,
                                            Pageable pageable);
}