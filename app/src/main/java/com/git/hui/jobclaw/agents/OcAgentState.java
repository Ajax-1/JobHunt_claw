package com.git.hui.jobclaw.agents;

import com.git.hui.jobclaw.agents.impl.DraftWasherAgent;
import com.git.hui.jobclaw.agents.serializer.JsonSerializer;
import com.git.hui.jobclaw.gather.dao.entity.GatherTaskEntity;
import com.git.hui.jobclaw.gather.model.GatherTaskProcessBo;
import com.git.hui.jobclaw.oc.dao.entity.OcInfoEntity;
import com.git.hui.jobclaw.web.model.res.GatherVo;
import org.bsc.langgraph4j.serializer.StateSerializer;
import org.bsc.langgraph4j.serializer.std.ObjectStreamStateSerializer;
import org.bsc.langgraph4j.spring.ai.serializer.std.MessageSerializer;
import org.bsc.langgraph4j.state.AgentState;
import org.springframework.ai.chat.messages.Message;

import java.util.List;
import java.util.Map;

/**
 * @date 2025/8/13
 */
public class OcAgentState extends AgentState {

    /**
     * JobClaw智能体的外部输入，对象格式为 GatherTaskEntity
     */
    public static final String INPUT = "input";

    /**
     * TaskCliassifyAgent执行后的输出，对象格式为 GatherTaskProcessBo， 作为后续任务提取Agent的传参
     */
    public static final String TASK = "task";

    /**
     * TaskGatherAgent执行后的输出，对象格式为 GatherVo，内部记录的是提取后保存到草稿表中数据id（区分插入和更新数据）
     */
    public static final String GATHER = "gather";

    /**
     * TaskWasherAgent执行后的输出，对象格式为 DraftWasherAgent.WasherRecords，内部记录的是完成清洗的草稿数据主键
     */
    public static final String WASHER = "washer";

    /**
     * TaskPublishAgent执行后的输出，对象格式为 List<OcInfoEntity>，内部记录的是发布成功数据
     */
    public static final String PUBLISH = "publish";

    public OcAgentState(Map<String, Object> initData) {
        super(initData);
    }

    public GatherTaskEntity getInput() {
        return (GatherTaskEntity) value(INPUT).orElse(new GatherTaskEntity());
    }

    public GatherTaskProcessBo getTask() {
        return (GatherTaskProcessBo) value(TASK).orElse(null);
    }

    public GatherVo getGather() {
        return (GatherVo) value(GATHER).orElse(null);
    }

    public DraftWasherAgent.WasherRecords getWasherRecords() {
        return (DraftWasherAgent.WasherRecords) value(WASHER).orElse(new DraftWasherAgent.WasherRecords(List.of()));
    }

    public List<OcInfoEntity> getPublishRecords() {
        return (List<OcInfoEntity>) value(PUBLISH).orElse(List.of());
    }

    /**
     * 提供序列化方式，默认使用ObjectStreamStateSerializer，无法有效支持Java POJO类的序列化
     *
     * @return An instance of `StateSerializer` for serializing and deserializing `State` objects.
     */
    public static StateSerializer<OcAgentState> serializer() {
        var serializer = new ObjectStreamStateSerializer<>(OcAgentState::new);
        serializer.mapper().register(Message.class, new MessageSerializer());
        serializer.mapper().register(GatherTaskEntity.class, new JsonSerializer<>(GatherTaskEntity.class));
        serializer.mapper().register(GatherTaskProcessBo.class, new JsonSerializer<>(GatherTaskProcessBo.class));
        serializer.mapper().register(GatherVo.class, new JsonSerializer<>(GatherVo.class));
        serializer.mapper().register(DraftWasherAgent.WasherRecords.class, new JsonSerializer<>(DraftWasherAgent.WasherRecords.class));
        serializer.mapper().register(OcInfoEntity.class, new JsonSerializer<>(OcInfoEntity.class));
        return serializer;
    }
}
