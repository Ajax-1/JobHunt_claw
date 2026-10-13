package com.git.hui.jobclaw.channels;

import com.git.hui.jobclaw.channels.sdk.WeixinSdk;
import com.git.hui.jobclaw.channels.sdk.WeixinTypes;
import com.git.hui.jobclaw.core.apis.context.ReqInfoContext;
import com.git.hui.jobclaw.core.apis.context.UserRoleEnum;
import com.git.hui.jobclaw.core.apis.permission.Permission;
import com.git.hui.jobclaw.core.bus.ChannelEventPublisher;
import com.git.hui.jobclaw.core.channel.ChannelBinder;
import com.git.hui.jobclaw.core.channel.ChannelConfig;
import com.git.hui.jobclaw.core.configuration.ConfigurationManager;
import io.swagger.v3.oas.annotations.Operation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 用户扫码绑定微信ClawBot
 *
 * @date 2026/4/8
 */
@Slf4j
@Permission(role = UserRoleEnum.NORMAL)
@RestController
@RequestMapping("/api/wechat/clawbot")
public class WeChatClawBotBinder implements ChannelBinder {
    private static final String WX_CLAW_BOT_PREFIX = "agent.channels.wechat.clawbot";
    private static final String WX_CLAW_BOT_ACCOUNT_PREFIX = WX_CLAW_BOT_PREFIX + ".accounts";

    private final ConfigurationManager configurationManager;
    private final WxChatClawBotProperties wxChatClawBotProperties;
    private final WeixinSdk weixinSdk;

    private final ChannelEventPublisher channelEventPublisher;

    @Autowired(required = false)
    private WeChatClawBotChannel weChatClawBotChannel;

    public WeChatClawBotBinder(ConfigurationManager configurationManager, WxChatClawBotProperties wxChatClawBotProperties, ChannelEventPublisher channelEventPublisher) {
        this.configurationManager = configurationManager;
        this.wxChatClawBotProperties = wxChatClawBotProperties;
        this.channelEventPublisher = channelEventPublisher;

        // 初始化 WeixinSdk (用于生成二维码和检查状态)
        this.weixinSdk = new WeixinSdk.Builder()
                .baseUrl(this.wxChatClawBotProperties.getBaseUrl())
                .cdnBaseUrl(this.wxChatClawBotProperties.getCdnBaseUrl())
                .token("")
                .channelVersion("1.0.3")
                .loginBuild();

        this.configurationManager.registerCallback(wxChatClawBotProperties, new Runnable() {
            @Override
            public void run() {
                if (weChatClawBotChannel != null) {
                    // 账号配置发生变更，重新加载所有的账号
                    log.info("微信ClawBot账号变更，重新加载~");
                    weChatClawBotChannel.loadAllAccounts();
                }
            }
        });
    }

    /**
     * 生成绑定二维码
     */
    /**
     * 生成绑定二维码
     */
    @Operation(summary = "生成绑定二维码")
    @PostMapping("/qrcode")
    public BindQrInfo genBindQrCode() {
        try {
            WeixinTypes.BindQrCodeResp resp = weixinSdk.generateBindQrCode();

            if (resp.getQrcode() == null || resp.getQrcode().isBlank()) {
                log.error("Failed to get QR code: {}", resp.getErrmsg());
                return new BindQrInfo(false, "Failed to get QR code: " + resp.getErrmsg(), null, null);
            }

            log.info("QR code generated successfully");
            return new BindQrInfo(true, "success", resp.getQrcode(), resp.getQrcodeImgContent());
        } catch (Exception e) {
            log.error("Failed to get QR code", e);
            return new BindQrInfo(false, "Error: " + e.getMessage(), null, null);
        }
    }

    /**
     * 检查登录状态
     */
    @Operation(summary = "检查登录状态")
    @GetMapping("/status")
    public LoginStatus checkLoginStatus(@RequestParam String qrCode) {
        if (qrCode == null || qrCode.isBlank()) {
            return new LoginStatus(false, "No QR code available", null);
        }

        long userId = ReqInfoContext.getReqInfo().getUserId();
        try {
            WeixinTypes.QrCodeStatusResp resp = weixinSdk.checkQrCodeStatus(qrCode);
            String status = resp.getStatus();

            switch (status) {
                case "wait":
                    return new LoginStatus(false, "Waiting for scan", null);

                case "scaned":
                    return new LoginStatus(false, "Scanned, but not logged in", null);

                case "expired":
                    return new LoginStatus(false, "QR code expired", null);

                case "confirmed":
                    String botToken = resp.getBotToken();
                    String botId = resp.getIlinkBotId();
                    String ilinkUserId = resp.getIlinkUserId();
                    log.info("WeChat login successful! botToken:{} BotID: {}, UserID: {}",
                            botToken,
                            botId,
                            ilinkUserId);
                    var account = WxClawBotAccount.builder().userId(ilinkUserId).appId(botId).appSecret(
                            botToken).build();
                    this.bindAccount(userId, account);
                    return new LoginStatus(true, botId, ilinkUserId);

                default:
                    return new LoginStatus(false, "Unknown status: " + status, null);
            }

        } catch (Exception e) {
            log.error("Failed to check login status", e);
            return new LoginStatus(false, e.getMessage(), null);
        }
    }

