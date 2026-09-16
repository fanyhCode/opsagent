package com.opsagent.controller;

import com.opsagent.common.BizException;
import com.opsagent.common.Result;
import com.opsagent.dto.ObservabilitySummary;
import com.opsagent.security.UserContext;
import com.opsagent.service.ObservabilityService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Agent 可观测性接口。
 */
@RestController
@RequestMapping("/api/observability")
public class ObservabilityController {

    private final ObservabilityService observabilityService;

    public ObservabilityController(ObservabilityService observabilityService) {
        this.observabilityService = observabilityService;
    }

    /** GET /api/observability/summary —— 统计总览 */
    @GetMapping("/summary")
    public Result<ObservabilitySummary> summary() {
        if (UserContext.get() == null) {
            throw new BizException(401, "未登录");
        }
        return Result.ok(observabilityService.summary());
    }
}
