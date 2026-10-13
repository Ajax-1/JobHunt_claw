package com.git.hui.jobclaw.agents.impl;

import com.git.hui.jobclaw.agents.OcAgentState;
import com.git.hui.jobclaw.gather.model.GatherTaskProcessBo;
import com.git.hui.jobclaw.gather.service.OfferGatherService;
import com.git.hui.jobclaw.web.model.res.GatherVo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/**
 * 任务采集Agent
 *
 * @date 2025/8/13
 */
@Slf4j
@Service
public class TaskGatherAgent extends BaseAgent {
    /**
     * 采集分类Agent
     */
    public static final String AGENT_NAME = "task_gather";

    @Autowired
    private OfferGatherService offerGatherService;

    @Override
    public String agentName() {
        return AGENT_NAME;
    }

    public Map<String, Object> apply(OcAgentState ocAgentState) {
        GatherTaskProcessBo task = ocAgentState.getTask();
        printAgentStartLog(task);

        // 执行任务采集
        GatherVo vo = offerGatherService.gatherInfo(task);

        // 返回采集结果
        Map<String, Object> map = new HashMap<>();
        map.put(OcAgentState.GATHER, vo);
        printAgentEndLog(vo);
        return map;
    }
}