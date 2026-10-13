package com.git.hui.jobclaw.gather.dao.repository;

import com.git.hui.jobclaw.core.apis.PageListVo;
import com.git.hui.jobclaw.gather.dao.entity.GatherSourceEntity;
import com.git.hui.jobclaw.web.model.req.GatherSourceSearchReq;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.ArrayList;
import java.util.List;

public interface GatherSourceRepository extends JpaRepository<GatherSourceEntity, Long>, JpaSpecificationExecutor<GatherSourceEntity> {
    GatherSourceEntity findFirstBySourceHashAndStatusNot(String sourceHash, String status);

    GatherSourceEntity findFirstByContentHashAndStatusNotOrderByTypeDescIdAsc(String contentHash, String status);

    List<GatherSourceEntity> findByStatusNotOrderByContentHashAscTypeDescIdAsc(String status);

    default PageListVo<GatherSourceEntity> findList(GatherSourceSearchReq req) {
        Specification<GatherSourceEntity> spec = (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (req.getSourceId() != null) {
                predicates.add(criteriaBuilder.equal(root.get("id"), req.getSourceId()));
            }
            if (req.getType() != null) {
                predicates.add(criteriaBuilder.equal(root.get("type"), req.getType()));
            }
            if (req.getOwnerType() != null && !req.getOwnerType().isBlank()) {
                predicates.add(criteriaBuilder.equal(root.get("ownerType"), req.getOwnerType()));
            }
            if (req.getStatus() != null && !req.getStatus().isBlank()) {
                predicates.add(criteriaBuilder.equal(root.get("status"), req.getStatus()));
            }
            if (req.getKeyword() != null && !req.getKeyword().isBlank()) {
                String keyword = "%" + req.getKeyword().trim() + "%";
                predicates.add(criteriaBuilder.or(
                        criteriaBuilder.like(root.get("title"), keyword),
                        criteriaBuilder.like(root.get("content"), keyword)
                ));
            }
            return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };

        Page<GatherSourceEntity> ans = findAll(
                spec,
                PageRequest.of(req.getPage() - 1, req.getSize())
                        .withSort(Sort.by(Sort.Order.desc("updateTime"), Sort.Order.desc("id")))
        );
        return PageListVo.of(ans.getContent(), ans.getTotalElements(), req.getPage(), req.getSize());
    }
}
