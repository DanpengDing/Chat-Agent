package com.shanyangcode.infintechatagent.mq;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shanyangcode.infintechatagent.model.dto.ConversationPersistenceMessage;
import com.shanyangcode.infintechatagent.repository.ConversationMessageRepository;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.apis.ClientConfiguration;
import org.apache.rocketmq.client.apis.ClientConfigurationBuilder;
import org.apache.rocketmq.client.apis.ClientException;
import org.apache.rocketmq.client.apis.ClientServiceProvider;
import org.apache.rocketmq.client.apis.consumer.FilterExpression;
import org.apache.rocketmq.client.apis.consumer.SimpleConsumer;
import org.apache.rocketmq.client.apis.message.MessageView;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class ConversationPersistenceConsumer {

    private final ObjectMapper objectMapper;
    private final ConversationMessageRepository conversationMessageRepository;

    @Value("${app.chat.persistence.consumer-endpoint}")
    private String endpoint;

    @Value("${app.chat.persistence.topic}")
    private String topic;

    @Value("${app.chat.persistence.consumer-group}")
    private String consumerGroup;

    private final AtomicBoolean running = new AtomicBoolean(false);
    private ExecutorService executorService;
    private SimpleConsumer consumer;

    @PostConstruct
    public void init() throws ClientException {
        ClientServiceProvider provider = ClientServiceProvider.loadService();
        ClientConfigurationBuilder builder = ClientConfiguration.newBuilder()
                .setEndpoints(endpoint);
        ClientConfiguration configuration = builder.build();
        consumer = provider.newSimpleConsumerBuilder()
                .setClientConfiguration(configuration)
                .setConsumerGroup(consumerGroup)
                .setSubscriptionExpressions(Map.of(topic, FilterExpression.SUB_ALL))
                .setAwaitDuration(Duration.ofSeconds(15))
                .build();

        running.set(true);
        executorService = Executors.newSingleThreadExecutor(r -> {
            Thread thread = new Thread(r, "chat-persistence-consumer");
            thread.setDaemon(true);
            return thread;
        });
        executorService.submit(this::consumeLoop);
    }

    private void consumeLoop() {
        while (running.get()) {
            try {
                List<MessageView> messages = consumer.receive(16, Duration.ofSeconds(15));
                for (MessageView messageView : messages) {
                    handleMessage(messageView);
                }
            } catch (RuntimeException e) {
                if (isInterrupted(e)) {
                    Thread.currentThread().interrupt();
                    log.info("RocketMQ consumer loop interrupted during shutdown");
                    break;
                }
                log.error("Unexpected runtime error in RocketMQ consume loop", e);
                sleepQuietly(1000);
            } catch (ClientException e) {
                if (!running.get()) {
                    log.info("RocketMQ consumer stopped");
                    break;
                }
                log.error("Failed to receive RocketMQ messages", e);
                sleepQuietly(1000);
            } catch (Exception e) {
                if (e instanceof InterruptedException || !running.get()) {
                    Thread.currentThread().interrupt();
                    log.info("RocketMQ consumer loop interrupted during shutdown");
                    break;
                }
                log.error("Unexpected error in RocketMQ consume loop", e);
                sleepQuietly(1000);
            }
        }
    }

    private void handleMessage(MessageView messageView) {
        String payload = toUtf8(messageView.getBody());
        try {
            ConversationPersistenceMessage message = objectMapper.readValue(payload, ConversationPersistenceMessage.class);
            conversationMessageRepository.saveMessage(
                    message.getRequestId(),
                    message.getSessionId(),
                    message.getUserId(),
                    "user",
                    message.getQuestion()
            );
            conversationMessageRepository.saveMessage(
                    message.getRequestId(),
                    message.getSessionId(),
                    message.getUserId(),
                    "assistant",
                    message.getAnswer()
            );
            consumer.ack(messageView);
        } catch (Exception e) {
            log.error("Failed to consume persistence message, messageId={}, payload={}",
                    messageView.getMessageId(), payload, e);
        }
    }

    private String toUtf8(ByteBuffer buffer) {
        ByteBuffer duplicate = buffer.asReadOnlyBuffer();
        byte[] bytes = new byte[duplicate.remaining()];
        duplicate.get(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private boolean isInterrupted(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof InterruptedException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    @PreDestroy
    public void destroy() {
        running.set(false);
        if (consumer != null) {
            try {
                consumer.close();
            } catch (IOException e) {
                log.warn("Failed to close RocketMQ consumer cleanly", e);
            }
        }
        if (executorService != null) {
            executorService.shutdownNow();
            try {
                executorService.awaitTermination(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
