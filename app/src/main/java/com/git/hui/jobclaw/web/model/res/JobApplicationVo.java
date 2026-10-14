package com.git.hui.jobclaw.web.model.res;

import com.git.hui.jobclaw.application.dao.entity.JobApplicationEntity;
import com.git.hui.jobclaw.application.model.ApplicationStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** 仅返回求职记录内容，不向前端暴露或接受记录所有者。 */
public record JobApplicationVo(
        Long id,
        Long jobId,
        String companyName,
        String position,
        String jobLocation,
        String relatedLink,
        String jobAnnouncement,
        String deadline,
        ApplicationStatus status,
        String notes,
        LocalDate nextFollowUpDate,
        LocalDateTime createTime,
        LocalDateTime updateTime
) {
    public static JobApplicationVo from(JobApplicationEntity entity) {
        return new JobApplicationVo(entity.getId(), entity.getJobId(), entity.getCompanyName(),
                entity.getPosition(), entity.getJobLocation(), entity.getRelatedLink(),
                entity.getJobAnnouncement(), entity.getDeadline(), entity.getStatus(), entity.getNotes(),
                entity.getNextFollowUpDate(), entity.getCreateTime(), entity.getUpdateTime());
    }
}
