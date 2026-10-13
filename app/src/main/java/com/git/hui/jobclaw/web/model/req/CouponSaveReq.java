package com.git.hui.jobclaw.web.model.req;

import com.git.hui.jobclaw.constants.user.RechargeLevelEnum;
import com.git.hui.jobclaw.constants.user.coupon.CouponTypeEnum;
import lombok.Data;

import java.util.List;

/**
 * @date 2025/8/20
 */
@Data
public class CouponSaveReq {
    private Long couponId;

    /**
     * 减免金额时 = 优惠金额(单位为分)
     * 折扣时 = 折扣比例(如 95 表示 95%)
     */
    private String couponValue;

    /**
     * 优惠券数量
     */
    private Integer couponCount;

    /**
     * 优惠券类型
     *
     * @see CouponTypeEnum#getValue()
     */
    private Integer couponType;

    /**
     * 优惠券作用范围
     * 999 无限制
     * 666 表示指定用户可用
     * 0-3 表示对应的会员等级
     *
     * @see RechargeLevelEnum#values()
     */
    private Integer scope;

    /**
     * 优惠券开始时间(毫秒时间戳)
     */
    private Long startTime;

    /**
     * 优惠券结束时间(毫秒时间戳)
     */
    private Long endTime;

    /**
     * 优惠券指定用户
     */
    private List<Long> userIds;
}
