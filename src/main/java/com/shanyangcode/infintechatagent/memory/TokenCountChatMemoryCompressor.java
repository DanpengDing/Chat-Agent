package com.shanyangcode.infintechatagent.memory;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class TokenCountChatMemoryCompressor {

    private static final String DEFAULT_SUMMARY_PROMPT = """
            请将以下对话历史压缩为简洁摘要，严格控制在{tokenLimit} tokens以内。
            必须保留以下信息：
            1. 用户偏好与约束
            2. 已确认的重要事实
            3. 已做出的关键决策
            4. 当前核心诉求与待解决问题
            删除无意义寒暄与重复表述，只输出摘要正文。

            对话历史：
            {messages}
            """;

    private final int recentRounds;
    private final int recentTokenLimit;
    private final int summaryTokenLimit;
    private final String summaryPromptTemplate;
    private final ChatModel summaryChatModel;

    public TokenCountChatMemoryCompressor(int recentRounds,
                                          int recentTokenLimit,
                                          int summaryTokenLimit,
                                          String summaryPromptTemplate,
                                          ChatModel summaryChatModel) {
        this.recentRounds = recentRounds;
        this.recentTokenLimit = recentTokenLimit;
        this.summaryTokenLimit = summaryTokenLimit;
        this.summaryPromptTemplate = summaryPromptTemplate;
        this.summaryChatModel = summaryChatModel;
    }

    public List<ChatMessage> compress(List<ChatMessage> messages) {
        if (messages.size() <= recentRounds * 2) {
            log.debug("Skip compression, message count={} within recent window={}",
                    messages.size(), recentRounds * 2);
            return messages;
        }

        int splitIndex = messages.size() - recentRounds * 2;
        List<ChatMessage> oldMessages = messages.subList(0, splitIndex);
        List<ChatMessage> recentMessages = messages.subList(splitIndex, messages.size());

        int recentTokens = estimateTokens(recentMessages);
        if (recentTokens > recentTokenLimit) {
            log.warn("Recent messages token count {} exceeded limit {}", recentTokens, recentTokenLimit);
        }

        log.info("Compressing chat history, oldMessages={}, recentMessages={}",
                oldMessages.size(), recentMessages.size());

        String summary = generateSummary(oldMessages);

        List<ChatMessage> compressed = new ArrayList<>();
        compressed.add(SystemMessage.from("历史对话摘要:\n" + summary));
        compressed.addAll(recentMessages);

        log.info("Compression finished, originalMessages={}, compressedMessages={}",
                messages.size(), compressed.size());
        return compressed;
    }

    private String generateSummary(List<ChatMessage> messages) {
        if (messages.isEmpty()) {
            return "";
        }
        if (summaryChatModel != null) {
            try {
                String prompt = buildSummaryPrompt(messages);
                ChatResponse response = summaryChatModel.chat(List.of(
                        SystemMessage.from("你是一个会话记忆压缩助手，只返回可直接给模型使用的历史摘要。"),
                        UserMessage.from(prompt)
                ));
                if (response != null && response.aiMessage() != null) {
                    String summary = response.aiMessage().text();
                    if (summary != null && !summary.isBlank()) {
                        log.debug("LLM summary generated, length={}", summary.length());
                        return summary.trim();
                    }
                }
            } catch (Exception e) {
                log.warn("LLM summary generation failed, fallback to rule-based summary", e);
            }
        }

        return generateFallbackSummary(messages);
    }

    private String buildSummaryPrompt(List<ChatMessage> messages) {
        String template = summaryPromptTemplate == null || summaryPromptTemplate.isBlank()
                ? DEFAULT_SUMMARY_PROMPT
                : summaryPromptTemplate;
        return template
                .replace("{tokenLimit}", String.valueOf(summaryTokenLimit))
                .replace("{messages}", formatMessages(messages));
    }

    private String formatMessages(List<ChatMessage> messages) {
        StringBuilder builder = new StringBuilder();
        for (ChatMessage message : messages) {
            String text = extractText(message);
            if (text == null || text.isBlank()) {
                continue;
            }
            builder.append(roleOf(message))
                    .append(": ")
                    .append(text.trim())
                    .append('\n');
        }
        return builder.toString().trim();
    }

    private String roleOf(ChatMessage message) {
        if (message instanceof UserMessage) {
            return "user";
        }
        if (message instanceof AiMessage) {
            return "assistant";
        }
        if (message instanceof SystemMessage) {
            return "system";
        }
        return Objects.toString(message.type(), "unknown");
    }

    private String generateFallbackSummary(List<ChatMessage> messages) {
        StringBuilder summary = new StringBuilder();
        summary.append("共有").append(messages.size()).append("条历史消息。");

        for (int i = 0; i < Math.min(3, messages.size()); i++) {
            String text = extractText(messages.get(i));
            if (text != null && !text.isBlank()) {
                summary.append(' ')
                        .append(text, 0, Math.min(50, text.length()));
            }
        }

        log.debug("Fallback summary generated, length={}", summary.length());
        return summary.toString();
    }

    private String extractText(ChatMessage msg) {
        if (msg instanceof AiMessage aiMessage) {
            return aiMessage.text();
        }
        if (msg instanceof UserMessage userMessage) {
            return userMessage.singleText();
        }
        if (msg instanceof SystemMessage systemMessage) {
            return systemMessage.text();
        }
        return "";
    }

    public int estimateTokens(List<ChatMessage> messages) {
        int total = 0;
        for (ChatMessage msg : messages) {
            String text = extractText(msg);
            if (text != null) {
                total += text.length() / 4;
            }
        }
        return total;
    }
}
