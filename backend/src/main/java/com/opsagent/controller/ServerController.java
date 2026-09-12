package com.opsagent.controller;

import com.opsagent.entity.ServerInfo;
import com.opsagent.service.ServerInfoService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 服务器管理接口。
 *
 * 这个接口是监控模块的起点：先把"有哪些服务器"管理起来，
 * 后续的指标采集、Agent 诊断都会以 server 表中的记录为操作对象。
 */
@RestController
@RequestMapping("/api/servers")
public class ServerController {

    private final ServerInfoService serverInfoService;

    public ServerController(ServerInfoService serverInfoService) {
        this.serverInfoService = serverInfoService;
    }

    /**
     * GET /api/servers
     * 返回数据库中所有服务器的列表，数据来自 MySQL。
     */
    @GetMapping
    public List<ServerInfo> list() {
        return serverInfoService.listAll();
    }
}
