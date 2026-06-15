package com.visualspider.repository;

import com.visualspider.entity.CrawlTask;
import com.visualspider.enums.TaskStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CrawlTaskRepository extends JpaRepository<CrawlTask, Long> {

    Page<CrawlTask> findByConfigId(Long configId, Pageable pageable);

    List<CrawlTask> findByStatus(TaskStatus status);

    /**
     * 一次性加载 CrawlTask + listPages + detailUrls(避免 Session 关闭后 LazyInit)。
     * 子集合(listItems / articles)由调用方在事务内访问。
     */
    @EntityGraph(attributePaths = {"listPages", "detailUrls"})
    @Query("select t from CrawlTask t where t.id = :id")
    Optional<CrawlTask> findByIdWithRelations(@Param("id") Long id);
}