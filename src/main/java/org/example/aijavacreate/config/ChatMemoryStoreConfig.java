package org.example.aijavacreate.config;

import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;

@Configuration
public class ChatMemoryStoreConfig {

    @Value("${spring.data.redis.ttl:3600}")
    private long ttl;

    @Bean
    public ChatMemoryStore chatMemoryStore(StringRedisTemplate redisTemplate) {
        return new SpringRedisChatMemoryStore(redisTemplate, Duration.ofSeconds(ttl));
    }
}
