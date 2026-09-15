package com.opsagent.dto;

import java.util.List;

/**
 * 一次采集得到的服务器指标快照。
 *
 * 为什么用 record 嵌套 record？因为指标是只读的数据结构，
 * record 天然不可变、代码量少，而且嵌套类型能让结构一目了然：
 * 内存、磁盘、进程各自是一小块，不会混在一个大对象里。
 */
public record SystemMetrics(

        /** 主机名 */
        String hostname,

        /** CPU 使用率（百分比），由 100 - 空闲率 得到 */
        double cpuUsagePercent,

        /** 1 分钟、5 分钟、15 分钟平均负载 */
        double load1,
        double load5,
        double load15,

        /** 运行时长，例如 "up 3 hours, 20 minutes" */
        String uptime,

        /** 内存信息 */
        Memory memory,

        /** 根分区磁盘信息 */
        Disk disk,

        /** CPU 占用最高的进程 */
        List<Process> topProcesses,

        /** 采集时间（服务器返回的本地时间字符串） */
        String collectedAt
) {

    /** 内存（单位 MB） */
    public record Memory(long totalMb, long usedMb, long availableMb, double usagePercent) {
    }

    /** 磁盘分区 */
    public record Disk(String filesystem, String size, String used, String avail,
                       String usePercent, String mountedOn) {
    }

    /** 进程信息 */
    public record Process(String pid, String command, double cpuPercent, double memPercent) {
    }
}
