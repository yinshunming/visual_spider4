package com.visualspider.repository;

import com.visualspider.entity.CrawlConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CrawlConfigRepository extends JpaRepository<CrawlConfig, Long> {
}
