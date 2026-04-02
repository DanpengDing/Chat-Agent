package com.shanyangcode.infintechatagent.guardrail;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.guardrail.OutputGuardrail;
import dev.langchain4j.guardrail.OutputGuardrailResult;

import java.util.Set;

/**
 * Restricts model output to low-risk, user-safe troubleshooting advice.
 * High-risk repair guidance is rewritten to an official after-sales recommendation.
 */
public class SafeRepairOutputGuardrail implements OutputGuardrail {

    private static final Set<String> HIGH_RISK_KEYWORDS = Set.of(
            "拆机",
            "拆开",
            "拆后盖",
            "打开后盖",
            "拆屏",
            "换屏",
            "换电池",
            "更换电池",
            "主板",
            "更换主板",
            "排线",
            "断开排线",
            "焊接",
            "补焊",
            "飞线",
            "短接",
            "跳线",
            "加热",
            "开胶",
            "撬开",
            "刷机",
            "刷写固件",
            "拆除",
            "自行维修",
            "自己修",
            "内部元件",
            "电池鼓包",
            "高压",
            "电源板"
    );

    private static final String SAFE_REDIRECT_MESSAGE =
            "该问题可能涉及拆机或内部硬件维修，继续自行操作存在安全风险。"
                    + "建议停止自行拆解或维修，优先备份数据（如设备仍可正常开机），"
                    + "并尽快前往官方售后或授权维修点进行检测和处理。";

    @Override
    public OutputGuardrailResult validate(AiMessage aiMessage) {
        String text = aiMessage == null ? "" : aiMessage.text();
        if (text == null || text.isBlank()) {
            return success();
        }

        for (String keyword : HIGH_RISK_KEYWORDS) {
            if (text.contains(keyword)) {
                return successWith(SAFE_REDIRECT_MESSAGE);
            }
        }

        return success();
    }
}
