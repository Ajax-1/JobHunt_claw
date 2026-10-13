package com.git.hui.jobclaw.constants.user.coupon;

import com.git.hui.jobclaw.core.utils.json.IntBaseEnum;
import lombok.Getter;

/**
 * 优惠券状态
 *
 * @date 2025/8/20
 */
@Getter
public enum CouponStatusEnum implements IntBaseEnum {
    NOT_START(0, "未开始"),
    ING(1, "使用中"),
    EXPIRED(2, "已结束"),
    CANCEL(3, "已取消"),
    ;

    private final Integer value;
    private final String desc;

    CouponStatusEnum(Integer value, String desc) {
        this.value = value;
        this.desc = desc;
    }
}
