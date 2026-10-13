package com.git.hui.jobclaw.core.agent.llm;

import com.git.hui.jobclaw.core.channel.ChannelReceiveMessage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import static org.assertj.core.api.Assertions.*;

// AI-GENERATED AIDEV-NOTE: 文本附件不能误走视觉模型，超限文件必须明确失败。
class TextAttachmentTest {
    @TempDir Path dir;

    private ChannelReceiveMessage message(Path path) {
        return ChannelReceiveMessage.builder().message("读取附件")
                .files(List.of(ChannelReceiveMessage.FileMsg.builder().filePath(path)
                        .mimeType("application/octet-stream").build())).build();
    }

    @Test void textStaysInPromptWithNewlines() throws Exception {
        var file = dir.resolve("demo.txt");
        Files.writeString(file, "演示校验码\nJOBCLAW-20260917");
        var prompt = new BizAgentLlmCaller(null, null, null, "").buildUserMessage(message(file));
        assertThat(prompt.getText()).contains("读取附件", "演示校验码\nJOBCLAW-20260917");
        assertThat(prompt.getMedia()).isEmpty();
    }

    @Test void oversizedTextIsRejected() throws Exception {
        var file = dir.resolve("large.txt");
        Files.write(file, new byte[1024 * 1024 + 1]);
        assertThatThrownBy(() -> new BizAgentLlmCaller(null, null, null, "").buildUserMessage(message(file)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("1 MB");
    }
}
