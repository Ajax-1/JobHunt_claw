package io.github.ajax1.campusradar.push;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Set;

public interface PushRecordRepository extends JpaRepository<PushRecord, Long> {

    @Query("select r.jobId from PushRecord r where r.profileId = :profileId")
    Set<Long> findPushedJobIds(@Param("profileId") String profileId);
}
