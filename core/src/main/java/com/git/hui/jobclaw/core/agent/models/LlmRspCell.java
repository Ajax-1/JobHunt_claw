package com.git.hui.jobclaw.core.agent.models;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;

import java.util.List;

/**
 * 大模型返回的基础信息
 * <p>
 * 流式调用中每个 chunk 经 {@link #of(ChatResponse)} 转换后推送给调用方：
 * - thinking: 模型的思考推理过程（reasoningContent）
 * - content: 模型的文本输出（增量片段）
 * - tool: 工具调用状态信息（名称 + 参数摘要）
 * - toolResult: 工具执行结果（由 ReActAdvisor 合成发射）
 * <p>
 * AIDEV-NOTE: 四者互斥，每个 cell 通常只填充其中一个字段
 *
 * @date 2026/4/16
 */
@Slf4j
public record LlmRspCell(String thinking, String content, String tool, String toolResult) {

    /**
     * 兼容旧构造（无 toolResult 字段）
     */
    public LlmRspCell(String thinking, String content, String tool) {
        this(thinking, content, tool, null);
    }

    /**
     * 从流式 ChatResponse chunk 中提取 thinking / content / tool / toolResult 信息
     * <p>
     * AIDEV-NOTE: toolResult 由 ReActAdvisor 在工具执行后合成为带 "toolResult" 元数据的
     * AssistantMessage 发射，此处通过检测元数据标记来识别。
     */
    public static LlmRspCell of(ChatResponse chunk) {
        var output = chunk.getResult().getOutput();

        // 0. 工具执行结果（ReActAdvisor 合成的 ChatResponse，metadata 中带有 toolResult 标记）
        var toolResultFlag = output.getMetadata().get("toolResult");
        if (Boolean.TRUE.equals(toolResultFlag)) {
            log.info("[LlmRspCell] Detected toolResult cell, textLen={}", output.getText() != null ? output.getText().length() : 0);
            return new LlmRspCell(null, null, null, normalizeNewlines(output.getText()));
        }

        // 1. 思考内容（部分模型通过 reasoningContent 元数据返回思考过程）
        var r = output.getMetadata().get("reasoningContent");
        String text = output.getText();

        // 2. 工具调用信息
        String toolInfo = extractToolInfo(output);

        if (log.isDebugEnabled()) {
            log.debug("[LlmRspCell] chunk: textLen={}, hasToolResult={}, hasReasoning={}, toolInfo={}, usage={}",
                    text != null ? text.length() : 0, toolResultFlag, r != null, toolInfo, chunk.getMetadata().getUsage());
        }

        if (log.isDebugEnabled()) {
            log.debug("[agent rsp] Reasoning: \nthink>>{} \ntext>>{} \ntool>>{}", r, text, toolInfo);
        }

        // 纯思考内容（无文本、无工具）
        // AI-GENERATED AIDEV-NOTE: 换行增量不能因为附带 reasoningContent 就被当作纯思考丢弃。
        if (StringUtils.isEmpty(text) && r != null && toolInfo == null) {
            return new LlmRspCell((String) r, null, null, null);
        }

        // 工具调用（可能同时有文本片段）
        if (toolInfo != null) {
            return new LlmRspCell(
                    r instanceof String s ? s : null,
                    normalizeNewlines(text),
                    toolInfo,
                    null);
        }

        // 普通文本输出
        text = normalizeNewlines(text);
        return new LlmRspCell(null, text, null, null);
    }

    /**
     * 从 AssistantMessage 中提取工具调用摘要
     * <p>
     * 流式 chunk 中若包含 tool_calls，则格式化为可读字符串；
     * 否则返回 null。
     */
    private static String extractToolInfo(AssistantMessage output) {
        List<AssistantMessage.ToolCall> toolCalls = output.getToolCalls();
        if (toolCalls == null || toolCalls.isEmpty()) {
            return null;
        }

        var sb = new StringBuilder();
        for (var tc : toolCalls) {
            if (sb.length() > 0) sb.append("; ");
            sb.append(tc.name());
            String args = tc.arguments();
            if (StringUtils.isNotBlank(args)) {
                // 参数摘要：截取前120字符
                String summary = args.length() > 120 ? args.substring(0, 120) + "..." : args;
                sb.append("(").append(summary).append(")");
            }
        }
        return sb.toString();
    }

    /**
     * 标准化换行符，将字面量 \n 转换为真正的换行符
     * 用于处理大模型返回或工具调用结果中可能存在的转义问题
     *
     * @param text 原始文本
     * @return 标准化后的文本
     */
    private static String normalizeNewlines(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        
        // 将字面量的 \n (两个字符: 反斜杠 + n) 替换为真正的换行符
        // 注意：这里使用 replaceAll 需要转义反斜杠
        return text.replace("\\n", "\n");
    }

}
