package com.git.hui.jobclaw.web.model.res;

import com.git.hui.jobclaw.core.apis.context.UserBo;

/**
 * @date 2025/8/20
 */
public record CouponUseRecordVo(
        String coupon
        , Long payId // 支付id
        , String tradeNo // 交易号
        , String amount// 充值金额
        , Integer level// 充值级别
        , Integer status // 充值状态
        , Long payTime// 支付成功时间
        , String transactionId // 三方交易号
        , String promotionAmount // 优惠金额
        , UserBo user // 用户信息
) {
}
