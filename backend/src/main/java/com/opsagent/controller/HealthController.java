package com.opsagent.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 健康检查接口。
 *
 * 这是平台最基础的一个接口，作用有两个：
 * 1. 验证后端服务是否成功启动、网络是否可达；
 * 2. 为后续的监控模块打底——平台的监控能力本质上就是采集各类状态信息，
 *    健康检查是最简单的一种状态采集。
 *
 * @RestController = @Controller + @ResponseBody，表示这个类的返回值直接作为 HTTP 响应体（默认序列化为 JSON）
 * @RequestMapping("/api") 表示这个类下所有接口的路径都以 /api 开头
 */
@RestController
@RequestMapping("/api")
public class HealthController {

    /**
     * GET /api/health
     *
     * 返回服务当前状态。用 Map 返回是为了演示 JSON 序列化，
     * 后续接入实体类和统一响应封装后，这里会替换成规范的返回结构。
     */
    @GetMapping("/health")
    public Map<String, Object> health() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", "UP");
        result.put("service", "opsagent-backend");
        result.put("time", LocalDateTime.now().toString());
        return result;
    }
}
