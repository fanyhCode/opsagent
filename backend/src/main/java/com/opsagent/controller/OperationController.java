package com.opsagent.controller;

import com.opsagent.common.BizException;
import com.opsagent.common.Result;
import com.opsagent.entity.CommandAudit;
import com.opsagent.ops.OperationDefinition;
import com.opsagent.ops.OperationRegistry;
import com.opsagent.security.LoginUser;
import com.opsagent.security.UserContext;
import com.opsagent.service.OperationService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 操作与审计接口。
 *
 * 这些接口是"人工确认"环节的落地：
 * 前端拉待确认列表 → 用户点确认/拒绝 → 后端校验角色与状态 → 执行并留痕。
 */
@RestController
@RequestMapping("/api/operations")
public class OperationController {

    private final OperationService operationService;

    public OperationController(OperationService operationService) {
        this.operationService = operationService;
    }

    /** 待人工确认的操作列表 */
    @GetMapping("/pending")
    public Result<List<CommandAudit>> pending() {
        requireLogin();
        return Result.ok(operationService.listPending());
    }

    /** 操作审计记录（最近的执行历史） */
    @GetMapping("/audits")
    public Result<List<CommandAudit>> audits(@RequestParam(defaultValue = "50") int limit) {
        requireLogin();
        return Result.ok(operationService.listAudits(limit));
    }

    /** 操作白名单与风险等级 */
    @GetMapping("/registry")
    public Result<List<OperationDefinition>> registry() {
        requireLogin();
        return Result.ok(OperationRegistry.all());
    }

    /** 确认执行 */
    @PostMapping("/{id}/approve")
    public Result<CommandAudit> approve(@PathVariable Long id) {
        LoginUser approver = requireLogin();
        return Result.ok("操作已执行", operationService.approve(id, approver));
    }

    /** 拒绝执行 */
    @PostMapping("/{id}/reject")
    public Result<CommandAudit> reject(@PathVariable Long id) {
        LoginUser approver = requireLogin();
        return Result.ok("操作已拒绝", operationService.reject(id, approver));
    }

    private LoginUser requireLogin() {
        LoginUser user = UserContext.get();
        if (user == null) {
            throw new BizException(401, "未登录");
        }
        return user;
    }
}
