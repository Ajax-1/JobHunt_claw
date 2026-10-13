package com.git.hui.jobclaw.core.cli;

import com.git.hui.jobclaw.core.agent.BizAgent;
import com.git.hui.jobclaw.core.agent.models.LlmRspCell;
import com.git.hui.jobclaw.core.agent.models.UserConversationInfo;
import com.git.hui.jobclaw.core.apis.permission.AgentPermission;
import com.git.hui.jobclaw.core.channel.ChannelReceiveMessage;
import com.git.hui.jobclaw.core.router.intent.AgentRegistry;
import com.git.hui.jobclaw.core.router.intent.PresetAgentIntro;
import com.git.hui.jobclaw.core.router.intent.SessionAgentBinder;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class PlanModeCommandHandlerTest {

    @Test
    void bindsConversationToPlanAgent() {
        RecordingSessionAgentBinder binder = new RecordingSessionAgentBinder();
        PlanModeCommandHandler handler = new PlanModeCommandHandler(binder, new AvailablePlanAgentRegistry());
        UserConversationInfo conversation = new UserConversationInfo("user-1", "test", "conversation-1", false);
        AtomicReference<String> response = new AtomicReference<>();

        boolean handled = handler.handle(new ChannelReceiveMessage(), conversation, "/plan", text -> {
            response.set(text);
            return true;
        });

        assertThat(handled).isTrue();
        assertThat(binder.boundUserId).isEqualTo("user-1");
        assertThat(binder.boundSessionId).isEqualTo("conversation-1");
        assertThat(binder.boundAgentId).isEqualTo(PresetAgentIntro.PLAN.getAgentId());
        assertThat(response.get()).contains("已进入计划模式").contains("/reset");
    }

    @Test
    void supportsOnlyExactPlanCommand() {
        PlanModeCommandHandler handler = new PlanModeCommandHandler(
                new RecordingSessionAgentBinder(), new AvailablePlanAgentRegistry());

        assertThat(handler.supports("/plan")).isTrue();
        assertThat(handler.supports("/plan ")).isTrue();
        assertThat(handler.supports("/planet")).isFalse();
        assertThat(handler.supports("/plan build something")).isFalse();
    }

    @Test
    void doesNotBindWhenPlanAgentIsUnavailable() {
        RecordingSessionAgentBinder binder = new RecordingSessionAgentBinder();
        PlanModeCommandHandler handler = new PlanModeCommandHandler(binder, new EmptyAgentRegistry());
        AtomicReference<String> response = new AtomicReference<>();

        handler.handle(new ChannelReceiveMessage(),
                new UserConversationInfo("user-1", "test", "conversation-1", false),
                "/plan",
                text -> {
                    response.set(text);
                    return true;
                });

        assertThat(binder.boundAgentId).isNull();
        assertThat(response.get()).contains("PlanNotebook");
    }

    private static class AvailablePlanAgentRegistry implements AgentRegistry {
        @Override
        public void register(BizAgent agent) {
        }

        @Override
        public Optional<BizAgent> getAgent(String agentId) {
            return Optional.of(new AvailablePlanAgent());
        }

        @Override
        public List<BizAgent> getAgentsForIntent(PresetAgentIntro intentType) {
            return List.of();
        }

        @Override
        public Optional<BizAgent> getDefaultAgent() {
            return Optional.empty();
        }

        @Override
        public List<BizAgent> getAllAgents(String jobClawUserId) {
            return List.of();
        }

        @Override
        public boolean unregister(String agentId) {
            return false;
        }
    }

    private static class EmptyAgentRegistry extends AvailablePlanAgentRegistry {
        @Override
        public Optional<BizAgent> getAgent(String agentId) {
            return Optional.empty();
        }
    }

    private static class AvailablePlanAgent implements BizAgent {
        @Override
        public AgentPermission permission() {
            return AgentPermission.TOTAL;
        }

        @Override
        public AgentIntro getAgentIntro() {
            return PresetAgentIntro.PLAN;
        }

        @Override
        public String process(UserConversationInfo userConversationInfo, ChannelReceiveMessage message) {
            return null;
        }

        @Override
        public Flux<LlmRspCell> stream(UserConversationInfo userConversationInfo, ChannelReceiveMessage message) {
            return Flux.empty();
        }
    }

    private static class RecordingSessionAgentBinder implements SessionAgentBinder {
        private String boundUserId;
        private String boundSessionId;
        private String boundAgentId;

        @Override
        public void bind(String jobClawUserId, String sessionId, String agentId) {
            boundUserId = jobClawUserId;
            boundSessionId = sessionId;
            boundAgentId = agentId;
        }

        @Override
        public Optional<BoundAgentInfo> getBoundAgent(String jobClawUserId, String sessionId) {
            return Optional.empty();
        }

        @Override
        public void unbind(String jobClawUserId, String sessionId) {
        }

        @Override
        public boolean needsIntentRecognition(String jobClawUserId, String sessionId, String userMessage) {
            return true;
        }

        @Override
        public List<IntentHistoryItem> getIntentHistory(String jobClawUserId, String sessionId) {
            return List.of(new IntentHistoryItem(PresetAgentIntro.PLAN, 1.0, Instant.now()));
        }
    }
}
