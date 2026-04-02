package com.shanyangcode.infintechatagent.model.dto;

import lombok.Data;

@Data
public class ConversationPersistenceMessage {

    private String requestId;

    private Long sessionId;

    private Long userId;

    private String question;

    private String answer;
}
