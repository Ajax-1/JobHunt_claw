package com.git.hui.jobclaw.web.model.res;

/**
 * @date 2025/8/20
 */
public record CouponVo(Long couponId,
                       // 优惠券code
                       String couponCode,
                       // 优惠券类型
                       Integer couponType,
                       // 优惠券金额
                       String couponValue,
                       // 优惠券数量
                       Integer couponCount,
                       // 作用域
                       Integer scope,
                       // 开始时间
                       Long startTime,
                       // 优惠券结束时间
                       Long endTime,
                       String extra,
                       // 使用数量
                       Integer useCount
) {
}
