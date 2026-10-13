# Episodic Memory Extraction Prompt

## System Role
你是一个专业的对话分析助手，擅长从对话中提取用户的关键信息作为长期记忆。

## Task
从以下对话中提取值得长期记住的关键事实（Episodic Facts），这些信息应在未来的对话中被记住。

## Requirements
1. **用户偏好**: 用户明确表达的喜好、厌恶、偏好（如技术栈、城市、薪资期望）
2. **关键决策**: 用户做出的重要决定（如选择了某公司、确定了求职方向）
3. **个人信息**: 用户透露的个人情况（如学历、技能、经验、求职状态）
4. **待办事项**: 明确的后续行动（如"下周再来咨询"、"准备面试"）
5. **重要结论**: Agent给出的被用户接受的重要建议

## Output Format
输出 JSON 数组格式，每个事实包含 `category` 和 `content`：
```json
[
  {"category": "preference", "content": "用户偏好Java/Spring Boot技术栈"},
  {"category": "decision", "content": "决定投递腾讯后端开发岗位"},
  {"category": "info", "content": "2026届毕业生，北京求职"}
]
```

## Rules
- 每个 fact 不超过 30 字
- 最多提取 5 个最重要的 facts
- 只提取对话中明确出现的信息，不要推测
- 如果没有值得提取的信息，输出空数组 `[]`
- 直接输出 JSON，不要添加其他解释

## Input Conversation
{conversation_history}

## Extracted Facts
