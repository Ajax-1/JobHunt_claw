package com.git.hui.jobclaw.constants.user;

import com.git.hui.jobclaw.core.utils.json.IntBaseEnum;
import lombok.Getter;

/**
 * 充值状态
 * @date 2025/7/21
 */
@Getter
public enum RechargeStatusEnum implements IntBaseEnum {
    NOT_PAY(0, "待支付"),
    PAYING(1, "支付中"),
    SUCCEED(2, "支付成功"),
    FAIL(3, "支付失败"),
    ;
    private Integer value;
    private String desc;

    RechargeStatusEnum(Integer value, String desc) {
        this.value = value;
        this.desc = desc;
    }
}
