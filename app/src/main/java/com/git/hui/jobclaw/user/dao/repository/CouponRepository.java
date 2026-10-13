
package com.git.hui.jobclaw.user.dao.repository;

import com.git.hui.jobclaw.constants.common.BaseStateEnum;
import com.git.hui.jobclaw.user.dao.entity.CouponEntity;
import com.git.hui.jobclaw.core.apis.PageListVo;
import com.git.hui.jobclaw.web.model.req.CouponSearchReq;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.ArrayList;
import java.util.List;

public interface CouponRepository extends JpaRepository<CouponEntity, Long>, JpaSpecificationExecutor<CouponEntity> {

    public CouponEntity findByCouponCode(String couponCode);

    /**
     * 条件查询
     *
     * @param req
     * @return
     */
    default PageListVo<CouponEntity> findList(CouponSearchReq req) {
        Specification<CouponEntity> spec = (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (req.getCouponCode() != null) {
                predicates.add(criteriaBuilder.equal(root.get("couponCode"), req.getCouponCode()));
            }
            // 过滤掉无效的数据
            predicates.add(criteriaBuilder.equal(root.get("state"), BaseStateEnum.NORMAL_STATE.getValue()));
            return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };

        // 分页时，PageNumber 从 0开始
        Page<CouponEntity> ans = findAll(spec
                // 分页查询
                , PageRequest.of(req.getPage() - 1, req.getSize())
                        // 根据优惠券开始时间倒排，时间相同的根据id进行倒排
                        .withSort(Sort.by(Sort.Order.desc("startTime"), Sort.Order.desc("id")))
        );
        return PageListVo.of(ans.getContent(), ans.getTotalElements(), req.getPage(), req.getSize());
    }

    @Modifying(clearAutomatically = true)
    @Query(value = "update recharge_coupon r set r.useCount = r.useCount + :cnt, r.updateTime=now() where r.couponCode = :couponCode")
    int updateCntByCode(@Param("couponCode") String couponCode, @Param("cnt") Integer cnt);
}
