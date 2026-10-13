package com.git.hui.jobclaw.agents.impl;

import com.git.hui.jobclaw.agents.OcAgentState;
import com.git.hui.jobclaw.constants.gather.GatherTargetTypeEnum;
import com.git.hui.jobclaw.gather.dao.entity.GatherTaskEntity;
import com.git.hui.jobclaw.gather.service.GatherTaskService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/**
 * 任务分类的agent
 *
 * @date 2025/8/13
 */
@Slf4j
@Service
public class TaskClassifyAgent extends BaseAgent {
    /**
     * 采集分类Agent
     */
    public static final String AGENT_NAME = "task_classify";

    @Autowired
    private GatherTaskService gatherTaskService;

    @Override
    public String agentName() {
        return AGENT_NAME;
    }

    public Map<String, Object> apply(OcAgentState ocAgentState) {
        GatherTaskEntity task = ocAgentState.getInput();
        printAgentStartLog(task);
        if (task.getType() != 0) {
            return buildResponse(task);
        }

        // 未指定传入数据类型的时候，需要根据输入信息进行自动判断
        if (task.getContent().startsWith("/oc")) {
            // 传入附件的场景
            if (task.getContent().endsWith("csv")) {
                task.setType(GatherTargetTypeEnum.CSV_FILE.getValue());
            } else if (task.getContent().endsWith("xls") || task.getContent().endsWith("xlsx")) {
                task.setType(GatherTargetTypeEnum.EXCEL_FILE.getValue());
            } else if (task.getContent().endsWith("png") || task.getContent().endsWith("jpg") || task.getContent().endsWith("jpeg")
                    || task.getContent().endsWith("gif") || task.getContent().endsWith("webp")) {
                task.setType(GatherTargetTypeEnum.IMAGE.getValue());
            }
        } else {
            // 文本类型
            if (task.getContent().contains("<div>") || task.getContent().contains("<td ")) {
                // html格式文本
                task.setType(GatherTargetTypeEnum.HTML_TEXT.getValue());
            } else {
                if (task.getContent().matches("^https?://.*$")) {
                    // 完整的http链接
                    task.setType(GatherTargetTypeEnum.HTTP_URL.getValue());
                } else {
                    // 如果文本中，只有一个http链接地址，则认为文本是http方式
                    int index = task.getContent().indexOf("http");
                    if (index >= 0) {
                        index = task.getContent().indexOf("http", index + 4);
                        if (index < 0) {
                            task.setType(GatherTargetTypeEnum.HTTP_URL.getValue());
                        } else {
                            task.setType(GatherTargetTypeEnum.TEXT.getValue());
                        }
                    } else {
                        task.setType(GatherTargetTypeEnum.TEXT.getValue());
                    }
                }
            }
        }

        return buildResponse(task);
    }

    private Map<String, Object> buildResponse(GatherTaskEntity task) {
        Map<String, Object> map = new HashMap<>();
        map.put(OcAgentState.TASK, gatherTaskService.markTaskProcessing(task));
        printAgentEndLog(map);
        return map;
    }
}