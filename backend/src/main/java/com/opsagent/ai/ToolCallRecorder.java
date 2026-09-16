package com.opsagent.ai;

import java.util.ArrayList;
import java.util.List;

/**
 * 工具调用记录器（基于 ThreadLocal）。
 *
 * 为什么需要它？
 * Spring AI 会自动帮我们完成"模型要求调用工具 → 执行工具 → 把结果回喂给模型"这一整套循环，
 * 但这样就看不到中间过程了。而"Agent 实际调用了哪些工具"恰恰是这个项目最想展示的东西
 * （简历里的核心亮点，也是前端对话页面的看点）。
 *
 * 做法：每次对话开始前清空本线程的记录列表，工具方法执行时往里面写一条，
 * 对话结束后取出来返回给前端。
 *
 * 注意：依赖"工具在发起对话的同一个线程里执行"这一前提（Spring AI 同步调用满足）。
 * 如果将来改成异步/流式调用，需要换成基于请求上下文的传递方式。
 */
public final class ToolCallRecorder {

    private static final ThreadLocal<List<ToolCallRecord>> HOLDER =
            ThreadLocal.withInitial(ArrayList::new);

    private ToolCallRecorder() {
    }

    /** 对话开始前调用，清空上一轮的记录 */
    public static void start() {
        HOLDER.set(new ArrayList<>());
    }

    /** 工具方法执行时调用 */
    public static void record(String tool, String arguments, String result,
                              long durationMs, boolean success) {
        HOLDER.get().add(new ToolCallRecord(tool, arguments, summarize(result), durationMs, success));
    }

    /** 对话结束后调用，取出记录并清理 ThreadLocal（避免线程复用串数据） */
    public static List<ToolCallRecord> finish() {
        List<ToolCallRecord> records = HOLDER.get();
        HOLDER.remove();
        return records;
    }

    /** 结果摘要：界面不需要展示全部内容，截断即可 */
    private static String summarize(String result) {
        if (result == null) {
            return "";
        }
        String singleLine = result.replaceAll("\\s+", " ").trim();
        return singleLine.length() <= 160 ? singleLine : singleLine.substring(0, 160) + "…";
    }
}
