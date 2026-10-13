package com.git.hui.jobclaw.web.controller.admin;

import com.git.hui.jobclaw.core.bizexception.BizException;
import com.git.hui.jobclaw.core.bizexception.StatusEnum;
import com.git.hui.jobclaw.core.apis.permission.Permission;
import com.git.hui.jobclaw.core.apis.context.UserRoleEnum;
import com.git.hui.jobclaw.user.service.CouponService;
import com.git.hui.jobclaw.user.service.RechargeService;
import com.git.hui.jobclaw.core.apis.PageListVo;
import com.git.hui.jobclaw.web.model.req.CouponSaveReq;
import com.git.hui.jobclaw.web.model.req.CouponSearchReq;
import com.git.hui.jobclaw.web.model.res.CouponUseRecordVo;
import com.git.hui.jobclaw.web.model.res.CouponVo;
import io.micrometer.common.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 后台用户管理
 *
 * @date 2025/7/17
 */
@Permission(role = UserRoleEnum.ADMIN)
@RestController
@RequestMapping(path = "/api/admin/coupon")
public class AdminCouponController {
    private final CouponService couponService;

    private final RechargeService rechargeService;

    public AdminCouponController(CouponService couponService, RechargeService rechargeService) {
        this.couponService = couponService;
        this.rechargeService = rechargeService;
    }

    @RequestMapping(path = "list")
    public PageListVo<CouponVo> list(CouponSearchReq req) {
        if (StringUtils.isBlank(req.getCouponCode())) {
            req.setCouponCode(null);
        }
        req.autoInitPage();
        return couponService.searchList(req);
    }

    /**
     * 新建优惠券
     *
     * @param req
     * @return
     */
    @PostMapping(path = "create")
    public boolean createCoupon(@RequestBody CouponSaveReq req) {
        if (req.getStartTime() == null) {
            req.setStartTime(System.currentTimeMillis());
        }
        if (req.getEndTime() == null) {
            // 默认一年的有效期
            req.setEndTime(System.currentTimeMillis() + 1000 * 60 * 60 * 24 * 365L);
        }

        if (req.getEndTime() < req.getStartTime()) {
            throw new BizException(StatusEnum.ILLEGAL_ARGUMENTS_MIXED, "结束时间早于开始时间");
        }

        return couponService.insertCoupon(req);
    }

    /**
     * 修改优惠券
     *
     * @param req
     * @return
     */
    @PostMapping(path = "update")
    public boolean updateCoupon(@RequestBody CouponSaveReq req) {
        if (req.getCouponId() == null) {
            throw new BizException(StatusEnum.UNEXPECT_ERROR, "请选择要修改的优惠券");
        }
        return couponService.updateCoupon(req);
    }

    @GetMapping(path = "delete")
    public boolean deleteCoupon(Long couponId) {
        if (couponId == null) {
            throw new BizException(StatusEnum.RECORDS_NOT_EXISTS, "请选择要删除的优惠券");
        }
        return couponService.deleteCoupon(couponId);
    }

    /**
     * 优惠券使用详情
     *
     * @param req
     */
    @GetMapping(path = "useDetail")
    public PageListVo<CouponUseRecordVo> couponUseDetail(CouponSearchReq req) {
        if (StringUtils.isBlank(req.getCouponCode())) {
            throw new BizException(StatusEnum.ILLEGAL_ARGUMENTS_MIXED, "请选择要查看的优惠券");
        }
        req.autoInitPage();
        return rechargeService.listCouponUseRecords(req);
    }
}
