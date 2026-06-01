package com.visualspider.repository;

import com.visualspider.entity.CrawlField;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CrawlFieldRepository extends JpaRepository<CrawlField, Long> {

    List<CrawlField> findByConfigIdOrderByCreatedAtAsc(Long configId);
}
