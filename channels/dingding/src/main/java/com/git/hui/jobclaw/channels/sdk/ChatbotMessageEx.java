package com.git.hui.jobclaw.channels.sdk;

import com.dingtalk.open.app.api.models.bot.ChatbotMessage;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class ChatbotMessageEx extends ChatbotMessage {
    private String robotId;
    private String aiCardId;
}