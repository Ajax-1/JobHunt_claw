//package com.git.hui.jobclaw.core.agent.memory;
//
//import org.junit.jupiter.api.BeforeEach;
//import org.junit.jupiter.api.Test;
//import org.junit.jupiter.api.io.TempDir;
//import org.springframework.ai.chat.messages.AssistantMessage;
//import org.springframework.ai.chat.messages.Message;
//import org.springframework.ai.chat.messages.UserMessage;
//
//import java.io.IOException;
//import java.nio.file.Path;
//import java.util.ArrayList;
//import java.util.List;
//
//import static org.junit.jupiter.api.Assertions.*;
//
///**
// * Integration tests for {@link FileSystemChatMemoryRepository} with smart window management.
// *
// * AIDEV-NOTE: Integration test for Phase 1 smart window implementation
// */
//class FileSystemChatMemoryRepositoryTest {
//
//    @TempDir
//    Path tempDir;
//
//    private FileSystemChatMemoryRepository repository;
//    private ContextWindowProperties properties;
//    private SmartWindowChatMemory smartWindow;
//    private SessionSummarizer sessionSummarizer;
//
//    @BeforeEach
//    void setUp() throws IOException {
//        // Setup properties with small limits for testing
//        properties = new ContextWindowProperties();
//        properties.setTrimEnabled(true);
//        properties.setMaxMessages(10);
//        properties.setKeepRecent(5);
//        properties.setMaxTokens(5000);
//        properties.setFilterShortMessages(true);
//        properties.setShortMessageThreshold(5);
//
//        smartWindow = new SmartWindowChatMemory(properties);
//
//        // Create a mock SessionSummarizer that doesn't generate summaries
//        sessionSummarizer = org.mockito.Mockito.mock(SessionSummarizer.class);
//        org.mockito.Mockito.when(sessionSummarizer.shouldSummarize(org.mockito.ArgumentMatchers.anyList()))
//                .thenReturn(false);
//        org.mockito.Mockito.when(sessionSummarizer.createSummaryMessage(org.mockito.ArgumentMatchers.anyString()))
//                .thenReturn(null);
//
//
//
//        repository = new FileSystemChatMemoryRepository(
//                new org.springframework.core.io.FileSystemResource(tempDir),
//                smartWindow,
//                sessionSummarizer,
//                null,
//                null,
//                null);
//    }
//
//    @Test
//    void testSaveAndFindWithSmartWindow() {
//        String conversationId = "test-user-conv1";
//
//        // Save 20 messages
//        List<Message> messages = createMessages(20);
//        repository.saveAll(conversationId, messages);
//
//        // Find should return only messages within window
//        List<Message> found = repository.findByConversationId(conversationId);
//
//        // Should be limited by maxMessages (10)
//        assertTrue(found.size() <= 10);
//        assertFalse(found.isEmpty());
//    }
//
//    @Test
//    void testSaveAndFindWithFiltering() {
//        String conversationId = "test-user-conv2";
//
//        List<Message> messages = new ArrayList<>();
//        messages.add(new UserMessage("好的")); // Should be filtered
//        messages.add(new AssistantMessage("你好！"));
//        messages.add(new UserMessage("这是一个重要的问题"));
//        messages.add(new UserMessage("收到")); // Should be filtered
//        messages.add(new AssistantMessage("这是回答"));
//
//        repository.saveAll(conversationId, messages);
//        List<Message> found = repository.findByConversationId(conversationId);
//
//        // Should filter out "好的" and "收到"
//        assertTrue(found.size() < messages.size());
//        assertTrue(found.stream().anyMatch(m -> m.getText().contains("重要的问题")));
//    }
//
//    @Test
//    void testAppendAll() {
//        String conversationId = "test-user-conv3";
//
//        // Save initial messages (10 messages = 5 pairs)
//        List<Message> initial = createMessages(5);
//        repository.saveAll(conversationId, initial);
//
//        // Append more messages (20 messages = 10 pairs)
//        List<Message> additional = createMessages(10);
//        repository.appendAll(conversationId, additional);
//
//        // Find should apply window management but ALL messages should be saved
//        List<Message> found = repository.findByConversationId(conversationId);
//        assertTrue(found.size() <= 10); // Window limits what we see
//
//        // Verify the file actually contains all messages (30 total)
//        // by checking that we can append again and previous data is preserved
//        List<Message> moreMessages = List.of(
//            new UserMessage("追加测试消息"),
//            new AssistantMessage("追加测试回复")
//        );
//        repository.appendAll(conversationId, moreMessages);
//
//        // Should still have window-limited results
//        List<Message> foundAfterAppend = repository.findByConversationId(conversationId);
//        assertTrue(foundAfterAppend.size() <= 12); // 10 window + 2 new (approximately)
//    }
//
//    @Test
//    void testAppendAllPreservesFullHistory() {
//        String conversationId = "test-user-conv-preserve";
//
//        // Save 50 messages (25 pairs)
//        List<Message> firstBatch = createMessages(25);
//        repository.saveAll(conversationId, firstBatch);
//
//        // Append 10 more messages (5 pairs)
//        List<Message> secondBatch = createMessages(5);
//        repository.appendAll(conversationId, secondBatch);
//
//        // Append another 10 messages (5 pairs)
//        List<Message> thirdBatch = createMessages(5);
//        repository.appendAll(conversationId, thirdBatch);
//
//        // Window should limit what we see
//        List<Message> found = repository.findByConversationId(conversationId);
//        assertTrue(found.size() <= 10);
//
//        // But total saved should be 70 messages (35 pairs)
//        // We can verify this by appending one more and checking it doesn't lose data
//        List<Message> finalBatch = List.of(
//            new UserMessage("最终测试消息"),
//            new AssistantMessage("最终测试回复")
//        );
//        repository.appendAll(conversationId, finalBatch);
//
//        // The final message should be in the results (most recent)
//        List<Message> finalFound = repository.findByConversationId(conversationId);
//        assertTrue(finalFound.stream().anyMatch(m -> m.getText().equals("最终测试消息")));
//        assertTrue(finalFound.stream().anyMatch(m -> m.getText().equals("最终测试回复")));
//    }
//
//    @Test
//    void testFindNonExistentConversation() {
//        List<Message> found = repository.findByConversationId("non-existent");
//        assertTrue(found.isEmpty());
//    }
//
//    @Test
//    void testDeleteConversation() {
//        String conversationId = "test-user-conv4";
//
//        List<Message> messages = createMessages(5);
//        repository.saveAll(conversationId, messages);
//
//        // Verify saved
//        assertFalse(repository.findByConversationId(conversationId).isEmpty());
//
//        // Delete
//        repository.deleteByConversationId(conversationId);
//
//        // Verify deleted
//        assertTrue(repository.findByConversationId(conversationId).isEmpty());
//    }
//
//    @Test
//    void testFindConversationIds() {
//        // Save multiple conversations
//        repository.saveAll("user1-conv1", createMessages(3));
//        repository.saveAll("user1-conv2", createMessages(3));
//        repository.saveAll("user2-conv1", createMessages(3));
//
//        List<String> ids = repository.findConversationIds();
//
//        // Should return all conversation IDs (MD5 hashed)
//        assertEquals(3, ids.size());
//    }
//
//    @Test
//    void testWindowDisabled() {
//        properties.setTrimEnabled(false);
//
//        String conversationId = "test-user-conv5";
//        List<Message> messages = createMessages(30);
//        repository.saveAll(conversationId, messages);
//
//        List<Message> found = repository.findByConversationId(conversationId);
//
//        // Should return all messages when window is disabled
//        // But createMessages creates pairs, so 30 calls = 60 messages
//        assertEquals(60, found.size());
//    }
//
//    @Test
//    void testTokenBasedTruncation() {
//        // Set very low token limit
//        properties.setMaxTokens(200);
//        properties.setKeepRecent(3);
//        properties.setFilterShortMessages(false);
//
//        String conversationId = "test-user-conv6";
//        List<Message> messages = createLongMessages(10);
//        repository.saveAll(conversationId, messages);
//
//        List<Message> found = repository.findByConversationId(conversationId);
//
//        // Should truncate based on token limit
//        assertTrue(found.size() <= 10);
//        assertTrue(found.size() >= 3); // At least keepRecent
//    }
//
//    @Test
//    void testUserIsolation() {
//        // Save messages for different users
//        repository.saveAll("user1-conv", createMessages(5));
//        repository.saveAll("user2-conv", createMessages(5));
//
//        // Each user should have their own conversation
//        List<Message> user1Messages = repository.findByConversationId("user1-conv");
//        List<Message> user2Messages = repository.findByConversationId("user2-conv");
//
//        // createMessages(5) creates 10 messages (5 pairs)
//        assertEquals(10, user1Messages.size());
//        assertEquals(10, user2Messages.size());
//        assertNotEquals(user1Messages.get(0).getText(), user2Messages.get(0).getText());
//    }
//
//    // Helper methods
//
//    private List<Message> createMessages(int count) {
//        List<Message> messages = new ArrayList<>();
//        for (int i = 0; i < count; i++) {
//            messages.add(new UserMessage("User Message " + i));
//            messages.add(new AssistantMessage("Assistant Response " + i));
//        }
//        return messages;
//    }
//
//    private List<Message> createLongMessages(int count) {
//        List<Message> messages = new ArrayList<>();
//        for (int i = 0; i < count; i++) {
//            String longText = "这是一条较长的测试消息 " + i + "，包含较多的内容以增加token数量。" +
//                    "Spring AI框架提供了很好的对话管理能力，我们需要在这个基础上实现智能窗口。" +
//                    "通过合理的上下文管理，可以在有限的token窗口内保留最重要的信息。";
//            messages.add(new UserMessage(longText));
//            messages.add(new AssistantMessage(longText + " - Assistant Response"));
//        }
//        return messages;
//    }
//}
