package com.shanyangcode.infintechatagent.service;

import com.shanyangcode.infintechatagent.Monitor.MonitorContext;
import com.shanyangcode.infintechatagent.Monitor.MonitorContextHolder;
import com.shanyangcode.infintechatagent.ai.AiChat;
import com.shanyangcode.infintechatagent.model.dto.ChatRequest;
import com.shanyangcode.infintechatagent.model.dto.ConversationPersistenceMessage;
import com.shanyangcode.infintechatagent.model.dto.StreamEvent;
import com.shanyangcode.infintechatagent.mq.ConversationPersistenceProducer;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.service.TokenStream;
import dev.langchain4j.service.tool.ToolExecution;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Service
@Slf4j
public class StreamChatService {

    @Resource
    private AiChat aiChat;

    @Resource
    private ConversationPersistenceProducer conversationPersistenceProducer;

    public SseEmitter stream(ChatRequest chatRequest) {
        SseEmitter emitter = new SseEmitter(0L);
        CompletableFuture.runAsync(() -> doStream(chatRequest, emitter));
        return emitter;
    }

    private void doStream(ChatRequest chatRequest, SseEmitter emitter) {
        String requestId = UUID.randomUUID().toString().replace("-", "");
        MonitorContext context = MonitorContext.builder()
                .userId(chatRequest.getUserId())
                .sessionId(chatRequest.getSessionId())
                .build();

        StringBuilder answerBuilder = new StringBuilder();
        ThinkingParser thinkingParser = new ThinkingParser();

        try {
            MonitorContextHolder.setContext(context);
            sendEvent(emitter, "status", Map.of(
                    "requestId", requestId,
                    "stage", "started",
                    "message", "开始处理请求"
            ));

            TokenStream tokenStream = aiChat.streamChat(chatRequest.getSessionId(), chatRequest.getQuery())
                    .onRetrieved(contents -> handleRetrieved(emitter, contents))
                    .onToolExecuted(toolExecution -> handleToolExecuted(emitter, toolExecution))
                    .onPartialResponse(partial -> handlePartialResponse(emitter, partial, answerBuilder, thinkingParser))
                    .onCompleteResponse(chatResponse -> {
                        String finalAnswer = chatResponse.aiMessage() != null && chatResponse.aiMessage().text() != null
                                ? chatResponse.aiMessage().text()
                                : answerBuilder.toString();
                        answerBuilder.setLength(0);
                        answerBuilder.append(finalAnswer);

                        sendEvent(emitter, "done", Map.of(
                                "requestId", requestId,
                                "message", "回答完成"
                        ));
                        emitter.complete();
                        persistConversationAsync(chatRequest, requestId, finalAnswer);
                    })
                    .onError(error -> {
                        log.error("Stream chat failed, sessionId={}", chatRequest.getSessionId(), error);
                        sendEvent(emitter, "error", Map.of(
                                "requestId", requestId,
                                "message", error.getMessage()
                        ));
                        emitter.completeWithError(error);
                    });

            tokenStream.start();
        } catch (Exception e) {
            log.error("Failed to start stream chat, sessionId={}", chatRequest.getSessionId(), e);
            sendEvent(emitter, "error", Map.of(
                    "requestId", requestId,
                    "message", e.getMessage()
            ));
            emitter.completeWithError(e);
        } finally {
            MonitorContextHolder.clearContext();
        }
    }

    private void handleRetrieved(SseEmitter emitter, List<Content> contents) {
        sendEvent(emitter, "status", Map.of(
                "stage", "retrieved",
                "message", "已完成知识检索",
                "count", contents.size()
        ));
    }

    private void handleToolExecuted(SseEmitter emitter, ToolExecution toolExecution) {
        Map<String, Object> data = new HashMap<>();
        data.put("toolName", toolExecution.request().name());
        data.put("arguments", toolExecution.request().arguments());
        data.put("result", toolExecution.result());
        sendEvent(emitter, "tool_result", data);
    }

    private void handlePartialResponse(SseEmitter emitter, String partial, StringBuilder answerBuilder, ThinkingParser parser) {
        ParsedChunk parsedChunk = parser.parse(partial);
        if (!parsedChunk.thinking().isEmpty()) {
            sendEvent(emitter, "thinking", parsedChunk.thinking());
        }
        if (!parsedChunk.answer().isEmpty()) {
            answerBuilder.append(parsedChunk.answer());
            sendEvent(emitter, "answer", parsedChunk.answer());
        }
    }

    private void persistConversationAsync(ChatRequest chatRequest, String requestId, String finalAnswer) {
        CompletableFuture.runAsync(() -> {
            try {
                ConversationPersistenceMessage message = new ConversationPersistenceMessage();
                message.setRequestId(requestId);
                message.setSessionId(chatRequest.getSessionId());
                message.setUserId(chatRequest.getUserId());
                message.setQuestion(chatRequest.getQuery());
                message.setAnswer(finalAnswer);
                conversationPersistenceProducer.send(message);
            } catch (Exception e) {
                log.error("Persist conversation failed, sessionId={}", chatRequest.getSessionId(), e);
            }
        });
    }

    private void sendEvent(SseEmitter emitter, String eventName, Object data) {
        try {
            emitter.send(SseEmitter.event()
                    .name(eventName)
                    .data(new StreamEvent(eventName, data), MediaType.APPLICATION_JSON));
        } catch (IOException e) {
            throw new IllegalStateException("Failed to send SSE event", e);
        }
    }

    private record ParsedChunk(String thinking, String answer) {
    }

    private static class ThinkingParser {

        private static final String THINK_OPEN = "<think>";
        private static final String THINK_CLOSE = "</think>";

        private final StringBuilder buffer = new StringBuilder();
        private boolean inThinking;
        private boolean seenThinkingTag;

        ParsedChunk parse(String chunk) {
            buffer.append(chunk);
            StringBuilder thinking = new StringBuilder();
            StringBuilder answer = new StringBuilder();

            while (true) {
                if (inThinking) {
                    int closeIndex = buffer.indexOf(THINK_CLOSE);
                    if (closeIndex < 0) {
                        if (buffer.length() > THINK_CLOSE.length()) {
                            int flushLength = buffer.length() - THINK_CLOSE.length();
                            thinking.append(buffer, 0, flushLength);
                            buffer.delete(0, flushLength);
                        }
                        break;
                    }
                    thinking.append(buffer, 0, closeIndex);
                    buffer.delete(0, closeIndex + THINK_CLOSE.length());
                    inThinking = false;
                } else {
                    int openIndex = buffer.indexOf(THINK_OPEN);
                    if (openIndex < 0) {
                        if (seenThinkingTag) {
                            if (buffer.length() > THINK_OPEN.length()) {
                                int flushLength = buffer.length() - THINK_OPEN.length();
                                answer.append(buffer, 0, flushLength);
                                buffer.delete(0, flushLength);
                            }
                        } else {
                            answer.append(buffer);
                            buffer.setLength(0);
                        }
                        break;
                    }
                    seenThinkingTag = true;
                    if (openIndex > 0) {
                        answer.append(buffer, 0, openIndex);
                    }
                    buffer.delete(0, openIndex + THINK_OPEN.length());
                    inThinking = true;
                }
            }

            return new ParsedChunk(thinking.toString(), answer.toString());
        }
    }
}
