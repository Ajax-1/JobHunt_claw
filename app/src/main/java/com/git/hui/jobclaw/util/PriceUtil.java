package com.git.hui.jobclaw.util;

import com.git.hui.jobclaw.core.bizexception.BizException;
import com.git.hui.jobclaw.core.bizexception.StatusEnum;
import com.git.hui.jobclaw.constants.user.coupon.CouponTypeEnum;
import org.apache.commons.lang3.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;

/**
 * @date 2024/12/04
 */
public class PriceUtil {

    /**
     * 价格转分
     *
     * @param price
     * @return
     */
    public static Integer toCentPrice(String price) {
        if (StringUtils.isBlank(price)) {
            return null;
        }

        BigDecimal ans = new BigDecimal(price).multiply(new BigDecimal("100.0")).setScale(0, RoundingMode.HALF_DOWN);
        return ans.intValue();
    }

    /**
     * 分的价格转元
     *
     * @param price
     * @return
     */
    public static String toYuanPrice(Integer price) {
        if (price == null) {
            return null;
        }
        DecimalFormat df1 = new DecimalFormat("0.00");
        String ans = df1.format(price / 100f);
        if (price % 100 == 0) {
            // 整元时，移除后面的小数
            return ans.substring(0, ans.length() - 3);
        } else if (price % 10 == 0) {
            return ans.substring(0, ans.length() - 1);
        }
        return ans;
    }

    /**
     * 折扣转百分比
     *
     * @param discount 10 表示10折，即100%
     * @return
     */
    public static Integer discount2Percent(String discount) {
        if (StringUtils.isBlank(discount)) {
            return null;
        }
        return new BigDecimal(discount).multiply(new BigDecimal("10.0")).setScale(0, RoundingMode.HALF_DOWN).intValue();
    }


    public static String percent2Discount(Integer discount) {
        if (discount == null) {
            return null;
        }
        DecimalFormat df1 = new DecimalFormat("0.0");
        return df1.format(discount / 10f);
    }

    /**
     * 判断是否为合法的价格
     *
     * @param price
     * @return ture 合法价格
     */
    public static boolean legalPrice(String price) {
        if (StringUtils.isBlank(price)) {
            return false;
        }

        Integer pp = toCentPrice(price);
        return pp != null && pp > 0;
    }

    /**
     * 计算优惠后的价格
     *
     * @param originalPrice
     * @param couponType
     * @param couponValue
     * @return
     */
    public static Integer calculatePriceAfterCoupon(Integer originalPrice, Integer couponType, Integer couponValue) {
        if (couponType.equals(CouponTypeEnum.SUB_AMOUNT.getValue())) {
            // 金额减免
            return Math.max(0, originalPrice - couponValue);
        } else if (couponType.equals(CouponTypeEnum.DISCOUNT.getValue())) {
            // 折扣
            return Math.max(0, originalPrice * couponValue / 100);
        } else {
            throw new BizException(StatusEnum.ILLEGAL_ARGUMENTS, "非法的优惠券类型");
        }
    }
}
