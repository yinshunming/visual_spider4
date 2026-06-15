package com.visualspider.repository;

import com.visualspider.entity.DetailUrl;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DetailUrlRepository extends JpaRepository<DetailUrl, Long> {

    List<DetailUrl> findByTaskId(Long taskId);
}