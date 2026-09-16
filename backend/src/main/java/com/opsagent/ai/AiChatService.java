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

/**
 * AI 对话服务（M3.1：先打通普通对话）。
 *
 * 这里用的是 Spring AI 的 ChatClient：
 *   chatClient.prompt().user("...").call().chatResponse() —— 拿到完整响应（含 token 用量）
 *
 * 为什么要封装成一个 Service 而不是直接写在 Controller 里？
 * 1. 系统提示词集中管理；
 * 2. 统一异常处理与日志；
 * 3. 统一记录用量（每次调用都会写一条 ai_usage 记录）；
 * 4. 下一步做工具调用时，只要在这里加 .tools(...)，Controller 不用改。
 */
@Service
public class AiChatService {

    private static final Logger log = LoggerFactory.getLogger(AiChatService.class);

    /**
     * 系统提示词：定义 Agent 的角色与回答规范。
     * 用 Java 17 的文本块（"""）书写，多行提示词不用再拼字符串。
     */
    private static final String SYSTEM_PROMPT = """
            你是 OpsAgent，一个 Linux 运维与故障诊断助手，服务对象是运维工程师。

            回答要求：
            1. 全程使用中文，表达简洁，结论先行，必要时分点说明；
            2. 涉及 Linux 命令时，给出命令本身并说明它的作用；
            3. 不确定的信息不要编造。如果缺少数据（比如没有实时指标、没有日志），
               要明确说明"还需要采集哪些信息"，而不是凭空猜测；
            4. 涉及重启服务、删除文件等有风险的操作时，必须提醒风险并建议先确认。
            """;

    private final ChatClient chatClient;
    private final AiUsageService aiUsageService;

    public AiChatService(ChatClient.Builder chatClientBuilder, AiUsageService aiUsageService) {
        this.chatClient = chatClientBuilder
                .defaultSystem(SYSTEM_PROMPT)
                .build();
        this.aiUsageService = aiUsageService;
    }

    /**
     * 一次普通对话（M3.2 会在此基础上加入工具调用）。
     */
    public String chat(String userMessage) {
        if (userMessage == null || userMessage.isBlank()) {
            throw new BizException("请输入内容");
        }
        try {
            long start = System.currentTimeMillis();

            // 用 chatResponse() 而不是 content()，因为我们要拿到 token 用量元数据
            ChatResponse response = chatClient.prompt()
                    .user(userMessage)
                    .call()
                    .chatResponse();

            long duration = System.currentTimeMillis() - start;
            String answer = response.getResult().getOutput().getText();

            recordUsage(response, duration);
            log.info("AI 对话完成，耗时 {} ms", duration);
            return answer;

        } catch (Exception e) {
            // 调用外部服务失败是常态（网络、额度、Key 无效），必须给出人能看懂的提示
            log.error("调用大模型失败", e);
            throw new BizException("调用 AI 服务失败：" + AiErrorTranslator.friendly(e));
        }
    }

    /** 记录本次调用的 token 用量与耗时 */
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
            // 统计失败不能影响对话本身
            log.warn("记录 token 用量失败：{}", e.getMessage());
        }
    }
}
