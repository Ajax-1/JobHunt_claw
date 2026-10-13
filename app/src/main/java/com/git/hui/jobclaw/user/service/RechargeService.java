package com.git.hui.jobclaw.user.service;

import com.git.hui.jobclaw.core.bizexception.BizException;
import com.git.hui.jobclaw.core.bizexception.StatusEnum;
import com.git.hui.jobclaw.core.apis.context.ReqInfoContext;
import com.git.hui.jobclaw.core.apis.context.UserBo;
import com.git.hui.jobclaw.core.utils.SpringUtil;
import com.git.hui.jobclaw.components.id.IdUtil;
import com.git.hui.jobclaw.configs.service.CommonDictService;
import com.git.hui.jobclaw.constants.user.RechargeConstants;
import com.git.hui.jobclaw.constants.user.RechargeLevelEnum;
import com.git.hui.jobclaw.constants.user.RechargeStatusEnum;
import com.git.hui.jobclaw.constants.user.ThirdPayWayEnum;
import com.git.hui.jobclaw.user.convert.RechargeConvert;
import com.git.hui.jobclaw.user.dao.entity.CouponEntity;
import com.git.hui.jobclaw.user.dao.entity.RechargeEntity;
import com.git.hui.jobclaw.user.dao.repository.RechargeRepository;
import com.git.hui.jobclaw.user.model.PayCallbackBo;
import com.git.hui.jobclaw.user.model.PrePayInfoResBo;
import com.git.hui.jobclaw.user.model.ThirdPayOrderReqBo;
import com.git.hui.jobclaw.user.service.pay.ThirdPayHandler;
import com.git.hui.jobclaw.util.PriceUtil;
import com.git.hui.jobclaw.core.utils.json.IntBaseEnum;
import com.git.hui.jobclaw.core.utils.json.JsonUtil;
import com.git.hui.jobclaw.core.apis.PageListVo;
import com.git.hui.jobclaw.web.model.req.CouponSearchReq;
import com.git.hui.jobclaw.web.model.res.CommonDictVo;
import com.git.hui.jobclaw.web.model.res.CouponUseRecordVo;
import com.git.hui.jobclaw.web.model.res.DictItemVo;
import com.git.hui.jobclaw.web.model.res.RechargePayVo;
import com.git.hui.jobclaw.web.model.res.RechargeRecordVo;
import io.micrometer.common.util.StringUtils;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;

import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

/**
 * 充值服务
 *
 * @date 2025/7/21
 */
@Slf4j
@Service
public class RechargeService {

    private final RechargeRepository rechargeRepository;

    private final ThirdPayHandler thirdPayHandler;

    private final UserService userService;

    private final CommonDictService commonDictService;

    private final CouponService couponService;

    public RechargeService(RechargeRepository rechargeRepository, ThirdPayHandler thirdPayHandler, UserService userService, CommonDictService commonDictService, CouponService couponService) {
        this.rechargeRepository = rechargeRepository;
        this.thirdPayHandler = thirdPayHandler;
        this.userService = userService;
        this.commonDictService = commonDictService;
        this.couponService = couponService;
    }

    /**
     * 查询成功、失败，支付中的充值记录
     * <p>
     * 一个人的充值记录有限，这里先不实现具体的翻页
     *
     * @return
     */
    public PageListVo<RechargeRecordVo> listRechargeRecords() {
        Long userId = ReqInfoContext.getReqInfo().getUserId();
        List<RechargeEntity> list = rechargeRepository.findByUserIdAndStatusInOrderByIdDesc(userId,
                List.of(RechargeStatusEnum.NOT_PAY.getValue(),
                        RechargeStatusEnum.SUCCEED.getValue(),
                        RechargeStatusEnum.FAIL.getValue(),
                        RechargeStatusEnum.PAYING.getValue()));

        List<RechargeRecordVo> voList = RechargeConvert.toRecordList(list);
        return PageListVo.of(voList, list.size(), 1, list.size());
    }

    /**
     * 查询优惠券使用记录
     *
     * @param req
     * @return
     */
    public PageListVo<CouponUseRecordVo> listCouponUseRecords(CouponSearchReq req) {
        PageListVo<RechargeEntity> list = rechargeRepository.findList(req);
        if (list.getTotal() <= 0L) {
            return PageListVo.emptyVo();
        }

        // 补齐用户信息
        List<Long> userIdList = list.getList().stream().map(RechargeEntity::getUserId).toList();
        List<UserBo> users = userService.getUserByUserIds(userIdList);
        List<CouponUseRecordVo> voList = RechargeConvert.toCouponUseRecordList(list.getList(), users);
        return PageListVo.of(voList, list.getTotal(), req.getPage(), req.getSize());
    }


