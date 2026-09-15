package com.opsagent.ssh;

/**
 * 远程命令执行结果。
 *
 * @param exitStatus 命令退出码，0 表示成功
 * @param stdout     标准输出
 * @param stderr     标准错误
 */
public record SshCommandResult(int exitStatus, String stdout, String stderr) {
}
