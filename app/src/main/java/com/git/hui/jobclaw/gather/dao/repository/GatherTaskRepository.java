
package com.git.hui.jobclaw.gather.dao.repository;

import com.git.hui.jobclaw.gather.dao.entity.GatherTaskEntity;
import com.git.hui.jobclaw.core.apis.PageListVo;
import com.git.hui.jobclaw.web.model.req.GatherTaskSearchReq;
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

public interface GatherTaskRepository extends JpaRepository<GatherTaskEntity, Long>, JpaSpecificationExecutor<GatherTaskEntity> {


    /**
     * 找到最早提交的待处理任务
     *
     * @param state
     * @return
     */
    GatherTaskEntity findFirstByStateOrderByCreateTimeAsc(Integer state);

    List<GatherTaskEntity> findTop100BySourceIdIsNullOrderByIdAsc();

    List<GatherTaskEntity> findTop500BySourceIdIsNotNullOrderByIdDesc();

    @Modifying(clearAutomatically = true)
    @Query(value = "update gather_task g set g.sourceId = :targetSourceId where g.sourceId = :sourceId")
    int moveSourceTasks(@Param("sourceId") Long sourceId, @Param("targetSourceId") Long targetSourceId);

    default PageListVo<GatherTaskEntity> findList(GatherTaskSearchReq req) {
        Specification<GatherTaskEntity> spec = (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (req.getType() != null) {
                predicates.add(criteriaBuilder.equal(root.get("type"), req.getType()));
            }
            if (req.getModel() != null) {
                predicates.add(criteriaBuilder.equal(root.get("model"), req.getModel()));
            }
            if (req.getTaskId() != null) {
                predicates.add(criteriaBuilder.equal(root.get("id"), req.getTaskId()));
            }
            if (req.getSourceId() != null) {
                predicates.add(criteriaBuilder.equal(root.get("sourceId"), req.getSourceId()));
            }
            if (req.getRunnerType() != null && !req.getRunnerType().isBlank()) {
                predicates.add(criteriaBuilder.equal(root.get("runnerType"), req.getRunnerType()));
            }
            if (req.getState() != null) {
                predicates.add(criteriaBuilder.equal(root.get("state"), req.getState()));
            }
            return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };

        // 分页时，PageNumber 从 0开始
        Page<GatherTaskEntity> ans = findAll(spec
                // 分页查询
                , PageRequest.of(req.getPage() - 1, req.getSize())
                        // 根据时间倒排，时间相同的根据id进行倒排
                        .withSort(Sort.by(Sort.Order.desc("createTime"), Sort.Order.desc("id")))
        );
        return PageListVo.of(ans.getContent(), ans.getTotalElements(), req.getPage(), req.getSize());
    }
}
