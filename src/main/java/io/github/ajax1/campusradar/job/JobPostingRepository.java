package io.github.ajax1.campusradar.job;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface JobPostingRepository extends JpaRepository<JobPosting, Long>, JpaSpecificationExecutor<JobPosting> {

    Optional<JobPosting> findBySourceAndExternalId(String source, String externalId);

    long countBySourceAndStatus(String source, JobStatus status);

    long countByStatus(JobStatus status);

    /**
     * 把本轮采集没有再出现的岗位标记为下线
     */
    @Modifying
    @Query("update JobPosting j set j.status = io.github.ajax1.campusradar.job.JobStatus.CLOSED, j.updatedAt = :now "
            + "where j.source = :source and j.status = io.github.ajax1.campusradar.job.JobStatus.OPEN and j.lastSeenAt < :runStart")
    int closeNotSeenSince(@Param("source") String source, @Param("runStart") Instant runStart, @Param("now") Instant now);

    /**
     * 老数据没有 postedAt，启动时补上
     */
    @Modifying
    @Query("update JobPosting j set j.postedAt = coalesce(j.publishedAt, j.firstSeenAt) where j.postedAt is null")
    int backfillPostedAt();

    @Query("select j.company as company, count(j) as total from JobPosting j "
            + "where j.status = io.github.ajax1.campusradar.job.JobStatus.OPEN group by j.company order by count(j) desc")
    List<CompanyCount> countOpenByCompany();

    interface CompanyCount {
        String getCompany();

        long getTotal();
    }
}
