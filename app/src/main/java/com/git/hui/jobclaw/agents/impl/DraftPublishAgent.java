package com.git.hui.jobclaw.agents.impl;

import com.git.hui.jobclaw.agents.OcAgentState;
import com.git.hui.jobclaw.oc.dao.entity.OcInfoEntity;
import com.git.hui.jobclaw.oc.service.GatherService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 草稿数据发布Agent
 *
 * @date 2025/8/13
 */
@Slf4j
@Service
public class DraftPublishAgent extends BaseAgent {
    public static final String AGENT_NAME = "draft_publish";
    @Autowired
    private GatherService gatherService;

    @Override
    public String agentName() {
        return AGENT_NAME;
    }

    public Map<String, Object> apply(OcAgentState ocAgentState) {
        DraftWasherAgent.WasherRecords vo = ocAgentState.getWasherRecords();
        printAgentStartLog(vo);

        List<OcInfoEntity> list = gatherService.moveToOc(vo.ids());
        Map<String, Object> map = new HashMap<>();
        map.put(OcAgentState.PUBLISH, list);
        printAgentEndLog(map);
        return map;
    }
}
