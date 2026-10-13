package com.git.hui.jobclaw.user.convert;

import com.git.hui.jobclaw.core.apis.context.UserBo;
import com.git.hui.jobclaw.user.dao.entity.RechargeEntity;
import com.git.hui.jobclaw.util.PriceUtil;
import com.git.hui.jobclaw.web.model.res.CouponUseRecordVo;
import com.git.hui.jobclaw.web.model.res.RechargePayVo;
import com.git.hui.jobclaw.web.model.res.RechargeRecordVo;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * @date 2025/7/22
 */
public class RechargeConvert {

    public static RechargePayVo toVo(RechargeEntity entity) {
        return new RechargePayVo(entity.getId(), entity.getTradeNo(), PriceUtil.toYuanPrice(entity.getAmount()),
                entity.getVipLevel()
                , entity.getPrePayId()
                , entity.getPrePayExpireTime() == null ? 0 : entity.getPrePayExpireTime().getTime());
    }

    public static RechargeRecordVo toRecord(RechargeEntity entity) {
        return new RechargeRecordVo(entity.getId()
                , entity.getTradeNo()
                , PriceUtil.toYuanPrice(entity.getAmount())
                , entity.getVipLevel()
                , entity.getStatus()
                , entity.getPayCallbackTime() == null ? entity.getUpdateTime().getTime() : entity.getPayCallbackTime().getTime()
                , entity.getThirdTransCode()
                , entity.getCouponCode()
                , PriceUtil.toYuanPrice(entity.getPromotionAmount())
        );
    }

    public static List<RechargeRecordVo> toRecordList(List<RechargeEntity> list) {
        if (list.isEmpty()) {
            return List.of();
        }
        return list.stream().map(RechargeConvert::toRecord).collect(Collectors.toList());
    }

    public static CouponUseRecordVo toCouponUseRecord(RechargeEntity entity, UserBo user) {
        return new CouponUseRecordVo(
                entity.getCouponCode()
                , entity.getId()
                , entity.getTradeNo()
                , PriceUtil.toYuanPrice(entity.getAmount())
                , entity.getVipLevel()
                , entity.getStatus()
                , entity.getPayCallbackTime() == null ? entity.getUpdateTime().getTime() : entity.getPayCallbackTime().getTime()
                , entity.getThirdTransCode()
                , PriceUtil.toYuanPrice(entity.getPromotionAmount())
                , user
        );
    }


    public static List<CouponUseRecordVo> toCouponUseRecordList(List<RechargeEntity> list, List<UserBo> users) {
        if (list.isEmpty()) {
            return List.of();
        }

        Map<Long, UserBo> userMap = users.stream().collect(Collectors.toMap(UserBo::userId, s -> s));
        return list.stream().map(item -> toCouponUseRecord(item, userMap.get(item.getUserId()))).collect(Collectors.toList());
    }
}
