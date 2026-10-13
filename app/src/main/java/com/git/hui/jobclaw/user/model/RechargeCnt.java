package com.git.hui.jobclaw.user.model;

/**
 * 优惠券使用的统计信息
 *
 * @param couponCode
 * @param status
 * @param cnt
 */
public record RechargeCnt(String couponCode, Integer status, Long cnt) {
}