    /**
     * 准备充值
     */
    @Transactional
    public RechargePayVo toPay(RechargeLevelEnum vipLevel, String couponCode) {
        Long userId = ReqInfoContext.getReqInfo().getUserId();
        List<RechargeEntity> list = rechargeRepository.findByUserIdAndVipLevelAndStatusInOrderByIdDesc(userId, vipLevel.getValue(), List.of(RechargeStatusEnum.NOT_PAY.getValue(), RechargeStatusEnum.PAYING.getValue()));
        // 如果存在支付中的，则不允许再次发起
        if (list.stream().anyMatch(s -> s.getStatus().equals(RechargeStatusEnum.PAYING.getValue()))) {
            throw new BizException(StatusEnum.REPEAT_PAY);
        }

        // 找到最近的一个待支付记录
        // 记录原始需要支付的金额
        final Integer orgAmount = getVipPrice(vipLevel);
        // 记录实际支付金额
        int payAmount = orgAmount;
        if (StringUtils.isNotBlank(couponCode)) {
            // 基于优惠券，获取优惠后的金额
            CouponEntity coupon = couponService.getCouponByCode(couponCode);
            if (coupon == null || coupon.getUseCount() + 1 >= coupon.getCouponCount()) {
                throw new BizException(StatusEnum.RECORDS_NOT_EXISTS, "来晚一步，优惠券已经用完了哦~");
            }
            payAmount = PriceUtil.calculatePriceAfterCoupon(orgAmount, coupon.getCouponType(), coupon.getCouponValue());
        }

        Assert.notNull(payAmount, "充值金额不准确~");
        RechargeEntity entity = list.stream()
                .filter(s -> s.getStatus().equals(RechargeStatusEnum.NOT_PAY.getValue())
                        && (
                        (StringUtils.isBlank(couponCode) && StringUtils.isBlank(s.getCouponCode()))
                                || Objects.equals(couponCode, s.getCouponCode()))
                )
                .findFirst()
                .orElse(null);
        if (entity == null) {
            // 不存在，则创建一个待支付记录
            Long payId = IdUtil.genId();
            entity = new RechargeEntity()
                    .setId(payId)
                    .setStatus(RechargeStatusEnum.NOT_PAY.getValue())
                    .setUserId(userId)
                    .setAmount(payAmount)
                    .setVipLevel(vipLevel.getValue())
                    // 优惠金额
                    .setPromotionAmount(orgAmount - payAmount)
                    .setCouponCode(couponCode)
                    .setCreateTime(new Date())
                    .setUpdateTime(new Date());
        }

        // 已经支付过，判断是否可以直接用它
        if (checkPrePayIdValid(entity)) {
            // 合法，则直接返回
            return RechargeConvert.toVo(entity);
        }

        if (payAmount == 0) {
            // 算上优惠之后，若支付价格为0，则直接设置为充值成功
            entity.setStatus(RechargeStatusEnum.SUCCEED.getValue());
            rechargeRepository.saveAndFlush(entity);
            processAfterRechargeStatusChange(entity, RechargeStatusEnum.NOT_PAY.getValue());
            return RechargeConvert.toVo(entity);
        }

        // 非法, 重新向微信支付获取预支付ID
        ThirdPayWayEnum payWay = ThirdPayWayEnum.WX_NATIVE;
        entity.setPayWay(payWay.getValue());
        entity.setTradeNo(IdUtil.genPayCode(payWay, entity.getId()));

        // 需要像微信重新创建支付订单，并且将结果反写到支付记录中
        ThirdPayOrderReqBo req = new ThirdPayOrderReqBo();
        req.setTotal(payAmount);
        req.setOutTradeNo(entity.getTradeNo());
        req.setDescription(entity.getUserId() + "的会员充值");
        // 目前只支持native支付
        req.setPayWay(payWay);
        PrePayInfoResBo res = thirdPayHandler.createPayOrder(req);
        entity.setPrePayId(res.getPrePayId());
        entity.setPrePayExpireTime(new Date(res.getExpireTime()));
        entity.setUpdateTime(new Date());
        rechargeRepository.saveAndFlush(entity);

        // 返回预支付信息
        return RechargeConvert.toVo(entity);
    }

    public RechargeLevelEnum getRechargeLevel(String price) {
        CommonDictVo dict = commonDictService.queryDict(RechargeConstants.RECHARGE_APP, RechargeConstants.VIP_PRICE_KEY);
        if (dict == null) {
            return null;
        }
        for (DictItemVo item : dict.items()) {
            if (item.value().equals(price)) {
                return RechargeConstants.RECHARGE_LEVEL_MAP.get(item.intro());
            }
        }
        return null;
    }

