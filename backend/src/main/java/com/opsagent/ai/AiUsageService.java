package com.opsagent.ai;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.opsagent.dto.AiUsageSummary;
import com.opsagent.entity.AiUsage;
import com.opsagent.mapper.AiUsageMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/**
 * AI 用量与余额服务。
 *
 * 数据来源有两处：
 * 1. **token 用量**：每次调用大模型时，响应里会带上本次消耗的 token 数，
 *    我们把它记进 ai_usage 表，再按"今日/累计"做汇总——这是自己统计的数据；
 * 2. **账户余额**：DeepSeek 提供了余额查询接口 GET /user/balance，直接调它拿真实余额。
 *
 * 为什么要给余额加缓存？余额变化很慢，而前端刷新页面、每次对话后都会查一次，
 * 每次都打外部接口既慢又浪费配额，所以缓存 5 分钟。
 */
@Service
public class AiUsageService {

    private static final Logger log = LoggerFactory.getLogger(AiUsageService.class);

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final AiUsageMapper usageMapper;
    private final RestClient restClient;
    private final String baseUrl;
    private final String apiKey;
    private final BigDecimal warnThreshold;
    private final long cacheSeconds;

    /** 余额缓存（volatile 保证多线程下可见性） */
    private volatile BalanceCache balanceCache;

    public AiUsageService(AiUsageMapper usageMapper,
                          @Value("${spring.ai.openai.base-url}") String baseUrl,
                          @Value("${spring.ai.openai.api-key}") String apiKey,
                          @Value("${opsagent.ai.balance-warn-threshold:5}") BigDecimal warnThreshold,
                          @Value("${opsagent.ai.balance-cache-seconds:300}") long cacheSeconds) {
        this.usageMapper = usageMapper;
        this.baseUrl = baseUrl;
        this.apiKey = apiKey;
        this.warnThreshold = warnThreshold;
        this.cacheSeconds = cacheSeconds;
        // Spring 6 自带的 RestClient：写 HTTP 调用比 RestTemplate 更简洁
        this.restClient = RestClient.builder().build();
    }

    /**
     * 记录一次 AI 调用。
     * 注意：记流水失败绝不能影响正常对话，所以这里整体 try/catch，只记日志。
     */
    public void record(Long userId, String model, Integer promptTokens,
                       Integer completionTokens, Integer totalTokens, long durationMs) {
        try {
            AiUsage row = new AiUsage();
            row.setUserId(userId);
            row.setModel(model == null ? "" : model);
            row.setPromptTokens(nvl(promptTokens));
            row.setCompletionTokens(nvl(completionTokens));
            row.setTotalTokens(nvl(totalTokens));
            row.setDurationMs(durationMs);
            row.setCreatedAt(LocalDateTime.now());
            usageMapper.insert(row);
        } catch (Exception e) {
            log.warn("记录 AI 用量失败（不影响对话）：{}", e.getMessage());
        }
    }

    /** 汇总用量与余额，供前端展示 */
    public AiUsageSummary summary() {
        long[] today = aggregate(LocalDate.now().atStartOfDay());
        long[] total = aggregate(null);
        BalanceInfo balance = balance();

        boolean lowBalance = false;
        if (balance.balance() != null) {
            try {
                lowBalance = new BigDecimal(balance.balance()).compareTo(warnThreshold) < 0;
            } catch (NumberFormatException ignored) {
                // 余额字段解析不了就不告警，避免误报
            }
        }

        return new AiUsageSummary(
                today[0], today[1],
                total[0], total[1],
                balance.currency(),
                balance.balance(),
                balance.available(),
                lowBalance,
                warnThreshold.toPlainString(),
                balance.error(),
                balance.checkedAt());
    }

    /**
     * 统计调用次数与 token 总数。
     * since 为 null 表示统计全部（累计），否则统计该时间点之后的（今日）。
     */
    private long[] aggregate(LocalDateTime since) {
        QueryWrapper<AiUsage> wrapper = new QueryWrapper<>();
        wrapper.select("COUNT(*) AS cnt", "IFNULL(SUM(total_tokens), 0) AS tokens");
        if (since != null) {
            wrapper.ge("created_at", since);
        }

        List<Map<String, Object>> rows = usageMapper.selectMaps(wrapper);
        if (rows == null || rows.isEmpty()) {
            return new long[]{0, 0};
        }
        Map<String, Object> row = rows.get(0);
        return new long[]{toLong(row.get("cnt")), toLong(row.get("tokens"))};
    }

    /** 取余额：优先用缓存，缓存过期再查接口 */
    private BalanceInfo balance() {
        BalanceCache cached = balanceCache;
        if (cached != null && (System.currentTimeMillis() - cached.timestamp()) / 1000 < cacheSeconds) {
            return cached.info();
        }
        BalanceInfo fresh = fetchBalance();
        balanceCache = new BalanceCache(fresh, System.currentTimeMillis());
        return fresh;
    }

    /**
     * 调用 DeepSeek 的余额接口。
     * 返回结构大致是：
     * {"is_available":true,"balance_infos":[{"currency":"CNY","total_balance":"110.00",...}]}
     */
    private BalanceInfo fetchBalance() {
        String checkedAt = LocalDateTime.now().format(TIME_FORMAT);
        try {
            Map<String, Object> body = restClient.get()
                    .uri(baseUrl + "/user/balance")
                    .header("Authorization", "Bearer " + apiKey)
                    .retrieve()
                    .body(new ParameterizedTypeReference<Map<String, Object>>() {
                    });

            if (body == null) {
                return new BalanceInfo(null, null, false, "余额接口没有返回内容", checkedAt);
            }

            boolean available = Boolean.TRUE.equals(body.get("is_available"));
            Object infos = body.get("balance_infos");
            if (infos instanceof List<?> list && !list.isEmpty() && list.get(0) instanceof Map<?, ?> first) {
                return new BalanceInfo(
                        str(first.get("currency")),
                        str(first.get("total_balance")),
                        available,
                        null,
                        checkedAt);
            }
            return new BalanceInfo(null, null, available, "余额接口返回格式与预期不符", checkedAt);

        } catch (Exception e) {
            log.warn("查询 DeepSeek 余额失败：{}", e.getMessage());
            return new BalanceInfo(null, null, false, AiErrorTranslator.friendly(e), checkedAt);
        }
    }

    private String str(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private long toLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (Exception e) {
            return 0;
        }
    }

    private int nvl(Integer value) {
        return value == null ? 0 : value;
    }

    /** 余额信息 */
    private record BalanceInfo(String currency, String balance, boolean available,
                               String error, String checkedAt) {
    }

    /** 带时间戳的缓存包装 */
    private record BalanceCache(BalanceInfo info, long timestamp) {
    }
}