    /**
     * 获取当前用户绑定的微信机器人信息
     */
    @Operation(summary = "获取绑定信息")
    @GetMapping("/list")
    public List<WxClawBotAccount> getMyBindInfo() {
        Long userId = ReqInfoContext.getReqInfo().getUserId();
        var accounts = wxChatClawBotProperties.getAccounts();
        if (accounts == null) {
            return List.of();
        }
        var userAccounts = accounts.get(String.valueOf(userId));
        if (userAccounts == null || userAccounts.getState() != ChannelConfig.ChannelState.NORMAL || userAccounts.getAppSecret() == null
                || userAccounts.getAppSecret().isBlank()) {
            return List.of();
        }
        return List.of(userAccounts);
    }

    @Operation(summary = "手动绑定微信 ClawBot 账号")
    @PostMapping("/bind")
    public boolean bindManual(@RequestBody ManualBindReq req) {
        if (req == null || req.wxClawbotUserId() == null || req.wxClawbotUserId().isBlank() || req.appId() == null
                || req.appId().isBlank() || req.appSecret() == null || req.appSecret().isBlank()) {
            throw new IllegalArgumentException("微信 ClawBot 账号信息不完整");
        }

        Long userId = ReqInfoContext.getReqInfo().getUserId();
        var account = WxClawBotAccount.builder()
                .userId(req.wxClawbotUserId())
                .appId(req.appId())
                .appSecret(req.appSecret())
                .mode(ChannelConfig.ConnectionMode.LOOP)
                .state(ChannelConfig.ChannelState.NORMAL)
                .build();
        this.bindAccount(userId, account);
        return true;
    }

    @Operation(summary = "解绑微信 ClawBot 账号")
    @PostMapping("/unbind")
    public boolean unbindManual(@RequestBody ManualUnbindReq req) {
        Long userId = ReqInfoContext.getReqInfo().getUserId();
        Map<String, Object> obj = new HashMap<>();
        obj.put(WX_CLAW_BOT_ACCOUNT_PREFIX + "." + userId + ".app-secret", "");
        obj.put(WX_CLAW_BOT_ACCOUNT_PREFIX + "." + userId + ".state", ChannelConfig.ChannelState.ERROR.name());
        configurationManager.updateProperties(obj);

        var accounts = wxChatClawBotProperties.getAccounts();
        if (accounts != null) {
            var account = accounts.get(String.valueOf(userId));
            if (account != null) {
                account.setAppSecret("");
                account.setState(ChannelConfig.ChannelState.ERROR);
            }
        }
        return true;
    }

    @Override
    public Map<Long, List<ChannelConfig>> getAllAccounts() {
        Map<String, WxClawBotAccount> map = wxChatClawBotProperties.getAccounts();
        Map<Long, List<ChannelConfig>> res = new HashMap<>();
        map.forEach((key, value) -> res.put(Long.valueOf(key), List.of(value)));
        return res;
    }

    @Override
    public void bindAccount(Long userId, ChannelConfig channelInfo) {
        WxClawBotAccount account = (WxClawBotAccount) channelInfo;
        account.setOwnerJobClawUserId(String.valueOf(userId));
        // 只要有一个账号，就设置为这个通道启用
        Map<String, Object> obj = new HashMap<>();
        obj.put(WX_CLAW_BOT_ACCOUNT_PREFIX + "." + userId + ".app-id", account.getAppId());
        obj.put(WX_CLAW_BOT_ACCOUNT_PREFIX + "." + userId + ".app-secret", account.getAppSecret());
        obj.put(WX_CLAW_BOT_ACCOUNT_PREFIX + "." + userId + ".user-id", account.getUserId());
        obj.put(WX_CLAW_BOT_ACCOUNT_PREFIX + "." + userId + ".mode", ChannelConfig.ConnectionMode.LOOP.name());
        obj.put(WX_CLAW_BOT_ACCOUNT_PREFIX + "." + userId + ".state", ChannelConfig.ChannelState.NORMAL.name());
        obj.put(WX_CLAW_BOT_PREFIX + ".enabled", true);
        configurationManager.updateProperties(obj);

        if (weChatClawBotChannel != null) {
            // 启用账号
            weChatClawBotChannel.addAccount(account);
            // 发送用户连接消息
            // fixme 是否为新用户的判断，应该判断之前是否已经录入相同的 ilinkUserId，而不是这里的直接写true
            channelEventPublisher.publishUserConnected(weChatClawBotChannel.name(),
                    account.getUserId(),
                    account,
                    true,
                    ReqInfoContext.getReqInfo().getClientIp());
        }
    }


    public record BindQrInfo(boolean success, String msg, String qrCode, String qrUrl) {
    }

    public record LoginStatus(boolean success, String msg, String accountId) {
    }

    public record ManualBindReq(String wxClawbotUserId, String appId, String appSecret) {
    }

    public record ManualUnbindReq(String userId) {
    }
}
