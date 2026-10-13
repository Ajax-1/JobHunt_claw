
package com.git.hui.jobclaw.user.dao.repository;

import com.git.hui.jobclaw.constants.user.RechargeStatusEnum;
import com.git.hui.jobclaw.user.dao.entity.RechargeEntity;
import com.git.hui.jobclaw.user.model.RechargeCnt;
import com.git.hui.jobclaw.core.apis.PageListVo;
import com.git.hui.jobclaw.web.model.req.CouponSearchReq;
import jakarta.persistence.LockModeType;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public interface RechargeRepository extends JpaRepository<RechargeEntity, Long>, JpaSpecificationExecutor<RechargeEntity> {

    List<RechargeEntity> findByUserIdAndVipLevelAndStatusInOrderByIdDesc(Long userId, Integer vipLevel, List<Integer> status);


    /**
     * 查询用户的充值记录
     *
     * @param userId
     * @param status
     * @return
     */
    List<RechargeEntity> findByUserIdAndStatusInOrderByIdDesc(Long userId, List<Integer> status);


    /**
     * 使用悲观锁获取充值记录
     *
     * @param id
     * @return
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from user_recharge r where r.id = ?1")
    RechargeEntity selectByIdForUpdate(Long id);

    /**
     * 查询优惠券使用数量
     *
     * @param couponCodes
     * @return
     */
    @Query("select new com.git.hui.jobclaw.user.model.RechargeCnt(r.couponCode, r.status, count(*)) from user_recharge r where r.status in (1, 2) and r.couponCode in ?1 group by r.couponCode, r.status")
    List<RechargeCnt> selectCouponCount(List<String> couponCodes);

    /**
     * 条件查询
     *
     * @param req
     * @return
     */
    default PageListVo<RechargeEntity> findList(CouponSearchReq req) {
        Specification<RechargeEntity> spec = (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(criteriaBuilder.equal(root.get("couponCode"), req.getCouponCode()));
            // 修复状态筛选条件
            predicates.add(root.get("status").in(List.of(RechargeStatusEnum.PAYING.getValue(), RechargeStatusEnum.SUCCEED.getValue())));
            return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };

        // 分页时，PageNumber 从 0开始
        Page<RechargeEntity> ans = findAll(spec
                // 分页查询
                , PageRequest.of(req.getPage() - 1, req.getSize())
                        // 根据优惠券开始时间倒排，时间相同的根据id进行倒排
                        .withSort(Sort.by(Sort.Order.desc("id")))
        );
        return PageListVo.of(ans.getContent(), ans.getTotalElements(), req.getPage(), req.getSize());
    }

    /**
     * 查询超时未支付的充值记录
     *
     * @param status
     * @param expireTime
     * @return
     */
    List<RechargeEntity> findByStatusAndPrePayExpireTimeBefore(Integer status, Date expireTime);
}
