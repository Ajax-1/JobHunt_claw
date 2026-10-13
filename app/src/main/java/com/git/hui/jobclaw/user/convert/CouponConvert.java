package com.git.hui.jobclaw.user.convert;

import com.git.hui.jobclaw.core.bizexception.BizException;
import com.git.hui.jobclaw.core.bizexception.StatusEnum;
import com.git.hui.jobclaw.constants.common.BaseStateEnum;
import com.git.hui.jobclaw.constants.user.coupon.CouponTypeEnum;
import com.git.hui.jobclaw.user.dao.entity.CouponEntity;
import com.git.hui.jobclaw.util.PriceUtil;
import com.git.hui.jobclaw.core.utils.json.JsonUtil;
import com.git.hui.jobclaw.web.model.req.CouponSaveReq;
import com.git.hui.jobclaw.web.model.res.CouponVo;
import org.springframework.util.CollectionUtils;

import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * 对象转换类
 *
 * @date 2025/8/20
 */
public class CouponConvert {

    public static CouponVo toVo(CouponEntity entity) {
        return new CouponVo(entity.getId(),
                entity.getCouponCode(),
                entity.getCouponType(),
                formatCouponValue(entity),
                entity.getCouponCount(),
                 entity.getScope(), entity.getStartTime().getTime(), entity.getEndTime().getTime(),
                entity.getExtra(), entity.getUseCount());
    }

    public static List<CouponVo> toVoList(List<CouponEntity> entityList) {
        if (entityList == null) {
            return null;
        }
        return entityList.stream().map(CouponConvert::toVo).toList();
    }

    public static String formatCouponValue(CouponEntity entity) {
        if (entity.getCouponType().equals(CouponTypeEnum.SUB_AMOUNT.getValue())) {
            // 金额减免
            return PriceUtil.toYuanPrice(entity.getCouponValue());
        } else {
            return PriceUtil.percent2Discount(entity.getCouponValue());
        }
    }

    public static void formatCouponValue(CouponEntity coupon, Integer couponType, String couponValue) {
        coupon.setCouponType(couponType);
        if (couponType.equals(CouponTypeEnum.SUB_AMOUNT.getValue())) {
            // 金额减免
            coupon.setCouponValue(PriceUtil.toCentPrice(couponValue));
        } else if (couponType.equals(CouponTypeEnum.DISCOUNT.getValue())) {
            coupon.setCouponValue(PriceUtil.discount2Percent(couponValue));
        } else {
            throw new BizException(StatusEnum.UNEXPECT_ERROR, "不支持的优惠券类型");
        }
    }

    public static CouponEntity toEntity(CouponSaveReq req) {
        CouponEntity couponEntity = new CouponEntity();
        couponEntity.setCouponCount(req.getCouponCount() == null ? 1 : req.getCouponCount());
        CouponConvert.formatCouponValue(couponEntity, req.getCouponType(), req.getCouponValue());
        couponEntity.setScope(req.getScope());
        couponEntity.setStartTime(new Date(req.getStartTime()));
        couponEntity.setEndTime(new Date(req.getEndTime()));
        couponEntity.setState(BaseStateEnum.NORMAL_STATE.getValue());
        couponEntity.setExtra(CollectionUtils.isEmpty(req.getUserIds()) ? "{}" : JsonUtil.toStr(Map.of("uids", req.getUserIds())));
        couponEntity.setUseCount(0);
        return couponEntity;
    }
}
