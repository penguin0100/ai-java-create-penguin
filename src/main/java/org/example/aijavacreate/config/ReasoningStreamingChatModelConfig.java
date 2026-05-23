package org.example.aijavacreate.config;

import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import lombok.Data;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.Map;

@Configuration
@ConfigurationProperties(prefix = "langchain4j.open-ai.reasoning-streaming-chat-model")
@Data
public class ReasoningStreamingChatModelConfig {

    private String baseUrl;

    private String apiKey;

    private String modelName;

    private Integer maxTokens;

    private Double temperature;

    private Boolean logRequests = false;

    private Boolean logResponses = false;

    /**
     * 是否返回深度思考内容（reasoning_content）
     */
    @Value("${langchain4j.open-ai.reasoning-return-thinking:true}")
    private Boolean reasoningReturnThinking;

    /**
     * 是否在请求中回放深度思考内容
     */
    @Value("${langchain4j.open-ai.reasoning-send-thinking:true}")
    private Boolean reasoningSendThinking;

    /**
     * 深度思考字段名称，DeepSeek 使用 reasoning_content
     */
    @Value("${langchain4j.open-ai.reasoning-thinking-field:reasoning_content}")
    private String reasoningThinkingField;

    /**
     * DeepSeek V4 思考开关，设置为 enabled 开启深度思考。
     * 留空则不发送 thinking 参数，兼容其它 OpenAI 格式供应商。
     */
    @Value("${langchain4j.open-ai.reasoning-thinking-type:}")
    private String reasoningThinkingType;

    /**
     * DeepSeek V4 思考强度（high/max），留空则使用供应商默认值。
     */
    @Value("${langchain4j.open-ai.reasoning-effort:}")
    private String reasoningEffort;

    /**
     * 推理流式模型（用于 Vue 项目生成，带工具调用和深度思考）
     */
    @Bean
    @Scope("prototype")
    public StreamingChatModel reasoningStreamingChatModelPrototype() {
        OpenAiStreamingChatModel.OpenAiStreamingChatModelBuilder builder = OpenAiStreamingChatModel.builder()
                .apiKey(apiKey)
                .baseUrl(baseUrl)
                .modelName(modelName)
                .maxTokens(maxTokens)
                .temperature(temperature)
                .strictJsonSchema(true)
                .strictTools(true)
                .parallelToolCalls(false)
                .returnThinking(Boolean.TRUE.equals(reasoningReturnThinking))
                .sendThinking(Boolean.TRUE.equals(reasoningSendThinking), reasoningThinkingField)
                .logRequests(Boolean.TRUE.equals(logRequests))
                .logResponses(Boolean.TRUE.equals(logResponses));

        if (StringUtils.hasText(reasoningEffort)) {
            builder.reasoningEffort(reasoningEffort);
        }
        if (StringUtils.hasText(reasoningThinkingType)) {
            Map<String, Object> customParameters = new HashMap<>();
            customParameters.put("thinking", Map.of("type", reasoningThinkingType));
            builder.customParameters(customParameters);
        }
        return builder.build();
    }
}
