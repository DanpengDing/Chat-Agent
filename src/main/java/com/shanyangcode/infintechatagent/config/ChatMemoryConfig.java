package com.shanyangcode.infintechatagent.config;

import com.shanyangcode.infintechatagent.memory.TokenCountChatMemoryCompressor;
import dev.langchain4j.model.chat.ChatModel;
import jakarta.annotation.PostConstruct;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "chat.memory")
@Data
@Slf4j
public class ChatMemoryConfig {

    private int maxMessages = 20;
    private Compression compression = new Compression();
    private Redis redis = new Redis();

    @PostConstruct
    public void init() {
        log.info("=== Chat memory compression config loaded ===");
        log.info("maxMessages={}", maxMessages);
        log.info("tokenThreshold={}", compression.getTokenThreshold());
        log.info("recentRounds={}", compression.getRecentRounds());
        log.info("recentTokenLimit={}", compression.getRecentTokenLimit());
        log.info("summaryTokenLimit={}", compression.getSummaryTokenLimit());
        log.info("fallbackRecentRounds={}", compression.getFallbackRecentRounds());
        log.info("redisLockExpireSeconds={}", redis.getLock().getExpireSeconds());
        log.info("redisLockRetryTimes={}", redis.getLock().getRetryTimes());
    }

    @Data
    public static class Compression {
        private int tokenThreshold = 6000;
        private int recentRounds = 5;
        private int recentTokenLimit = 2000;
        private int summaryTokenLimit = 500;
        private String summaryPrompt;
        private int fallbackRecentRounds = 10;
    }

    @Data
    public static class Redis {
        private int ttlSeconds = 3600;
        private Lock lock = new Lock();
    }

    @Data
    public static class Lock {
        private int expireSeconds = 5;
        private int retryTimes = 3;
        private int retryIntervalMs = 100;
    }

    @Bean
    public TokenCountChatMemoryCompressor tokenCountChatMemoryCompressor(ChatModel chatModel) {
        log.info("Create TokenCountChatMemoryCompressor bean");
        return new TokenCountChatMemoryCompressor(
                compression.getRecentRounds(),
                compression.getRecentTokenLimit(),
                compression.getSummaryTokenLimit(),
                compression.getSummaryPrompt(),
                chatModel
        );
    }
}
