package com.opsagent.ssh;

import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import com.opsagent.common.BizException;
import com.opsagent.entity.ServerInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

/**
 * SSH 命令执行器：整个平台"伸手去操作 Linux"的唯一出口。
 *
 * 为什么要有这一层？后面 Agent 的工具（Linux/Docker/日志/JVM）全都靠它执行远程命令，
 * 把它单独封装成组件有几个好处：
 * 1. 统一处理超时、异常、编码；
 * 2. 后面做"命令安全执行引擎"时，只要在这一层前面加校验即可，不用改各处调用代码；
 * 3. 将来换成连接池、换成密钥登录、换成跳板机，都只改这一个地方。
 */
@Service
public class SshExecutor {

    private static final Logger log = LoggerFactory.getLogger(SshExecutor.class);

    /** 默认超时时间（秒）。远程命令必须设超时，否则网络异常时会一直挂住线程。 */
    private static final int DEFAULT_TIMEOUT_SECONDS = 15;

    public SshCommandResult execute(ServerInfo server, String command) {
        return execute(server, command, DEFAULT_TIMEOUT_SECONDS);
    }

    /**
     * 在目标服务器上执行命令。
     *
     * 注意：这是一条"能执行任意命令"的能力，非常危险。
     * 现阶段只有平台内部的监控模块调用它，且命令是代码里写死的；
     * 将来开放给 Agent 动态生成命令时，必须先经过命令白名单与风险分级校验（M5）。
     */
    public SshCommandResult execute(ServerInfo server, String command, int timeoutSeconds) {
        Session session = null;
        ChannelExec channel = null;
        try {
            JSch jsch = new JSch();
            session = jsch.getSession(server.getUsername(), server.getHost(), server.getPort());
            session.setPassword(server.getPassword());
            // 演示环境跳过主机指纹校验（否则首次连接会交互式询问 yes/no，程序会卡住）。
            // 生产环境必须校验 known_hosts，否则存在中间人攻击风险。
            session.setConfig("StrictHostKeyChecking", "no");
            session.setTimeout(timeoutSeconds * 1000);
            session.connect(timeoutSeconds * 1000);

            channel = (ChannelExec) session.openChannel("exec");
            channel.setCommand(command);
            channel.setInputStream(null);

            ByteArrayOutputStream stdout = new ByteArrayOutputStream();
            ByteArrayOutputStream stderr = new ByteArrayOutputStream();
            channel.setOutputStream(stdout);
            channel.setErrStream(stderr);
            channel.connect(timeoutSeconds * 1000);

            // JSch 的 exec 通道不会自己等命令结束，需要轮询 + 自己控制超时
            long deadline = System.currentTimeMillis() + timeoutSeconds * 1000L;
            while (!channel.isClosed()) {
                if (System.currentTimeMillis() > deadline) {
                    throw new BizException("远程命令执行超时（" + timeoutSeconds + " 秒）");
                }
                Thread.sleep(100);
            }

            int exitStatus = channel.getExitStatus();
            String out = stdout.toString(StandardCharsets.UTF_8);
            String err = stderr.toString(StandardCharsets.UTF_8);

            log.info("SSH 执行完成：host={} exit={} 输出 {} 字节", server.getHost(), exitStatus, out.length());
            return new SshCommandResult(exitStatus, out, err);

        } catch (JSchException e) {
            throw new BizException("SSH 连接失败：" + friendlyMessage(e));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BizException("SSH 执行被中断");
        } finally {
            // 一定要断开，否则连接会泄漏，服务器上的 sshd 进程也会堆积
            if (channel != null) {
                channel.disconnect();
            }
            if (session != null) {
                session.disconnect();
            }
        }
    }

    /**
     * 把底层异常翻译成人能看懂的提示。
     * 排查问题时，"认证失败"和"端口不通"是完全不同的处理方向，提示必须能区分。
     */
    private String friendlyMessage(JSchException e) {
        String message = e.getMessage() == null ? "" : e.getMessage();
        if (message.contains("Auth fail")) {
            return "用户名或密码错误（请检查 server 表里的 SSH 账号密码）";
        }
        if (message.contains("timeout") || message.contains("SocketTimeout")) {
            return "连接超时，请检查网络与防火墙";
        }
        if (message.contains("Connection refused")) {
            return "目标主机拒绝连接，请确认 SSH 服务是否启动（systemctl status ssh）";
        }
        if (message.contains("UnknownHost") || message.contains("unreachable")) {
            return "主机地址不可达：" + server_host(e);
        }
        return message;
    }

    private String server_host(JSchException e) {
        return e.getMessage() == null ? "" : e.getMessage();
    }
}
