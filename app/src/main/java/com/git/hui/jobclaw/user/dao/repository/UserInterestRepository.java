
package com.git.hui.jobclaw.user.dao.repository;

import com.git.hui.jobclaw.user.dao.entity.UserInterestEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface UserInterestRepository extends JpaRepository<UserInterestEntity, Long>, JpaSpecificationExecutor<UserInterestEntity> {

    UserInterestEntity findByUserId(Long userId);

}
