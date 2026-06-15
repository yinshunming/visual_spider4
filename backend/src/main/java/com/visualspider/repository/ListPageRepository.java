package com.visualspider.repository;

import com.visualspider.entity.ListPage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ListPageRepository extends JpaRepository<ListPage, Long> {

    List<ListPage> findByTaskId(Long taskId);

    Page<ListPage> findByConfigId(Long configId, Pageable pageable);
}