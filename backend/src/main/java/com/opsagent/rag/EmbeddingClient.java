package com.opsagent.rag;

import com.opsagent.common.BizException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 向量化客户端：把文本转成向量（embedding）。
 *
 * 用的是百炼（DashScope）的 OpenAI 兼容接口 /embeddings，
 * 请求格式和 OpenAI 完全一致，所以将来换成其它厂商只需要改配置里的
 * base-url 与 model，代码一行都不用动。
 *
 * 为什么用 HTTP 直连而不是官方 SDK？
 * 好处是不引入额外依赖、不受 SDK 版本影响、出错时能直接看到原始响应，排查更简单。
 * 只有模型必须走 SDK（例如多模态向量）时才需要引入 SDK。
 */
@Component
public class EmbeddingClient {

    private static final Logger log = LoggerFactory.getLogger(EmbeddingClient.class);

    /** 一次请求最多提交多少条文本。批次太大容易触发接口限制，所以拆开发送。 */
    private static final int BATCH_SIZE = 8;

    private final RestClient restClient;
    private final String baseUrl;
    private final String apiKey;
    private final String model;

    public EmbeddingClient(@Value("${opsagent.rag.base-url}") String baseUrl,
                           @Value("${opsagent.rag.api-key}") String apiKey,
                           @Value("${opsagent.rag.model}") String model) {
        this.baseUrl = baseUrl;
        this.apiKey = apiKey;
        this.model = model;
        this.restClient = RestClient.builder().build();
    }

    public String getModel() {
        return model;
    }

    /** API Key 是否已配置 */
    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank() && !"not-configured".equals(apiKey);
    }

    /**
     * 批量向量化。
     * 返回顺序与输入顺序一一对应（这一点很重要，错位会导致切片和向量对不上）。
     */
    public List<double[]> embed(List<String> texts) {
        if (texts == null || texts.isEmpty()) {
            return List.of();
        }
        if (!isConfigured()) {
            throw new BizException("还没有配置向量模型的 API Key（环境变量 DASHSCOPE_API_KEY）");
        }

        List<double[]> vectors = new ArrayList<>();
        for (int start = 0; start < texts.size(); start += BATCH_SIZE) {
            List<String> batch = texts.subList(start, Math.min(start + BATCH_SIZE, texts.size()));
            vectors.addAll(embedBatch(batch));
        }
        return vectors;
    }

    public double[] embedOne(String text) {
        return embed(List.of(text)).get(0);
    }

    private List<double[]> embedBatch(List<String> batch) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("input", batch);

        try {
            Map<String, Object> response = restClient.post()
                    .uri(baseUrl + "/embeddings")
                    .header("Authorization", "Bearer " + apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(new ParameterizedTypeReference<Map<String, Object>>() {
                    });

            if (response == null || !(response.get("data") instanceof List<?> data) || data.isEmpty()) {
                throw new BizException("向量化接口返回内容为空");
            }

            // 按 index 排序，确保向量与输入文本顺序一致
            List<Map<String, Object>> items = new ArrayList<>();
            for (Object item : data) {
                if (item instanceof Map<?, ?> map) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> row = (Map<String, Object>) map;
                    items.add(row);
                }
            }
            items.sort(Comparator.comparingInt(m -> toInt(m.get("index"))));

            List<double[]> vectors = new ArrayList<>();
            for (Map<String, Object> item : items) {
                if (!(item.get("embedding") instanceof List<?> raw)) {
                    throw new BizException("向量化接口返回格式异常：缺少 embedding 字段");
                }
                double[] vector = new double[raw.size()];
                for (int i = 0; i < raw.size(); i++) {
                    vector[i] = ((Number) raw.get(i)).doubleValue();
                }
                vectors.add(vector);
            }

            log.info("向量化完成：model={} 文本 {} 条，维度 {}", model, vectors.size(),
                    vectors.isEmpty() ? 0 : vectors.get(0).length);
            return vectors;

        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.error("调用向量化接口失败", e);
            throw new BizException("调用向量模型失败：" + friendlyMessage(e));
        }
    }

    /** 把常见错误翻译成人能看懂的提示 */
    private String friendlyMessage(Exception e) {
        String message = e.getMessage() == null ? "" : e.getMessage();
        if (message.contains("401") || message.contains("Unauthorized") || message.contains("invalid_api_key")) {
            return "API Key 无效，请检查环境变量 DASHSCOPE_API_KEY";
        }
        if (message.contains("400") || message.contains("model")) {
            return "模型名可能不对（当前配置：" + model + "），请核对控制台里的模型名称。原始信息：" + message;
        }
        if (message.contains("timeout") || message.contains("timed out")) {
            return "请求超时，请检查网络";
        }
        return message;
    }

    private int toInt(Object value) {
        return value instanceof Number number ? number.intValue() : 0;
    }
}
