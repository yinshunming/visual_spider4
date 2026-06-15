package com.visualspider.controller;

import com.visualspider.dto.ApiResponse;
import com.visualspider.dto.request.CreateTaskRequest;
import com.visualspider.dto.response.TaskResponse;
import com.visualspider.entity.CrawlTask;
import com.visualspider.service.CrawlEngine;
import com.visualspider.service.CrawlTaskService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/tasks")
public class TaskController {

    private final CrawlTaskService taskService;
    private final CrawlEngine engine;

    public TaskController(CrawlTaskService taskService, CrawlEngine engine) {
        this.taskService = taskService;
        this.engine = engine;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<TaskResponse> create(@RequestBody CreateTaskRequest request) {
        CrawlTask task = taskService.createTask(request.configId(), request.urls());
        // 异步触发调度(同步调用,引擎内 try-with-resources 管理生命周期)
        new Thread(() -> engine.run(task)).start();
        return ApiResponse.success(TaskResponse.from(task));
    }

    @GetMapping
    public ApiResponse<Page<TaskResponse>> list(@RequestParam(name = "config_id", required = false) Long configId,
                                                Pageable pageable) {
        Page<CrawlTask> page = configId == null
                ? taskService.list(pageable)
                : taskService.listByConfigId(configId, pageable);
        return ApiResponse.success(page.map(TaskResponse::from));
    }

    @GetMapping("/{id}")
    public ApiResponse<TaskResponse> getById(@PathVariable Long id) {
        return ApiResponse.success(TaskResponse.from(taskService.getById(id)));
    }

    @PostMapping("/{id}/stop")
    public ApiResponse<Void> stop(@PathVariable Long id) {
        engine.stop(id);
        return ApiResponse.success(null);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        taskService.delete(id);
    }
}