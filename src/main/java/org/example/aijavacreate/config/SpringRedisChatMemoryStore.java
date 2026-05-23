package org.example.aijavacreate.config;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ChatMessageDeserializer;
import dev.langchain4j.data.message.ChatMessageSerializer;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.util.Collections;
import java.util.List;

import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

/**
 * 基于 Spring Data Redis 的 ChatMemoryStore 实现，使用标准 GET/SET 命令，无需 RedisJSON 模块。
 * 序列化使用 langchain4j 内置的 ChatMessageSerializer / ChatMessageDeserializer，确保 ChatMessage 多态类型正确处理。
 */
@Slf4j
public class SpringRedisChatMemoryStore implements ChatMemoryStore {

    private static final String KEY_PREFIX = "chat-memory:";

    private final StringRedisTemplate redisTemplate;
    private final Duration ttl;

    public SpringRedisChatMemoryStore(StringRedisTemplate redisTemplate, Duration ttl) {
        this.redisTemplate = ensureNotNull(redisTemplate, "redisTemplate");
        this.ttl = ensureNotNull(ttl, "ttl");
    }

    @Override
    public List<ChatMessage> getMessages(Object memoryId) {
        String key = buildKey(memoryId);
        String json = redisTemplate.opsForValue().get(key);
        if (json == null || json.isEmpty()) {
            return Collections.emptyList();
        }
        try {
            return ChatMessageDeserializer.messagesFromJson(json);
        } catch (Exception e) {
            log.error("反序列化聊天记忆失败，memoryId: {}, 将删除该 key", memoryId, e);
            redisTemplate.delete(key);
            return Collections.emptyList();
        }
    }

    @Override
    public void updateMessages(Object memoryId, List<ChatMessage> messages) {
        String key = buildKey(memoryId);
        try {
            String json = ChatMessageSerializer.messagesToJson(messages);
            redisTemplate.opsForValue().set(key, json, ttl);
        } catch (Exception e) {
            log.error("序列化聊天记忆失败，memoryId: {}", memoryId, e);
        }
    }

    @Override
    public void deleteMessages(Object memoryId) {
        String key = buildKey(memoryId);
        redisTemplate.delete(key);
    }

    private String buildKey(Object memoryId) {
        return KEY_PREFIX + memoryId;
    }
}
