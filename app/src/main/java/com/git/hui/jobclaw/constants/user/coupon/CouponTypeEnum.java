package com.git.hui.jobclaw.constants.user.coupon;

import com.git.hui.jobclaw.core.utils.json.IntBaseEnum;
import lombok.Getter;

/**
 * 优惠券类型
 *
 * @date 2025/8/20
 */
@Getter
public enum CouponTypeEnum implements IntBaseEnum {
    SUB_AMOUNT(0, "减金额"),
    DISCOUNT(1, "折扣"),
    ;
    private final Integer value;
    private final String desc;

    private CouponTypeEnum(Integer value, String desc) {
        this.value = value;
        this.desc = desc;
    }
}
