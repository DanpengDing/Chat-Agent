package com.shanyangcode.infintechatagent.ai;

import com.shanyangcode.infintechatagent.config.ChatMemoryConfig;
import com.shanyangcode.infintechatagent.memory.CompressibleChatMemory;
import com.shanyangcode.infintechatagent.memory.TokenCountChatMemoryCompressor;
import com.shanyangcode.infintechatagent.tool.EmailTool;
import com.shanyangcode.infintechatagent.tool.RagTool;
import com.shanyangcode.infintechatagent.tool.TimeTool;
import dev.langchain4j.community.store.memory.chat.redis.RedisChatMemoryStore;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.mcp.McpToolProvider;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.rag.RetrievalAugmentor;
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
    private RetrievalAugmentor retrievalAugmentor;

    @Resource
    private RagTool ragTool;

    @Resource
    private EmailTool emailTool;

    @Resource
    private StreamingChatModel streamingChatModel;

    @Bean
    public AiChat aiChat() {
        return AiServices.builder(AiChat.class)
                .streamingChatModel(streamingChatModel)
                .retrievalAugmentor(retrievalAugmentor)
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
                //调用工具出现幻觉解决策略
                .hallucinatedToolNameStrategy(toolExecutionRequest -> ToolExecutionResultMessage.from(
                        toolExecutionRequest, "错误：没有名为 " + toolExecutionRequest.name() + " 的工具"))
                .build();
    }
}
