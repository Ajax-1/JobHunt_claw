package com.git.hui.jobclaw.constants.user;

import com.git.hui.jobclaw.core.utils.json.StringBaseEnum;
import lombok.Getter;

/**
 * 微信公众号登录二维码类型
 *
 * @date 2025/9/28
 */
@Getter
public enum LoginQrTypeEnum implements StringBaseEnum {

    SUBSCRIPTION_ACCOUNT("Subscription Account", "微信公众号"),
    SERVICE_ACCOUNT("Service Account", "服务号"),
    ;
    private String value;
    private String desc;

    LoginQrTypeEnum(String code, String desc) {
        this.value = code;
        this.desc = desc;
    }
}
