package com.git.hui.jobclaw.channels.sdk;

import com.git.hui.jobclaw.core.channel.ChannelReceiveMessage;

/**
 *
 * @date 2026/4/22
 */
public record DingDingMsgContent(String content, ChannelReceiveMessage.MediaMsg media, ChannelReceiveMessage.FileMsg file) {
}
