package com.opsagent.controller;

import com.opsagent.common.Result;
import com.opsagent.dto.SystemMetrics;
import com.opsagent.entity.ServerMetric;
import com.opsagent.service.MonitorService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 监控接口。
 *
 * 当前提供"实时概览"：一次调用就把目标服务器的 CPU、内存、磁盘、负载、
 * 进程 Top5 全部取回来。后续会在此基础上增加定时采集入库与趋势查询。
 *
 * 这个接口需要登录令牌（不在白名单里），因为服务器状态属于敏感信息。
 */
@RestController
@RequestMapping("/api/monitor")
public class MonitorController {

    private final MonitorService monitorService;

    public MonitorController(MonitorService monitorService) {
        this.monitorService = monitorService;
    }

    /**
     * GET /api/monitor/{serverId}/overview
     * 实时采集指定服务器的指标（通过 SSH 执行只读命令）。
     */
    @GetMapping("/{serverId}/overview")
    public Result<SystemMetrics> overview(@PathVariable Long serverId) {
        return Result.ok(monitorService.collect(serverId));
    }

    /**
     * GET /api/monitor/{serverId}/history?limit=30
     * 查询最近若干条采集记录，用于画趋势图。
     */
    @GetMapping("/{serverId}/history")
    public Result<List<ServerMetric>> history(@PathVariable Long serverId,
                                              @RequestParam(defaultValue = "30") int limit) {
        return Result.ok(monitorService.history(serverId, limit));
    }
}
