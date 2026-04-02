package com.shanyangcode.infintechatagent.controller;

import com.shanyangcode.infintechatagent.Monitor.MonitorContext;
import com.shanyangcode.infintechatagent.Monitor.MonitorContextHolder;
import com.shanyangcode.infintechatagent.ai.AiChat;
import com.shanyangcode.infintechatagent.model.dto.ChatRequest;
import com.shanyangcode.infintechatagent.model.dto.KnowledgeRequest;
import com.shanyangcode.infintechatagent.orchestrator.SimpleOrchestrator;
import com.shanyangcode.infintechatagent.ratelimit.RateLimit;
import com.shanyangcode.infintechatagent.ratelimit.RateLimitRule;
import com.shanyangcode.infintechatagent.ratelimit.RateLimitTarget;
import com.shanyangcode.infintechatagent.service.StreamChatService;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.store.embedding.EmbeddingStoreIngestor;
import jakarta.annotation.Resource;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RateIntervalUnit;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@Slf4j
public class AiChatController {

    @Resource
    private AiChat aiChat;

    @Resource
    private StreamChatService streamChatService;

    @Resource
    private SimpleOrchestrator simpleOrchestrator;

    @Resource
    private EmbeddingStoreIngestor embeddingStoreIngestor;

    @Value("${rag.docs-path}")
    private String docsPath;

    private static final String TARGET_FILENAME = "InfiniteChat.md";

    @PostMapping("/chat")
    @RateLimit(
            keyPrefix = "ai:chat",
            rules = {
                    @RateLimitRule(target = RateLimitTarget.USER, rate = 5, rateInterval = 1, rateIntervalUnit = RateIntervalUnit.SECONDS),
                    @RateLimitRule(target = RateLimitTarget.IP, rate = 20, rateInterval = 1, rateIntervalUnit = RateIntervalUnit.SECONDS),
                    @RateLimitRule(target = RateLimitTarget.API, rate = 50, rateInterval = 1, rateIntervalUnit = RateIntervalUnit.SECONDS)
            }
    )
    public String chat(@RequestBody ChatRequest chatRequest) {
        MonitorContextHolder.setContext(MonitorContext.builder()
                .userId(chatRequest.getUserId())
                .sessionId(chatRequest.getSessionId())
                .build());
        try {
            return aiChat.chat(chatRequest.getSessionId(), chatRequest.getQuery());
        } finally {
            MonitorContextHolder.clearContext();
        }
    }

    @PostMapping(value = "/streamChat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @RateLimit(
            keyPrefix = "ai:stream-chat",
            rules = {
                    @RateLimitRule(target = RateLimitTarget.USER, rate = 2, rateInterval = 1, rateIntervalUnit = RateIntervalUnit.MINUTES),
                    @RateLimitRule(target = RateLimitTarget.IP, rate = 2, rateInterval = 1, rateIntervalUnit = RateIntervalUnit.MINUTES),
                    @RateLimitRule(target = RateLimitTarget.API, rate = 2, rateInterval = 1, rateIntervalUnit = RateIntervalUnit.MINUTES)
            }
    )
    public SseEmitter streamChat(@RequestBody ChatRequest chatRequest) {
        return streamChatService.stream(chatRequest);
    }

    @PostMapping("/multiAgentChat")
    @RateLimit(
            keyPrefix = "ai:multi-agent-chat",
            rules = {
                    @RateLimitRule(target = RateLimitTarget.USER, rate = 2, rateInterval = 1, rateIntervalUnit = RateIntervalUnit.SECONDS),
                    @RateLimitRule(target = RateLimitTarget.IP, rate = 10, rateInterval = 1, rateIntervalUnit = RateIntervalUnit.SECONDS),
                    @RateLimitRule(target = RateLimitTarget.API, rate = 20, rateInterval = 1, rateIntervalUnit = RateIntervalUnit.SECONDS)
            }
    )
    public String multiAgentChat(@RequestBody ChatRequest chatRequest) {
        MonitorContextHolder.setContext(MonitorContext.builder()
                .userId(chatRequest.getUserId())
                .sessionId(chatRequest.getSessionId())
                .build());
        try {
            return simpleOrchestrator.process(chatRequest.getSessionId(), chatRequest.getQuery());
        } finally {
            MonitorContextHolder.clearContext();
        }
    }

    @PostMapping("/insert")
    public String insertKnowledge(@RequestBody KnowledgeRequest knowledgeRequest) {
        String formattedContent = String.format("### Q: %s%n%nA: %s",
                knowledgeRequest.getQuestion(),
                knowledgeRequest.getAnswer());

        boolean writeSuccess = appendToFile(formattedContent, knowledgeRequest.getSourceName());
        if (!writeSuccess) {
            return "Insert failed: cannot write local file";
        }

        try {
            String sourceName = knowledgeRequest.getSourceName() != null
                    ? knowledgeRequest.getSourceName()
                    : TARGET_FILENAME;
            Metadata metadata = Metadata.from("file_name", sourceName);
            Document document = Document.from(formattedContent, metadata);
            embeddingStoreIngestor.ingest(document);

            log.info("RAG insert success: {}", knowledgeRequest.getQuestion());
            return "Insert success: synced to file and vector store";
        } catch (Exception e) {
            log.error("RAG vectorization failed", e);
            return "Insert partially succeeded: file written, vector store update failed";
        }
    }

    private synchronized boolean appendToFile(String content, String sourceName) {
        try {
            Path filePath = Paths.get(docsPath, sourceName);
            if (!Files.exists(filePath)) {
                Files.createDirectories(filePath.getParent());
                Files.createFile(filePath);
            }

            Files.writeString(
                    filePath,
                    System.lineSeparator() + System.lineSeparator() + content,
                    StandardOpenOption.APPEND,
                    StandardOpenOption.CREATE
            );
            return true;
        } catch (IOException e) {
            log.error("Failed to write local RAG document", e);
            return false;
        }
    }
}
