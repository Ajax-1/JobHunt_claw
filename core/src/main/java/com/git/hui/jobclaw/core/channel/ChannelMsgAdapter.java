package com.git.hui.jobclaw.core.channel;

import lombok.Builder;
import lombok.Data;

/**
 *
 * @date 2026/4/13
 */
@FunctionalInterface
public interface ChannelMsgAdapter<T> {

    ChannelReceiveMessage adaptToReceive(MsgWrapper<T> msg);


    @Data
    @Builder(toBuilder = true)
    class MsgWrapper<T> {
        String jobClawUserId;
        T msg;
    }

}
