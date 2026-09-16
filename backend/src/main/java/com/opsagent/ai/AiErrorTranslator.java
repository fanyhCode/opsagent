package com.opsagent.ai;

/**
 * 大模型调用失败的提示翻译。
 *
 * 调用外部 API 失败是常态（Key 无效、余额不足、网络抖动、限流），
 * 直接把底层异常抛给前端，用户只会看到一串看不懂的 JSON。
 * 这里统一翻译成人能看懂的中文提示，两个服务共用。
 */
public final class AiErrorTranslator {

    private AiErrorTranslator() {
    }

    public static String friendly(Exception e) {
        String message = e.getMessage() == null ? "" : e.getMessage();
        if (message.contains("401") || message.contains("Unauthorized")
                || message.contains("invalid_api_key") || message.contains("Authentication Fails")) {
            return "API Key 无效，请检查 IDEA 运行配置里的 DEEPSEEK_API_KEY";
        }
        if (message.contains("402") || message.contains("Insufficient Balance")) {
            return "DeepSeek 账户余额不足，请先充值";
        }
        if (message.contains("429") || message.contains("rate limit")) {
            return "请求过于频繁，请稍后再试";
        }
        if (message.contains("timeout") || message.contains("timed out")) {
            return "请求超时，请检查网络或稍后重试";
        }
        if (message.contains("not-configured")) {
            return "还没有配置 API Key（环境变量 DEEPSEEK_API_KEY）";
        }
        if (message.contains("UnknownHost") || message.contains("Connection refused")) {
            return "无法连接 DeepSeek 服务，请检查网络或代理设置";
        }
        return message;
    }
}
