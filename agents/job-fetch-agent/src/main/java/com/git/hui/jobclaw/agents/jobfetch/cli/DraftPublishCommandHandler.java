package com.git.hui.jobclaw.agents.jobfetch.cli;

import com.git.hui.jobclaw.agents.jobfetch.service.JobInfoPersistService;
import com.git.hui.jobclaw.agents.jobfetch.service.model.FetchedDraftEntity;
import com.git.hui.jobclaw.core.agent.models.UserConversationInfo;
import com.git.hui.jobclaw.core.channel.ChannelReceiveMessage;
import com.git.hui.jobclaw.core.cli.SystemCommandHandler;
import com.git.hui.jobclaw.core.router.intent.PresetAgentIntro;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * 草稿发布命令处理器 (/publishDrafts)
 *
 * AIDEV-NOTE: 用于将审核通过的职位草稿发布到正式库
 *
 * @date 2026/4/21
 */
@Slf4j
@Component
public class DraftPublishCommandHandler implements SystemCommandHandler {

    private final JobInfoPersistService jobInfoPersistService;

    public DraftPublishCommandHandler(JobInfoPersistService jobInfoPersistService) {
        this.jobInfoPersistService = jobInfoPersistService;
    }

    @Override
    public boolean handle(ChannelReceiveMessage msg,
                          UserConversationInfo conversationInfo,
                          String command,
                          Function<String, Boolean> process) {
        // 解析命令参数: /publishDrafts [id1,id2,...|all]
        String[] parts = command.split("\\s+", 2);

        // 情况1: /publishDrafts (无参数)
        if (parts.length == 1) {
            return process.apply(buildHelpMessage());
        }

        String param = parts[1].trim();

        // 情况2: /publishDrafts all (发布所有待审核草稿)
        if ("all".equalsIgnoreCase(param)) {
            return handlePublishAll(conversationInfo, process);
        }

        // 情况3: /publishDrafts <id1,id2,...> (发布指定ID的草稿)
        return handlePublishByIds(param, process);
    }

    /**
     * 处理发布所有草稿
     */
    private boolean handlePublishAll(UserConversationInfo conversationInfo,
                                     Function<String, Boolean> process) {
        try {
            // 先查询所有待发布的草稿
            List<FetchedDraftEntity> drafts = jobInfoPersistService.listToBePublished(100);
            
            if (drafts == null || drafts.isEmpty()) {
                return process.apply("✅ 暂无待发布的草稿\n\n💡 提示:\n• 当前没有需要审核的职位信息\n• 可以发送URL或文件来抓取新的职位");
            }

            // 提取所有ID
            List<Long> draftIds = new ArrayList<>();
            for (FetchedDraftEntity draft : drafts) {
                draftIds.add(draft.getId());
            }

            // 执行发布
            int publishedCount = jobInfoPersistService.publishDrafts(draftIds);

            return process.apply(buildPublishResultMessage(draftIds.size(), publishedCount, true));
        } catch (Exception e) {
            log.error("发布所有草稿失败", e);
            return process.apply("❌ 发布失败，请稍后重试");
        }
    }

    /**
     * 处理发布指定ID的草稿
     */
    private boolean handlePublishByIds(String idsStr,
                                       Function<String, Boolean> process) {
        try {
            // 解析ID列表
            String[] idStrings = idsStr.split(",");
            List<Long> draftIds = new ArrayList<>();
            
            for (String idStr : idStrings) {
                try {
                    draftIds.add(Long.parseLong(idStr.trim()));
                } catch (NumberFormatException e) {
                    return process.apply(String.format("❌ 无效的草稿ID: %s\n\n请使用数字ID，多个ID用逗号分隔，如: `/publishDrafts 1,2,3`", idStr.trim()));
                }
            }

            if (draftIds.isEmpty()) {
                return process.apply("❌ 未提供有效的草稿ID");
            }

            // 执行发布
            int publishedCount = jobInfoPersistService.publishDrafts(draftIds);

            return process.apply(buildPublishResultMessage(draftIds.size(), publishedCount, false));
        } catch (Exception e) {
            log.error("发布草稿失败: ids={}", idsStr, e);
            return process.apply("❌ 发布失败，请稍后重试");
        }
    }

    @Override
    public PresetAgentIntro getIntentType() {
        return PresetAgentIntro.DRAFT_PUBLISH;
    }

    @Override
    public String getDescription() {
        return "发布职位草稿到正式库";
    }

    /**
     * 构建发布结果消息
     */
    private String buildPublishResultMessage(int requestedCount, int publishedCount, boolean isAll) {
        StringBuilder sb = new StringBuilder();

        sb.append("✅ 发布成功\n\n");
        sb.append("📊 发布结果:\n");
        sb.append(String.format("  • 请求发布: %d 条\n", requestedCount));
        sb.append(String.format("  • 成功发布: %d 条\n\n", publishedCount));

        if (isAll) {
            sb.append("💡 提示:\n");
            sb.append("• 所有待审核的职位已发布到正式库\n");
            sb.append("• 所有用户现在可以查看这些职位\n");
            sb.append("• 可以在管理后台进一步管理\n");
        } else {
            sb.append("💡 提示:\n");
            sb.append("• 选中的职位已发布到正式库\n");
            sb.append("• 所有用户现在可以查看这些职位\n");
            sb.append("• 如需发布其他草稿，请再次使用此命令\n");
        }

        return sb.toString();
    }

    /**
     * 构建帮助消息
     */
    private String buildHelpMessage() {
        return """
                📋 草稿发布命令
                                
                用法:
                • `/publishDrafts` - 显示此帮助信息
                • `/publishDrafts <ID1,ID2,...>` - 发布指定ID的草稿
                • `/publishDrafts all` - 发布所有待审核的草稿
                                
                示例:
                • `/publishDrafts 1,2,3` - 发布ID为1、2、3的草稿
                • `/publishDrafts all` - 发布所有待审核的草稿
                                
                💡 提示:
                • 发布前建议先使用工具查看待审核草稿
                • 发布后职位将对所有用户可见
                • 发布操作不可逆，请谨慎操作
                """;
    }
}
