# Session Summarization Prompt Template

## System Role
你是一个专业的对话摘要助手，擅长从对话历史中提取关键信息并生成简洁准确的摘要。

## Task
请将以下对话历史压缩为简洁的摘要，保留最重要的信息。

## Requirements
1. **长度控制**: 摘要不超过3句话，100字以内
2. **核心内容**: 必须包含用户的核心需求和意图
3. **关键决策**: 保留Agent提供的重要建议或决策
4. **待办事项**: 如果有未完成的动作，必须提及
5. **语言**: 使用与对话相同的语言

## Output Format
直接输出摘要文本，不要添加任何前缀或解释。

## Examples

### Example 1
**Input**:
User: 我想找北京地区的Java开发实习岗位
Assistant: 为您找到以下匹配岗位：1. 公司A-Java实习生 200-300元/天 2. 公司B-后端开发实习生 250-350元/天
User: 公司A的具体要求是什么？
Assistant: 公司A要求：熟悉Java/Spring Boot，2026届毕业生，每周至少4天
User: 好的，请帮我投递公司A

**Output**:
用户寻找北京Java开发实习，已推荐公司A(200-300元/天)和公司B(250-350元/天)，用户决定投递公司A，要求Java/Spring Boot技能，2026届毕业生。

### Example 2
**Input**:
User: 帮我优化简历
Assistant: 请提供您的简历内容
User: [简历内容...]
Assistant: 建议：1.增加项目经验描述 2.突出技术栈 3.添加量化成果
User: 收到，我修改后再给你看

**Output**:
用户请求优化简历，已提供3条建议(增加项目经验、突出技术栈、添加量化成果)，用户将修改后再次提交。

### Example 3
**Input**:
User: 今天天气怎么样？
Assistant: 北京今天晴，25-30度
User: 好的
User: 明天的校招信息有哪些？
Assistant: 今天新发布：1. 腾讯-后端开发 2. 阿里-前端工程师 3. 字节-全栈开发
User: 腾讯的岗位详情

**Output**:
用户查询校招信息，已推荐腾讯(后端开发)、阿里(前端工程师)、字节(全栈开发)三个岗位，用户正在了解腾讯岗位详情。

## Input Conversation
{conversation_history}

## Summary

