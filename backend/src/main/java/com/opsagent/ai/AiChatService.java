package com.opsagent.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.opsagent.common.BizException;
import com.opsagent.dto.ChatResult;
import com.opsagent.entity.ChatMessage;
import com.opsagent.entity.ChatSession;
import com.opsagent.security.LoginUser;
import com.opsagent.security.UserContext;
import com.opsagent.service.ChatSessionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * AI 对话服务（M3.3：带工具调用 + 多轮对话记忆）。
 *
 * 一次对话的完整流程：
 * 1. 没有 sessionId 就新建一个会话，有就取出并**校验归属**；
 * 2. 取出最近 N 条历史消息，和本次提问一起拼成消息列表交给模型；
 * 3. 模型可以自主调用只读工具（Spring AI 负责循环）；
 * 4. 把用户提问、助手回答、工具轨迹都落库，并更新会话标题与活动时间。
 *
 * 第 2 步是"记忆"的本质：模型本身是无状态的，所谓的上下文，
 * 就是每一轮我们都把最近的历史重新发给它而已。
 */
@Service
public class AiChatService {

    private static final Logger log = LoggerFactory.getLogger(AiChatService.class);

    /**
     * 系统提示词：定义角色、回答规范，以及什么时候该用工具。
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
            5. 排查"服务器变慢""服务异常"这类问题时，除了看系统指标，还要看容器：
               先用 getContainerList 看有哪些容器在运行，再对可疑容器使用 getContainerLogs、
               searchLogs、getErrorStatistics 查日志，最后把"指标异常"和"日志异常"关联起来下结论，
               不要只看一个指标就下判断。
            6. 你不能直接执行任何写操作（重启容器、删除文件等）。
               如果确实需要重启容器，请调用 proposeRestartContainer 提交给用户人工确认，
               并在回答中明确告诉用户"已提交待确认，需要你在界面上确认后才会执行"。
               特别注意：当用户**明确要求**重启某个容器时，必须调用 proposeRestartContainer 提交，
               而不是只在回答里给建议或直接拒绝——人工确认环节由用户在界面上完成。
               不确定某个操作是否被允许时，先用 listSupportedOperations 查询。

            对话要求：
            对话是多轮的，用户可能会用"它""那台机器""刚才说的服务"这类指代，
            请结合上下文理解，不要每次都重新询问是哪台服务器。
            """;

    private final ChatClient chatClient;
    private final AiUsageService aiUsageService;
    private final SystemMonitorTools systemMonitorTools;
    private final ContainerTools containerTools;
    private final OperationTools operationTools;
    private final ChatSessionService chatSessionService;
    private final ObjectMapper objectMapper;

    public AiChatService(ChatClient.Builder chatClientBuilder,
                         AiUsageService aiUsageService,
                         SystemMonitorTools systemMonitorTools,
                         ContainerTools containerTools,
                         OperationTools operationTools,
                         ChatSessionService chatSessionService,
                         ObjectMapper objectMapper) {
        this.chatClient = chatClientBuilder
                .defaultSystem(SYSTEM_PROMPT)
                .build();
        this.aiUsageService = aiUsageService;
        this.systemMonitorTools = systemMonitorTools;
        this.containerTools = containerTools;
        this.operationTools = operationTools;
        this.chatSessionService = chatSessionService;
        this.objectMapper = objectMapper;
    }

    /**
     * 发起一次对话（自动处理会话与上下文）。
     */
    public ChatResult chat(Long sessionId, String userMessage, Long userId) {
        if (userMessage == null || userMessage.isBlank()) {
            throw new BizException("请输入内容");
        }
        if (userId == null) {
            throw new BizException(401, "未登录");
        }

        // 1. 找会话：没有就新建，有就校验归属
        ChatSession session = (sessionId == null)
                ? chatSessionService.create(userId)
                : chatSessionService.getOwned(sessionId, userId);

        // 2. 取最近的历史消息（带条数上限，控制 token 成本）
        List<ChatMessage> history = chatSessionService.recentMessages(
                session.getId(), ChatSessionService.MAX_HISTORY_MESSAGES);

        // 3. 先保存用户这条提问
        chatSessionService.append(session.getId(), ChatMessage.ROLE_USER, userMessage, null);

        // 4. 组装交给模型的消息：历史 + 本次提问
        List<Message> modelMessages = new ArrayList<>();
        for (ChatMessage item : history) {
            modelMessages.add(ChatMessage.ROLE_USER.equals(item.getRole())
                    ? new UserMessage(item.getContent())
                    : new AssistantMessage(item.getContent()));
        }
        modelMessages.add(new UserMessage(userMessage));

        ToolCallRecorder.start();
        // 把会话 id 放进线程上下文：工具里"提出操作"时需要记录来源会话
        ChatContextHolder.set(session.getId());

        try {
            long start = System.currentTimeMillis();

            ChatResponse response = chatClient.prompt()
                    .messages(modelMessages)
                    .tools(systemMonitorTools, containerTools, operationTools)
                    .call()
                    .chatResponse();

            long duration = System.currentTimeMillis() - start;
            String answer = response.getResult().getOutput().getText();
            List<ToolCallRecord> toolCalls = ToolCallRecorder.finish();

            // 5. 落库：助手回答 + 工具轨迹，并更新会话标题与活动时间
            chatSessionService.append(session.getId(), ChatMessage.ROLE_ASSISTANT,
                    answer, toJson(toolCalls));
            chatSessionService.autoTitle(session, userMessage);
            chatSessionService.touch(session);

            recordUsage(response, duration);
            log.info("AI 对话完成 session={} 耗时 {} ms，工具调用 {} 次",
                    session.getId(), duration, toolCalls.size());

            return new ChatResult(session.getId(), answer, toolCalls);

        } catch (Exception e) {
            // 异常路径也要清理 ThreadLocal，否则线程复用时会串数据
            ToolCallRecorder.finish();
            ChatContextHolder.clear();
            log.error("调用大模型失败", e);
            throw new BizException("调用 AI 服务失败：" + AiErrorTranslator.friendly(e));
        } finally {
            ChatContextHolder.clear();
        }
    }

    /** 工具轨迹序列化成 JSON 存库（字段名保持稳定，方便以后解析） */
    private String toJson(List<ToolCallRecord> toolCalls) {
        try {
            return objectMapper.writeValueAsString(toolCalls);
        } catch (Exception e) {
            log.warn("序列化工具轨迹失败：{}", e.getMessage());
            return null;
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
