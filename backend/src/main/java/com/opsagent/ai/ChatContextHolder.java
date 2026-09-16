package com.opsagent.ai;

/**
 * 当前对话上下文（ThreadLocal）。
 *
 * 为什么需要它？工具方法（比如"提出一个操作"）需要知道"这条操作是从哪个会话里提出的"，
 * 但工具方法的参数是由模型决定的，我们不能指望模型自己把 sessionId 传对。
 * 所以在对话开始时把 sessionId 放进 ThreadLocal，工具执行时直接取——业务代码无感。
 *
 * 用完必须 clear()，理由和 UserContext 一样：Tomcat 线程是复用的。
 */
public final class ChatContextHolder {

    private static final ThreadLocal<Long> SESSION_ID = new ThreadLocal<>();

    private ChatContextHolder() {
    }

    public static void set(Long sessionId) {
        SESSION_ID.set(sessionId);
    }

    public static Long get() {
        return SESSION_ID.get();
    }

    public static void clear() {
        SESSION_ID.remove();
    }
}
