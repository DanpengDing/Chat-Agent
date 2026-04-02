package com.shanyangcode.infintechatagent.mq;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shanyangcode.infintechatagent.model.dto.ConversationPersistenceMessage;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.apis.ClientConfiguration;
import org.apache.rocketmq.client.apis.ClientConfigurationBuilder;
import org.apache.rocketmq.client.apis.ClientException;
import org.apache.rocketmq.client.apis.ClientServiceProvider;
import org.apache.rocketmq.client.apis.message.Message;
import org.apache.rocketmq.client.apis.producer.Producer;
import org.apache.rocketmq.client.apis.producer.SendReceipt;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class ConversationPersistenceProducer {

    private final ObjectMapper objectMapper;

    @Value("${app.chat.persistence.producer-endpoint}")
    private String endpoint;

    @Value("${app.chat.persistence.topic}")
    private String topic;

    @Value("${app.chat.persistence.tag}")
    private String tag;

    private Producer producer;
    private ClientServiceProvider provider;

    @PostConstruct
    public void init() throws ClientException {
        provider = ClientServiceProvider.loadService();
        ClientConfigurationBuilder builder = ClientConfiguration.newBuilder()
                .setEndpoints(endpoint);
        ClientConfiguration configuration = builder.build();
        producer = provider.newProducerBuilder()
                .setTopics(topic)
                .setClientConfiguration(configuration)
                .build();
    }

    public void send(ConversationPersistenceMessage message) {
        try {
            Message mqMessage = provider.newMessageBuilder()
                    .setTopic(topic)
                    .setKeys(message.getRequestId())
                    .setTag(tag)
                    .setBody(objectMapper.writeValueAsBytes(message))
                    .build();
            SendReceipt sendReceipt = producer.send(mqMessage);
            log.info("发送持久化消息, messageId={}, requestId={}",
                    sendReceipt.getMessageId(), message.getRequestId());
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("持久化消息序列化失败", e);
        } catch (ClientException e) {
            log.error("持久化消息发送失败, endpoint={}, topic={}, requestId={}",
                    endpoint, topic, message.getRequestId(), e);
            throw new IllegalStateException("Failed to send persistence message", e);
        }
    }

    @PreDestroy
    public void destroy() {
        if (producer != null) {
            try {
                producer.close();
            } catch (IOException e) {
                log.warn("Failed to close RocketMQ producer cleanly", e);
            }
        }
    }
}