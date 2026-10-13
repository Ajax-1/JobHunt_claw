package com.git.hui.jobclaw.user.dao.entity;

import com.git.hui.jobclaw.constants.common.BaseStateEnum;
import com.git.hui.jobclaw.constants.user.RechargeLevelEnum;
import com.git.hui.jobclaw.constants.user.coupon.CouponTypeEnum;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.Data;
import lombok.experimental.Accessors;
import org.hibernate.annotations.DynamicUpdate;

import java.util.Date;

/**
 * 充值优惠券
 *
 * @date 2025/8/20
 */
@Data
@Accessors(chain = true)
// 动态更新字段
@DynamicUpdate
@Entity(name = "recharge_coupon")
public class CouponEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 券码
     */
    @Column(name = "coupon_code")
    private String couponCode;

    /**
     * 减免金额时 = 优惠金额(单位为分)
     * 折扣时 = 折扣比例(如 95 表示 95%)
     */
    @Column(name = "coupon_value")
    private Integer couponValue;

    /**
     * 优惠券数量
     */
    @Column(name = "coupon_count")
    private Integer couponCount;

    /**
     * 优惠券类型
     *
     * @see CouponTypeEnum#getValue()
     */
    @Column(name = "coupon_type")
    private Integer couponType;

    /**
     * 优惠券已使用数量
     */
    @Column(name = "use_count")
    private Integer useCount;

    /**
     * 优惠券作用范围
     * 999 无限制
     * 666 表示指定用户可用
     * 0-3 表示对应的会员等级
     *
     * @see RechargeLevelEnum#values()
     */
    @Column(name = "scope")
    private Integer scope;

    /**
     * 优惠券开始时间
     */
    @Column(name = "start_time")
    private Date startTime;

    /**
     * 优惠券结束时间
     */
    @Column(name = "end_time")
    private Date endTime;

    /**
     * 优惠券扩展说明
     */
    @Column(name = "extra")
    private String extra;

    /**
     * 状态:
     * -1 删除
     * 1 正常
     *
     * @see BaseStateEnum#getValue()
     */
    @Column(name = "state")
    private Integer state;

    /**
     * 微信支付创建订单回传的关键信息
     */
    @Column(name = "create_time")
    private Date createTime;


    @Column(name = "update_time")
    private Date updateTime;
}
