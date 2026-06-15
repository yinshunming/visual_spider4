package com.visualspider.service;

import com.visualspider.entity.CrawlTask;
import com.visualspider.enums.TaskStatus;
import com.visualspider.repository.CrawlTaskRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

/**
 * 服务启动阶段扫描所有 RUNNING 任务,统一标 FAILED(error_message="服务重启,任务中断")。
 * 通过 {@code crawl.engine.startup-cleanup-enabled} 配置关闭(默认 true)。
 */
@Component
@ConditionalOnProperty(name = "crawl.engine.startup-cleanup-enabled", havingValue = "true", matchIfMissing = true)
public class ZombieTaskCleanerRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ZombieTaskCleanerRunner.class);
    private static final String ZOMBIE_MESSAGE = "服务重启,任务中断";

    private final CrawlTaskRepository taskRepository;

    public ZombieTaskCleanerRunner(CrawlTaskRepository taskRepository) {
        this.taskRepository = taskRepository;
    }

    @Override
    public void run(ApplicationArguments args) {
        cleanup();
    }

    /** 暴露 cleanup 便于单测直接调用。 */
    public void cleanup() {
        List<CrawlTask> zombies = taskRepository.findByStatus(TaskStatus.RUNNING);
        if (zombies.isEmpty()) {
            log.info("Zombie 清理:无 RUNNING 任务");
            return;
        }
        Instant now = Instant.now();
        for (CrawlTask t : zombies) {
            t.setStatus(TaskStatus.FAILED);
            t.setErrorMessage(ZOMBIE_MESSAGE);
            t.setCompletedAt(now);
            taskRepository.save(t);
        }
        log.info("Zombie 清理:标记 {} 个 RUNNING 任务为 FAILED", zombies.size());
    }
}