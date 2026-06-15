package com.visualspider.service;

import com.visualspider.entity.CrawlTask;
import com.visualspider.enums.TaskStatus;
import com.visualspider.repository.CrawlTaskRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ZombieTaskCleanerRunner")
class ZombieTaskCleanerRunnerTest {

    @Mock
    private CrawlTaskRepository taskRepository;

    @InjectMocks
    private ZombieTaskCleanerRunner runner;

    private CrawlTask task(long id, TaskStatus status) {
        CrawlTask t = new CrawlTask();
        t.setId(id);
        t.setStatus(status);
        return t;
    }

    @Test
    @DisplayName("仅 RUNNING 任务被标 FAILED + error_message + completed_at")
    void run_marksRunningTasksFailed() {
        CrawlTask running = task(1L, TaskStatus.RUNNING);
        CrawlTask completed = task(2L, TaskStatus.COMPLETED);
        CrawlTask failed = task(3L, TaskStatus.FAILED);
        when(taskRepository.findByStatus(TaskStatus.RUNNING))
                .thenReturn(List.of(running));

        runner.cleanup();

        assertThat(running.getStatus()).isEqualTo(TaskStatus.FAILED);
        assertThat(running.getErrorMessage()).isEqualTo("服务重启,任务中断");
        assertThat(running.getCompletedAt()).isNotNull();
        verify(taskRepository).save(running);
        // COMPLETED / FAILED 不被修改
        verify(taskRepository, never()).save(completed);
        verify(taskRepository, never()).save(failed);
    }

    @Test
    @DisplayName("无 RUNNING 任务时不调用 save")
    void run_noRunningTasks_noSave() {
        when(taskRepository.findByStatus(TaskStatus.RUNNING)).thenReturn(List.of());

        runner.cleanup();

        verify(taskRepository, never()).save(any());
    }

    @Test
    @DisplayName("多个 RUNNING 任务都被标记")
    void run_multipleRunningTasks_allMarked() {
        CrawlTask r1 = task(1L, TaskStatus.RUNNING);
        CrawlTask r2 = task(2L, TaskStatus.RUNNING);
        when(taskRepository.findByStatus(TaskStatus.RUNNING))
                .thenReturn(List.of(r1, r2));

        runner.cleanup();

        ArgumentCaptor<CrawlTask> captor = ArgumentCaptor.forClass(CrawlTask.class);
        verify(taskRepository, org.mockito.Mockito.times(2)).save(captor.capture());
        assertThat(captor.getAllValues()).extracting(CrawlTask::getErrorMessage)
                .containsOnly("服务重启,任务中断");
    }
}