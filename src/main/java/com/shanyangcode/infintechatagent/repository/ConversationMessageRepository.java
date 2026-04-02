package com.shanyangcode.infintechatagent.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class ConversationMessageRepository {

    private final JdbcTemplate jdbcTemplate;

    public void saveMessage(String requestId, Long sessionId, Long userId, String role, String content) {
        String sql = """
                INSERT INTO conversation_message (request_id, session_id, user_id, role, content)
                VALUES (?, ?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE
                    content = VALUES(content),
                    updated_at = CURRENT_TIMESTAMP
                """;
        jdbcTemplate.update(sql, requestId, sessionId, userId, role, content);
    }
}
