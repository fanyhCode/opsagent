package com.opsagent.ai;

import com.opsagent.common.BizException;
import com.opsagent.security.LoginUser;
import com.opsagent.security.UserContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * AI 对话服务（M3.2：带工具调用的 Agent）。
 *
 * 整个 Agent 循环其实是 Spring AI 帮我们跑的：
 * 用户提问 -> 模型决定调用哪个工具 -> Spring AI 执行工具 -> 结果回喂给模型 ->
 * 模型可能继续调用工具 -> 直到给出最终回答。
 *
 * 我们要做的只有三件事：
 * 1. 用 @Tool 把平台能力暴露给模型（见 SystemMonitorTools）；
 * 2. 把工具对象交给 ChatClient（下面的 .tools(...)）；
 * 3. 用 ToolCallRecorder 记录调用轨迹，让前端能看到 Agent 做了什么。
 */
@Service
public class AiChatService {

    private static final Logger log = LoggerFactory.getLogger(AiChatService.class);

    /**
     * 系统提示词：定义角色、回答规范，以及什么时候该用工具。
     * 提示词写得好不好，直接决定 Agent 会不会主动去采集真实数据。
     */
    private static final String SYSTEM_PROMPT = """
            你是 OpsAgent，一个 Linux 运维与故障诊断助手，服务对象是运维工程师。

            回答要求：
            1. 全程使用中文，表达简洁，结论先行，必要时分点说明；
            2. 涉及 Linux 命令时，给出命令本身并说明它的作用；
            3. 不确定的信息不要编造；
            4. 涉及重启服务、删除文件等有风险的操作时，必须提醒风险并建议人工确认。

            工具使用规则：
            1. 当问题涉及服务器当前的真实状态（CPU、内存、磁盘、负载、进程）时，
               必须先用工具采集数据，再基于真实数据回答，禁止凭经验编造数值；
            2. 如果用户没有指明是哪台服务器，先用 listServers 看看平台纳管了哪些服务器；
            3. 工具返回错误（比如 SSH 连不上）时，如实说明失败原因并给出排查建议；
            4. 你目前只有只读工具，不能执行重启容器、删除文件这类写操作。
               遇到这类诉求，给出建议命令并提醒需要人工确认后再执行。
            """;

    private final ChatClient chatClient;
    private final AiUsageService aiUsageService;
    private final SystemMonitorTools systemMonitorTools;

    public AiChatService(ChatClient.Builder chatClientBuilder,
                         AiUsageService aiUsageService,
                         SystemMonitorTools systemMonitorTools) {
        this.chatClient = chatClientBuilder
                .defaultSystem(SYSTEM_PROMPT)
                .build();
        this.aiUsageService = aiUsageService;
        this.systemMonitorTools = systemMonitorTools;
    }

    /**
     * 发起一次对话，模型可以自主调用只读工具。
     */
    public ChatResult chat(String userMessage) {
        if (userMessage == null || userMessage.isBlank()) {
            throw new BizException("请输入内容");
        }

        // 清空本线程上一轮的工具调用记录
        ToolCallRecorder.start();

        try {
            long start = System.currentTimeMillis();

            // .tools(...) 把工具对象交给模型：Spring AI 会自动完成
            // 模型要求调用 -> 执行工具 -> 结果回喂 -> 继续推理 的多轮循环
            ChatResponse response = chatClient.prompt()
                    .user(userMessage)
                    .tools(systemMonitorTools)
                    .call()
                    .chatResponse();

            long duration = System.currentTimeMillis() - start;
            String answer = response.getResult().getOutput().getText();

            recordUsage(response, duration);
            List<ToolCallRecord> toolCalls = ToolCallRecorder.finish();
            log.info("AI 对话完成，耗时 {} ms，调用工具 {} 次", duration, toolCalls.size());

            return new ChatResult(answer, toolCalls);

        } catch (Exception e) {
            // 异常路径也要清理 ThreadLocal，否则线程复用时会串数据
            ToolCallRecorder.finish();
            log.error("调用大模型失败", e);
            throw new BizException("调用 AI 服务失败：" + AiErrorTranslator.friendly(e));
        }
    }

    /**
     * 记录本次调用的 token 用量与耗时。
     * 即使记录失败也不能影响对话本身，所以整体包了 try/catch。
     */
    private void recordUsage(ChatResponse response, long duration) {
        try {
            String model = response.getMetadata() == null ? null : response.getMetadata().getModel();
            Usage usage = response.getMetadata() == null ? null : response.getMetadata().getUsage();

            Integer promptTokens = usage == null ? 0 : usage.getPromptTokens();
            Integer completionTokens = usage == null ? 0 : usage.getCompletionTokens();
            Integer totalTokens = usage == null ? 0 : usage.getTotalTokens();

            LoginUser currentUser = UserContext.get();
            aiUsageService.record(
                    currentUser == null ? null : currentUser.id(),
                    model, promptTokens, completionTokens, totalTokens, duration);
        } catch (Exception e) {
            log.warn("记录 token 用量失败：{}", e.getMessage());
        }
    }
}
