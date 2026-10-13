package com.git.hui.jobclaw.plugins.plannotebook;

import com.alibaba.ttl.TransmittableThreadLocal;
import com.git.hui.jobclaw.core.agent.react.ReActMiddleware;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.model.tool.ToolCallingChatOptions;

import java.util.List;

/**
 * Injects the current plan after a ReAct tool call so later reasoning stays on track.
 */
public class PlanHintReActMiddleware implements ReActMiddleware {

    private static final String HINT_PREFIX = "<plan-notebook-hint>";

    private final PlanNotebook notebook;
    private final ThreadLocal<String> currentUserId = new TransmittableThreadLocal<>();

    public PlanHintReActMiddleware(PlanNotebook notebook) {
        this.notebook = notebook;
    }

    @Override
    public void setContext(ChatClientRequest request, String chatId) {
        if (request.prompt().getOptions() instanceof ToolCallingChatOptions options) {
            if (options.getToolContext() == null) {
                currentUserId.remove();
                return;
            }
            Object userId = options.getToolContext().get("jobClawUserId");
            if (userId instanceof String value && !value.isBlank()) {
                currentUserId.set(value);
            } else {
                currentUserId.remove();
            }
        }
    }

    @Override
    public void beforeReasoning(List<Message> messages, int iter, String chatId) {
        String userId = currentUserId.get();
        if (userId == null) {
            return;
        }
        messages.removeIf(message -> message.getText() != null && message.getText().startsWith(HINT_PREFIX));
        String hint = notebook.generateHint(userId);
        if (!hint.isBlank()) {
            messages.add(new SystemMessage(hint));
        }
    }

    @Override
    public void onComplete(int totalIters, String finalResponse, String chatId) {
        currentUserId.remove();
    }

    @Override
    public void onError(Exception error, int iter, String chatId) {
        currentUserId.remove();
    }
}
