package com.shanyangcode.infintechatagent.ai;

import com.shanyangcode.infintechatagent.guardrail.SafeRepairOutputGuardrail;
import com.shanyangcode.infintechatagent.guardrail.SafeInputGuardrail;
import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.TokenStream;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.guardrail.InputGuardrails;
import dev.langchain4j.service.guardrail.OutputGuardrails;


@InputGuardrails({SafeInputGuardrail.class})
@OutputGuardrails(value = {SafeRepairOutputGuardrail.class}, maxRetries = 0)
public interface AiChat {

    @SystemMessage(fromResource = "system-prompt/chat-bot.txt")
    String chat(@MemoryId Long sessionId, @UserMessage String prompt);


    @SystemMessage(fromResource = "system-prompt/chat-bot.txt")
    TokenStream streamChat(@MemoryId Long sessionId, @UserMessage String prompt);
}
