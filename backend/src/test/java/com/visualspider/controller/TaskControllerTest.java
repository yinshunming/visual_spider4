package com.visualspider.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.visualspider.dto.request.CreateTaskRequest;
import com.visualspider.entity.CrawlConfig;
import com.visualspider.entity.CrawlTask;
import com.visualspider.enums.ConfigStatus;
import com.visualspider.enums.PageType;
import com.visualspider.enums.SelectorType;
import com.visualspider.enums.TaskStatus;
import com.visualspider.exception.ConfigNotFoundException;
import com.visualspider.exception.TaskAlreadyRunningException;
import com.visualspider.exception.TaskNotFoundException;
import com.visualspider.service.CrawlEngine;
import com.visualspider.service.CrawlTaskService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TaskController.class)
@DisplayName("TaskController")
class TaskControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private CrawlTaskService taskService;

    @MockBean
    private CrawlEngine engine;

    private CrawlConfig buildConfig(PageType type) {
        CrawlConfig config = new CrawlConfig();
        config.setId(1L);
        config.setName("cfg");
        config.setStartUrl("https://example.com/list");
        config.setPageType(type);
        config.setSelectorType(SelectorType.CSS);
        config.setStatus(ConfigStatus.STOPPED);
        return config;
    }

    private CrawlTask buildTask(long id, CrawlConfig config, TaskStatus status) {
        CrawlTask task = new CrawlTask();
        task.setId(id);
        task.setConfig(config);
        task.setPageType(config.getPageType());
        task.setStatus(status);
        task.setStartedAt(Instant.now());
        return task;
    }

    @Nested
    @DisplayName("POST /api/v1/tasks")
    class Create {

        @Test
        @DisplayName("LIST_DETAIL 创建返回 201")
        void create_listDetail_returns201() throws Exception {
            CrawlConfig config = buildConfig(PageType.LIST_DETAIL);
            CrawlTask task = buildTask(10L, config, TaskStatus.RUNNING);
            when(taskService.createTask(eq(1L), eq(null))).thenReturn(task);

            String body = objectMapper.writeValueAsString(new CreateTaskRequest(1L, null));
            mockMvc.perform(post("/api/v1/tasks")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.data.id").value(10))
                    .andExpect(jsonPath("$.data.status").value("RUNNING"));
        }

        @Test
        @DisplayName("DETAIL_ONLY 创建返回 201")
        void create_detailOnly_returns201() throws Exception {
            CrawlConfig config = buildConfig(PageType.DETAIL_ONLY);
            CrawlTask task = buildTask(11L, config, TaskStatus.RUNNING);
            List<String> urls = List.of("https://example.com/a", "https://example.com/b");
            when(taskService.createTask(eq(1L), eq(urls))).thenReturn(task);

            String body = objectMapper.writeValueAsString(new CreateTaskRequest(1L, urls));
            mockMvc.perform(post("/api/v1/tasks")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.data.id").value(11));
        }

        @Test
        @DisplayName("已有 RUNNING 任务时返回 409")
        void create_alreadyRunning_returns409() throws Exception {
            CrawlConfig config = buildConfig(PageType.LIST_DETAIL);
            when(taskService.createTask(eq(1L), any()))
                    .thenThrow(new TaskAlreadyRunningException());

            String body = objectMapper.writeValueAsString(new CreateTaskRequest(1L, null));
            mockMvc.perform(post("/api/v1/tasks")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isConflict());
        }

        @Test
        @DisplayName("configId 不存在返回 200 + 业务错误码 404")
        void create_configNotFound_returnsBusinessError() throws Exception {
            when(taskService.createTask(eq(99L), any()))
                    .thenThrow(new ConfigNotFoundException(99L));

            String body = objectMapper.writeValueAsString(new CreateTaskRequest(99L, null));
            mockMvc.perform(post("/api/v1/tasks")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(404));
        }
    }

    @Nested
    @DisplayName("GET /api/v1/tasks/{id}")
    class GetById {

        @Test
        @DisplayName("返回 200 含 status / totalItems / crawledItems / failedItems")
        void getById_returns() throws Exception {
            CrawlConfig config = buildConfig(PageType.LIST_DETAIL);
            CrawlTask task = buildTask(10L, config, TaskStatus.COMPLETED);
            task.setTotalItems(5);
            task.setCrawledItems(4);
            task.setFailedItems(1);
            when(taskService.getById(10L)).thenReturn(task);

            mockMvc.perform(get("/api/v1/tasks/10"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.status").value("COMPLETED"))
                    .andExpect(jsonPath("$.data.totalItems").value(5))
                    .andExpect(jsonPath("$.data.crawledItems").value(4))
                    .andExpect(jsonPath("$.data.failedItems").value(1));
        }

        @Test
        @DisplayName("不存在返回 200 + 业务错误码 4041")
        void getById_notFound() throws Exception {
            when(taskService.getById(99L)).thenThrow(new TaskNotFoundException(99L));
            mockMvc.perform(get("/api/v1/tasks/99"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(4041));
        }
    }

    @Nested
    @DisplayName("POST /api/v1/tasks/{id}/stop")
    class Stop {

        @Test
        @DisplayName("正常 stop 返回 200")
        void stop_returns200() throws Exception {
            doNothing().when(engine).stop(10L);
            mockMvc.perform(post("/api/v1/tasks/10/stop"))
                    .andExpect(status().isOk());
            verify(engine).stop(10L);
        }

        @Test
        @DisplayName("stop 未运行任务返回 200 + 业务错误码 4041")
        void stop_unrunningTask_returnsBusinessError() throws Exception {
            doThrow(new TaskNotFoundException(99L)).when(engine).stop(99L);
            mockMvc.perform(post("/api/v1/tasks/99/stop"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(4041));
        }
    }

    @Nested
    @DisplayName("DELETE /api/v1/tasks/{id}")
    class Delete {

        @Test
        @DisplayName("删除返回 204")
        void delete_returns204() throws Exception {
            doNothing().when(taskService).delete(10L);
            mockMvc.perform(delete("/api/v1/tasks/10"))
                    .andExpect(status().isNoContent());
            verify(taskService).delete(10L);
        }

        @Test
        @DisplayName("删除不存在返回 200 + 业务错误码 4041")
        void delete_notFound() throws Exception {
            doThrow(new TaskNotFoundException(99L)).when(taskService).delete(99L);
            mockMvc.perform(delete("/api/v1/tasks/99"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(4041));
        }
    }

    @Nested
    @DisplayName("GET /api/v1/tasks(分页)")
    class TaskList {

        @Test
        @DisplayName("无 configId 过滤 → 全部任务分页")
        void list_all() throws Exception {
            CrawlConfig config = buildConfig(PageType.LIST_DETAIL);
            CrawlTask task = buildTask(10L, config, TaskStatus.COMPLETED);
            Page<CrawlTask> page = new PageImpl<>(List.of(task), PageRequest.of(0, 20), 1);
            when(taskService.list(any())).thenReturn(page);

            mockMvc.perform(get("/api/v1/tasks?page=0&size=20"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.content[0].id").value(10));
        }

        @Test
        @DisplayName("带 configId 过滤")
        void list_byConfigId() throws Exception {
            CrawlConfig config = buildConfig(PageType.LIST_DETAIL);
            CrawlTask task = buildTask(10L, config, TaskStatus.COMPLETED);
            Page<CrawlTask> page = new PageImpl<>(List.of(task), PageRequest.of(0, 20), 1);
            when(taskService.listByConfigId(eq(1L), any())).thenReturn(page);

            mockMvc.perform(get("/api/v1/tasks?config_id=1&page=0&size=20"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.content[0].id").value(10));
        }
    }
}