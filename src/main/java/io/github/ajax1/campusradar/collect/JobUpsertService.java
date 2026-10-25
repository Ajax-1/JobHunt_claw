package io.github.ajax1.campusradar.collect;

import io.github.ajax1.campusradar.job.JobPosting;
import io.github.ajax1.campusradar.job.JobPostingRepository;
import io.github.ajax1.campusradar.job.JobStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

/**
 * 单个岗位的写入：按 (source, externalId) 判断新增还是更新，内容没变只刷新 lastSeenAt
 */
@Service
public class JobUpsertService {

    public enum Outcome {CREATED, UPDATED, UNCHANGED}

    private final JobPostingRepository repository;

    public JobUpsertService(JobPostingRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public Optional<JobPosting> find(String source, String externalId) {
        return repository.findBySourceAndExternalId(source, externalId);
    }

    @Transactional
    public Outcome upsert(String source, String company, RawJob raw, Instant seenAt) {
        String cities = Texts.joinCities(raw.cities());
        // 摘要覆盖所有由解析得出的字段，解析逻辑改进后下一轮采集就能把旧数据刷新
        String hash = Texts.hash(raw.title(), raw.category(), cities, raw.description(), raw.requirement(),
                raw.recruitType() == null ? null : raw.recruitType().name(),
                raw.gradYear() == null ? null : raw.gradYear().toString(), raw.applyUrl());
        JobPosting job = repository.findBySourceAndExternalId(source, raw.externalId()).orElse(null);
        if (job == null) {
            job = new JobPosting();
            job.setSource(source);
            job.setExternalId(raw.externalId());
            job.setFirstSeenAt(seenAt);
            apply(job, company, raw, cities, hash, seenAt);
            repository.save(job);
            return Outcome.CREATED;
        }
        boolean reopened = job.getStatus() == JobStatus.CLOSED;
        job.setLastSeenAt(seenAt);
        if (!reopened && hash.equals(job.getContentHash())) {
            return Outcome.UNCHANGED;
        }
        apply(job, company, raw, cities, hash, seenAt);
        return Outcome.UPDATED;
    }

    @Transactional
    public int closeNotSeenSince(String source, Instant runStart) {
        return repository.closeNotSeenSince(source, runStart, Instant.now());
    }

    @Transactional(readOnly = true)
    public long countOpen(String source) {
        return repository.countBySourceAndStatus(source, JobStatus.OPEN);
    }

    private static void apply(JobPosting job, String company, RawJob raw, String cities, String hash, Instant seenAt) {
        job.setCompany(company);
        job.setTitle(raw.title());
        job.setCategory(raw.category());
        job.setRecruitType(raw.recruitType());
        job.setGradYear(raw.gradYear());
        job.setCities(cities);
        job.setDescription(raw.description());
        job.setRequirement(raw.requirement());
        job.setApplyUrl(raw.applyUrl());
        if (raw.publishedAt() != null) {
            job.setPublishedAt(raw.publishedAt());
        }
        job.setPostedAt(job.getPublishedAt() != null ? job.getPublishedAt() : job.getFirstSeenAt());
        job.setStatus(JobStatus.OPEN);
        job.setLastSeenAt(seenAt);
        job.setContentHash(hash);
        job.setUpdatedAt(Instant.now());
    }
}
