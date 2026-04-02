package com.shanyangcode.infintechatagent.ai;

import com.shanyangcode.infintechatagent.config.ChatMemoryConfig;
import com.shanyangcode.infintechatagent.memory.CompressibleChatMemory;
import com.shanyangcode.infintechatagent.memory.TokenCountChatMemoryCompressor;
import com.shanyangcode.infintechatagent.tool.EmailTool;
import com.shanyangcode.infintechatagent.tool.RagTool;
import com.shanyangcode.infintechatagent.tool.TimeTool;
import dev.langchain4j.community.store.memory.chat.redis.RedisChatMemoryStore;
import dev.langchain4j.mcp.McpToolProvider;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.service.AiServices;
import jakarta.annotation.Resource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.RedisTemplate;

@Configuration
public class AiChatService {

    @Resource
    private ChatModel chatModel;

    @Resource
    private McpToolProvider mcpToolProvider;

    @Resource
    private RedisChatMemoryStore redisChatMemoryStore;

    @Resource
    private TokenCountChatMemoryCompressor compressor;

    @Resource
    private ChatMemoryConfig chatMemoryConfig;

    @Resource
    private RedisTemplate<String, Object> redisTemplate;

    @Resource
    private ContentRetriever contentRetriever;

    @Resource
    private RagTool ragTool;

    @Resource
    private EmailTool emailTool;

    @Resource
    private StreamingChatModel streamingChatModel;

    @Bean
    public AiChat aiChat() {

        //把结构提供给AiServices，会自动创建代理对象，就相当于一个agent了
        return AiServices.builder(AiChat.class)
                .streamingChatModel(streamingChatModel)
                .contentRetriever(contentRetriever)
                .chatMemoryProvider(memoryId -> new CompressibleChatMemory(
                        memoryId,
                        redisChatMemoryStore,
                        compressor,
                        chatMemoryConfig.getMaxMessages(),
                        chatMemoryConfig.getCompression().getTokenThreshold(),
                        chatMemoryConfig.getCompression().getFallbackRecentRounds(),
                        redisTemplate,
                        chatMemoryConfig.getRedis().getLock().getExpireSeconds(),
                        chatMemoryConfig.getRedis().getLock().getRetryTimes(),
                        chatMemoryConfig.getRedis().getLock().getRetryIntervalMs()
                ))
                .tools(new TimeTool(), ragTool, emailTool)
                .toolProvider(mcpToolProvider)
                .build();
    }

}