    public Integer getVipPrice(RechargeLevelEnum level) {
        CommonDictVo dict = commonDictService.queryDict(RechargeConstants.RECHARGE_APP, RechargeConstants.VIP_PRICE_KEY);
        if (dict == null) {
            return null;
        }
        for (DictItemVo item : dict.items()) {
            if (item.intro().equals(level.getDesc())) {
                return PriceUtil.toCentPrice(item.value());
            }
        }
        return null;
    }

    private boolean checkPrePayIdValid(RechargeEntity recharge) {
        if (recharge.getPrePayId() == null || recharge.getPrePayExpireTime() == null) {
            return false;
        }

        if (recharge.getPrePayExpireTime().getTime() < System.currentTimeMillis() - 60_000L) {
            return false;
        }

        return true;
    }


    /**
     * 支付中
     */
    @Transactional
    public boolean paying(Long rechargeId) {
        RechargeEntity entity = rechargeRepository.selectByIdForUpdate(rechargeId);
        if (entity == null) {
            return false;
        }

        // 原始的支付状态
        final int orgStatus = entity.getStatus();
        // 主动查询一下支付状态
        try {
            PayCallbackBo bo = thirdPayHandler.queryOrder(entity.getTradeNo(), ThirdPayWayEnum.ofPay(entity.getPayWay()));
            if (bo.getPayStatus() == RechargeStatusEnum.SUCCEED || bo.getPayStatus() == RechargeStatusEnum.FAIL) {
                // 实际结果是支付成功/支付失败时，刷新下record对应的内容
                // 更新原来的支付状态为最新的结果
                entity.setStatus(bo.getPayStatus().getValue());
                entity.setPayCallbackTime(new Date(bo.getSuccessTime()));
                entity.setUpdateTime(new Date());
                entity.setThirdTransCode(bo.getThirdTransactionId());
                rechargeRepository.saveAndFlush(entity);
            } else {
                // 直接更新为支付中
                entity.setStatus(RechargeStatusEnum.PAYING.getValue());
                entity.setUpdateTime(new Date());
                rechargeRepository.saveAndFlush(entity);
            }

            processAfterRechargeStatusChange(entity, orgStatus);
        } catch (Exception e) {
            log.error("查询三方支付状态出现异常: {}", JsonUtil.toStr(entity), e);
        }

        // 依然返回true，将支付状态设置为true
        return true;
    }

    @Transactional
    public boolean refreshOrInitPay(Long rechargeId) {
        RechargeEntity entity = rechargeRepository.selectByIdForUpdate(rechargeId);
        if (entity == null) {
            return false;
        }

        final int orgStatus = entity.getStatus();
        // 主动查询一下支付状态
        try {
            PayCallbackBo bo = thirdPayHandler.queryOrder(entity.getTradeNo(), ThirdPayWayEnum.ofPay(entity.getPayWay()));
            if (bo.getPayStatus() == RechargeStatusEnum.SUCCEED || bo.getPayStatus() == RechargeStatusEnum.FAIL) {
                // 实际结果是支付成功/支付失败时，刷新下record对应的内容
                // 更新原来的支付状态为最新的结果
                entity.setStatus(bo.getPayStatus().getValue());
                entity.setPayCallbackTime(new Date(bo.getSuccessTime()));
                entity.setUpdateTime(new Date());
                entity.setThirdTransCode(bo.getThirdTransactionId());
                rechargeRepository.saveAndFlush(entity);
            } else if (bo.getPayStatus() == RechargeStatusEnum.PAYING) {
                // 微信返回支付中，但是数据库中不是支付中的状态，则同步更新支付中状态
                if (!Objects.equals(entity.getStatus(), RechargeStatusEnum.PAYING.getValue())) {
                    // 直接更新为支付中
                    entity.setStatus(RechargeStatusEnum.PAYING.getValue());
                    entity.setUpdateTime(new Date());
                    rechargeRepository.saveAndFlush(entity);
                }
            } else if (System.currentTimeMillis() >= entity.getPrePayExpireTime().getTime() - 60_000L) {
                // 超时还是未支付的场景，直接标记未支付失败
                entity.setStatus(RechargeStatusEnum.FAIL.getValue());
                entity.setThirdTransCode("超时未支付");
                entity.setUpdateTime(new Date());
                rechargeRepository.saveAndFlush(entity);
            }

            processAfterRechargeStatusChange(entity, orgStatus);
        } catch (Exception e) {
            log.error("查询三方支付状态出现异常: {}", JsonUtil.toStr(entity), e);
        }

        // 依然返回true，将支付状态设置为true
        return true;
    }

