package com.git.hui.jobclaw.channels;

import com.git.hui.jobclaw.core.apis.context.ReqInfoContext;
import com.git.hui.jobclaw.core.apis.context.UserRoleEnum;
import com.git.hui.jobclaw.core.apis.permission.Permission;
import com.git.hui.jobclaw.core.channel.ChannelBinder;
import com.git.hui.jobclaw.core.channel.ChannelConfig;
import com.git.hui.jobclaw.core.configuration.ConfigurationManager;
import com.git.hui.jobclaw.core.utils.SensitiveUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 钉钉机器人渠道绑定
 * @date 2026/4/15
 */
@Slf4j
@Permission(role = UserRoleEnum.NORMAL)
@RestController
@RequestMapping("/api/dingding")
public class DingDingBotBinder implements ChannelBinder {
    private final DingDingBotProperties dingDingBotProperties;
    private final DingDingBotChannel dingDingBotChannel;
    private final ConfigurationManager configurationManager;

    public DingDingBotBinder(DingDingBotProperties dingDingBotProperties, DingDingBotChannel dingDingBotChannel, ConfigurationManager configurationManager) {
        this.dingDingBotProperties = dingDingBotProperties;
        this.dingDingBotChannel = dingDingBotChannel;
        this.configurationManager = configurationManager;
        Thread.ofVirtual().start(this::registerAccountChangeCallback);
    }

    private void registerAccountChangeCallback() {
        configurationManager.registerCallback(dingDingBotProperties, () -> {
            dingDingBotChannel.activeChannelAccounts();
        });
    }

    @Override
    public Map<Long, List<ChannelConfig>> getAllAccounts() {
        var map = dingDingBotProperties.getAccounts();
        Map<Long, List<ChannelConfig>> res = new HashMap<>();
        map.forEach((key, value) -> {
            List<ChannelConfig> list = new ArrayList<>(value);
            res.put(Long.valueOf(key), list);
        });
        return res;
    }

    private List<DingDingBotProperties.DingDingBotAccount> getMyConfig() {
        Long userId = ReqInfoContext.getReqInfo().getUserId();
        var map = dingDingBotProperties.getAccounts();
        return map.getOrDefault(String.valueOf(userId), new ArrayList<>());
    }

    /**
     * 获取当前用户绑定的钉钉机器人列表
     */
    @GetMapping("/list")
    public List<DingDingBotProperties.DingDingBotAccount> list() {
        var userAccounts = getMyConfig();
        // 返回时隐藏敏感信息
        return userAccounts.stream().peek(config -> config.setAppSecret(SensitiveUtil.securityReturn(config.getAppSecret()))).toList();
    }

    /**
     * 绑定钉钉机器人
     */
    @PostMapping("/bind")
    public boolean bind(@RequestBody DingDingBotProperties.DingDingBotAccount config) {
        Long userId = ReqInfoContext.getReqInfo().getUserId();
        config.setOwnerJobClawUserId(String.valueOf(userId));
        config.setState(ChannelConfig.ChannelState.NORMAL);
        config.setMode(ChannelConfig.ConnectionMode.WEBSOCKET);
        // 需要保存用户的机器人找号
        var exists = getMyConfig();
        int targetIndex = -1;
        for (int i = 0, existsSize = exists.size(); i < existsSize; i++) {
            ChannelConfig exist = exists.get(i);
            if (exist.getAppId().equals(config.getAppId())) {
                // 存在则更新
                targetIndex = i;
                break;
            }
        }
        if (targetIndex < 0) {
            // 不存在，表示新增
            targetIndex = exists.size();
        }

        String prefix = "agent.channels.dingding.accounts." + userId + "[" + targetIndex + "].";
        Map<String, Object> keyValues = new HashMap<>();
        keyValues.put(prefix + "app-id", config.getAppId());
        if (!config.getAppSecret().contains("***")) {
            keyValues.put(prefix + "app-secret", config.getAppSecret());
        } else {
            config.setAppSecret(configurationManager.getProperty(prefix + "app-secret"));
        }
        keyValues.put(prefix + "mode", config.getMode().name());
        keyValues.put(prefix + "state", config.getState().name());
        keyValues.put(prefix + "scope", config.getScope().name());
        keyValues.put(prefix + "ai-card-id", config.getAiCardId());
        keyValues.put(prefix + "bot-name", config.getBotName());
        configurationManager.updateProperties(keyValues);

        bindAccount(userId, config);
        return true;
    }

    @Override
    public void bindAccount(Long userId, ChannelConfig channelInfo) {
        dingDingBotChannel.addAccount(channelInfo);
    }
}
