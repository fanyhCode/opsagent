package com.opsagent.scheduler;

import com.opsagent.entity.ServerInfo;
import com.opsagent.service.MonitorService;
import com.opsagent.service.ServerInfoService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 指标定时采集任务。
 *
 * @Scheduled 的两种常见写法：
 * - fixedRateString：以上一次"开始执行"的时间为基准，固定间隔触发（本类用这种，采集间隔稳定）；
 * - fixedDelayString：以上一次"执行结束"的时间为基准，适合任务耗时差异大的场景。
 *
 * 参数从配置文件读取（${opsagent.monitor.interval-ms:30000}），
 * 冒号后面的 30000 是"配置项不存在时的默认值"。
 */
@Component
public class MetricCollectScheduler {

    private static final Logger log = LoggerFactory.getLogger(MetricCollectScheduler.class);

    private final MonitorService monitorService;
    private final ServerInfoService serverInfoService;

    public MetricCollectScheduler(MonitorService monitorService, ServerInfoService serverInfoService) {
        this.monitorService = monitorService;
        this.serverInfoService = serverInfoService;
    }

    /** 应用启动 15 秒后开始首次采集，之后按配置间隔重复 */
    @Scheduled(fixedRateString = "${opsagent.monitor.interval-ms:30000}", initialDelayString = "15000")
    public void collectAllServers() {
        List<ServerInfo> servers = serverInfoService.listAll();
        if (servers.isEmpty()) {
            return;
        }

        int success = 0;
        for (ServerInfo server : servers) {
            // 没配置 SSH 凭据的服务器直接跳过，不产生无意义的日志
            if (server.getUsername() == null || server.getUsername().isBlank()
                    || server.getPassword() == null || server.getPassword().isBlank()) {
                continue;
            }
            try {
                monitorService.collectAndSave(server);
                success++;
            } catch (Exception e) {
                // 关键设计：单台服务器采集失败不能影响其它服务器。
                // 定时任务里最忌讳"一个异常把整轮任务打断"，所以这里必须逐台捕获。
                log.warn("采集失败 server={} host={} 原因={}",
                        server.getName(), server.getHost(), e.getMessage());
            }
        }

        log.info("本轮指标采集完成：成功 {}/{} 台", success, servers.size());
    }
}