    @Transactional
    public boolean payed(PayCallbackBo transaction) {
        log.info("微信支付回调执行业务逻辑 {}", transaction);
        if (transaction.getOutTradeNo().startsWith("TEST-")) {
            // TestController 中关于测试支付的回调逻辑时，我们只通过消息进行通知用户即可
            long payUser = transaction.getPayId();
            return true;
        }
        // 更新支付状态
        RechargeEntity entity = rechargeRepository.selectByIdForUpdate(transaction.getPayId());
        if (entity == null || !Objects.equals(entity.getTradeNo(), transaction.getOutTradeNo())) {
            throw new BizException(StatusEnum.RECORDS_NOT_EXISTS, "支付记录:" + transaction.getPayId());
        }

        if (Objects.equals(entity.getStatus(), transaction.getPayStatus().getValue())
                || RechargeStatusEnum.SUCCEED.getValue().equals(entity.getStatus())) {
            // 幂等，or已支付成功，不进行后续更新
            return true;
        }

        // 更新支付结果
        final int orgStatus = entity.getStatus();
        entity.setStatus(transaction.getPayStatus().getValue());
        entity.setPayCallbackTime(new Date(transaction.getSuccessTime()));
        entity.setUpdateTime(new Date());
        entity.setThirdTransCode(transaction.getThirdTransactionId());
        rechargeRepository.saveAndFlush(entity);

        // 状态变更后的回调
        this.processAfterRechargeStatusChange(entity, orgStatus);
        return true;
    }

    /**
     * 支付回调
     *
     * @param request
     * @param payCallback
     * @return
     */
    public ResponseEntity<?> payCallback(HttpServletRequest request, Function<PayCallbackBo, Boolean> payCallback) {
        try {
            PayCallbackBo bo = thirdPayHandler.payCallback(request, ThirdPayWayEnum.WX_NATIVE);
            boolean ans = payCallback.apply(bo);
            if (ans) {
                // 处理成功，返回 200 OK 状态码
                return ResponseEntity.status(HttpStatus.OK).build();
            } else {
                // 处理异常，返回 500 服务器内部异常 状态码
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
            }
        } catch (Exception e) {
            log.error("微信支付回调v3java失败={}", e.getMessage(), e);
        }
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
    }

    private void processAfterRechargeStatusChange(RechargeEntity entity, Integer oldStatus) {
        // 更新优惠券使用次数
        this.autoUpdateCouponCnt(entity.getCouponCode(), oldStatus, entity.getStatus());
        // 更新用户VIP信息
        this.autoUpdateUserVipInfo(entity, oldStatus);
    }

    /**
     * 自动更新优惠券使用次数
     * fixme: 说明，这里的计数，并不是严格券码使用方案，是可能存在用超的场景：因为我们pc支付的有效期是2小时，为了最大化的提供用户使用感受
     * fixme: 这两个小时内绑定的优惠券不会占用使用次数，即优惠券实际用超了，用户也是可以正常支付的；只要用户在唤起支付时，券码足够就能使用
     *
     * @param couponCode
     * @param oldStatus
     * @param newStatus
     */
    private void autoUpdateCouponCnt(String couponCode, Integer oldStatus, Integer newStatus) {
        if (StringUtils.isBlank(couponCode)) {
            return;
        }

        if (List.of(RechargeStatusEnum.PAYING.getValue(), RechargeStatusEnum.SUCCEED.getValue()).contains(oldStatus)) {
            // 支付变为无效时，释放使用次数
            if (List.of(RechargeStatusEnum.NOT_PAY.getValue(), RechargeStatusEnum.FAIL.getValue()).contains(newStatus)) {
                couponService.updateCouponUseCount(couponCode, -1);
            }
        } else {
            // 状态变更为支付中、支付成功时，优惠券使用次数 +1
            if (List.of(RechargeStatusEnum.PAYING.getValue(), RechargeStatusEnum.SUCCEED.getValue()).contains(newStatus)) {
                couponService.updateCouponUseCount(couponCode, 1);
            }
        }
    }

    private void autoUpdateUserVipInfo(RechargeEntity entity, Integer oldStatus) {
        if (!oldStatus.equals(RechargeStatusEnum.SUCCEED.getValue()) && RechargeStatusEnum.SUCCEED.getValue().equals(entity.getStatus())) {
            userService.updateUserVipInfo(entity.getUserId(), IntBaseEnum.getEnumByCode(RechargeLevelEnum.class, entity.getVipLevel()));
        }
    }

    /**
     * 自动释放超时未支付的充值记录
     */
    @Scheduled(cron = "0 0/5 * * * ?")
    public void scheduleToRefreshOverPayRecharge() {
        // 找到超时未支付的充值记录
        List<RechargeEntity> list = rechargeRepository.findByStatusAndPrePayExpireTimeBefore(RechargeStatusEnum.PAYING.getValue(),
                new Date(System.currentTimeMillis()));
        for (RechargeEntity entity : list) {
            // 刷新支付状态
            SpringUtil.getBean(RechargeService.class).refreshOrInitPay(entity.getId());
        }
    }
}
