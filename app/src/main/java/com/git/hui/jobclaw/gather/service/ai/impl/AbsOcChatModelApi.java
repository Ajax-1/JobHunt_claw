package com.git.hui.jobclaw.gather.service.ai.impl;

import com.git.hui.jobclaw.core.bizexception.BizException;
import com.git.hui.jobclaw.core.bizexception.StatusEnum;
import com.git.hui.jobclaw.constants.gather.GatherModelTypeEnum;
import com.git.hui.jobclaw.gather.model.ModelSelectReq;
import com.git.hui.jobclaw.gather.service.ai.OcChatModelApi;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.data.util.Pair;

/**
 * JobClaw大模型集成的统一接口
 *
 * @date 2025/7/30
 */
public abstract class AbsOcChatModelApi implements OcChatModelApi {
    /**
     * 返回文本聊天的大模型客户端
     *
     * @return
     */
    public abstract ChatClient chatClient();

    /**
     * 返回图文聊天的大模型客户端
     *
     * @return
     */
    public ChatClient imgClient() {
        throw new BizException(StatusEnum.MODEL_NOT_SUPPORT);
    }

    /**
     * 获取大模型
     *
     * @return
     */
    public abstract ChatModel chatModel();

    /**
     * 获取文本大模型名称
     *
     * @return
     */
    public abstract String chatModelName();

    /**
     * 获取图片理解大模型名称
     *
     * @return
     */
    public String imgModelName() {
        throw new BizException(StatusEnum.MODEL_NOT_SUPPORT);
    }


    /**
     * 根据传参获取对应de
     *
     * @param req
     * @return
     */
    public ChatClient chatClient(ModelSelectReq req) {
        if (req.mode() != modelEnum()) {
            return null;
        }
        return req.type() == GatherModelTypeEnum.IMAGE_MODEL ? imgClient() : chatClient();
    }

    /**
     * 获取模型
     *
     * @return left: 大模型  right: 模型名
     */
    public Pair<ChatModel, String> model(ModelSelectReq req) {
        if (req.mode() != modelEnum()) {
            return null;
        }
        String modelName = req.type() == GatherModelTypeEnum.IMAGE_MODEL ? imgModelName() : chatModelName();
        return Pair.of(chatModel(), modelName);
    }
}
