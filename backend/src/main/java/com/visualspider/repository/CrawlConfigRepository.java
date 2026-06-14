package com.visualspider.repository;

import com.visualspider.entity.CrawlConfig;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CrawlConfigRepository extends JpaRepository<CrawlConfig, Long> {

    /**
     * 一次性加载 CrawlConfig + fields,避免在 Session 关闭后(尤其 WebSocket 工作线程)
     * 访问 LAZY 的 fields 集合触发 LazyInitializationException。
     *
     * 注:用 @Query 显式 JPQL,避免 Spring Data JPA 把方法名 findByIdWithFields
     * 推导为派生查询(会找不到 'withFields' 属性导致启动失败)。
     */
    @EntityGraph(attributePaths = {"fields"})
    @Query("select c from CrawlConfig c where c.id = :id")
    Optional<CrawlConfig> findByIdWithFields(@Param("id") Long id);
}
