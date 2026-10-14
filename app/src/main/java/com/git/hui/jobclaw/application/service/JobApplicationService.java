package com.git.hui.jobclaw.application.service;

import com.git.hui.jobclaw.application.dao.entity.JobApplicationEntity;
import com.git.hui.jobclaw.application.dao.repository.JobApplicationRepository;
import com.git.hui.jobclaw.application.model.ApplicationStatus;
import com.git.hui.jobclaw.constants.oc.OcStateEnum;
import com.git.hui.jobclaw.core.apis.PageListVo;
import com.git.hui.jobclaw.core.bizexception.BizException;
import com.git.hui.jobclaw.core.bizexception.StatusEnum;
import com.git.hui.jobclaw.oc.dao.entity.OcInfoEntity;
import com.git.hui.jobclaw.oc.dao.repository.OcRepository;
import com.git.hui.jobclaw.web.model.req.ApplicationUpdateReq;
import com.git.hui.jobclaw.web.model.res.JobApplicationVo;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

/** AI-GENERATED AIDEV-NOTE: 所有读写按登录用户过滤，个人状态不改动公共职位。 */
@Service
public class JobApplicationService {
    private static final int MAX_NOTES_LENGTH = 4000;
    private final JobApplicationRepository applications;
    private final OcRepository jobs;

    public JobApplicationService(JobApplicationRepository applications, OcRepository jobs) {
        this.applications = applications;
        this.jobs = jobs;
    }

    public JobApplicationVo save(Long userId, Long jobId) {
        requireUser(userId);
        requireId(jobId, "职位 ID");
        var existing = applications.findByUserIdAndJobId(userId, jobId);
        if (existing.isPresent()) {
            return JobApplicationVo.from(existing.get());
        }

        OcInfoEntity job = jobs.findById(jobId)
                .filter(value -> OcStateEnum.PUBLISHED.getValue().equals(value.getState()))
                .orElseThrow(() -> new BizException(StatusEnum.RECORDS_NOT_EXISTS, "职位"));
        LocalDateTime now = LocalDateTime.now();
        JobApplicationEntity application = new JobApplicationEntity()
                .setUserId(userId).setJobId(jobId)
                .setCompanyName(orEmpty(job.getCompanyName())).setPosition(orEmpty(job.getPosition()))
                .setJobLocation(orEmpty(job.getJobLocation())).setRelatedLink(orEmpty(job.getRelatedLink()))
                .setJobAnnouncement(orEmpty(job.getJobAnnouncement())).setDeadline(orEmpty(job.getDeadline()))
                .setStatus(ApplicationStatus.SAVED).setNotes("").setCreateTime(now).setUpdateTime(now);
        try {
            return JobApplicationVo.from(applications.saveAndFlush(application));
        } catch (DataIntegrityViolationException duplicate) {
            // AI-GENERATED AIDEV-NOTE: 独立保存事务回滚后读取并发请求已创建的同一条记录。
            return applications.findByUserIdAndJobId(userId, jobId).map(JobApplicationVo::from)
                    .orElseThrow(() -> duplicate);
        }
    }

    public PageListVo<JobApplicationVo> list(Long userId, String status, int page, int size) {
        requireUser(userId);
        if (page < 1 || size < 1 || size > 100) {
            throw invalid("页码必须大于 0，每页数量必须在 1 到 100 之间");
        }
        PageRequest pageable = PageRequest.of(page - 1, size,
                Sort.by(Sort.Order.desc("updateTime"), Sort.Order.desc("id")));
        Page<JobApplicationEntity> result = status == null || status.isBlank()
                ? applications.findByUserId(userId, pageable)
                : applications.findByUserIdAndStatus(userId, parseStatus(status), pageable);
        PageListVo<JobApplicationVo> response = PageListVo.of(result.map(JobApplicationVo::from).getContent(),
                result.getTotalElements(), page, size);
        response.setHasMore(result.hasNext());
        return response;
    }

    @Transactional
    public JobApplicationVo update(Long userId, Long id, ApplicationUpdateReq request) {
        requireUser(userId);
        requireId(id, "记录 ID");
        if (request == null) {
            throw invalid("请提交求职记录内容");
        }
        ApplicationStatus status = parseStatus(request.status());
        String notes = orEmpty(request.notes());
        if (notes.length() > MAX_NOTES_LENGTH) {
            throw invalid("备注不能超过 4000 字");
        }
        LocalDate nextFollowUpDate = parseFollowUpDate(request.nextFollowUpDate());
        JobApplicationEntity application = ownedApplication(userId, id);
        application.setStatus(status).setNotes(notes).setNextFollowUpDate(nextFollowUpDate)
                .setUpdateTime(LocalDateTime.now());
        return JobApplicationVo.from(applications.saveAndFlush(application));
    }

    @Transactional
    public boolean delete(Long userId, Long id) {
        requireUser(userId);
        requireId(id, "记录 ID");
        applications.delete(ownedApplication(userId, id));
        return true;
    }

    private JobApplicationEntity ownedApplication(Long userId, Long id) {
        return applications.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new BizException(StatusEnum.RECORDS_NOT_EXISTS, "求职记录"));
    }

    private ApplicationStatus parseStatus(String value) {
        try {
            return ApplicationStatus.valueOf(value == null ? "" : value);
        } catch (IllegalArgumentException exception) {
            throw invalid("请选择有效的求职状态");
        }
    }

    private LocalDate parseFollowUpDate(String value) {
        if (value == null || value.isEmpty()) {
            return null;
        }
        if (!value.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}")) {
            throw invalid("跟进日期必须是 yyyy-MM-dd 格式的有效日期");
        }
        try {
            LocalDate date = LocalDate.parse(value, DateTimeFormatter.ISO_LOCAL_DATE);
            if (date.getYear() < 1) {
                throw invalid("跟进日期必须是 yyyy-MM-dd 格式的有效日期");
            }
            return date;
        } catch (DateTimeParseException exception) {
            throw invalid("跟进日期必须是 yyyy-MM-dd 格式的有效日期");
        }
    }

    private void requireUser(Long userId) {
        if (userId == null || userId <= 0) {
            throw new BizException(StatusEnum.FORBID_NOTLOGIN);
        }
    }

    private void requireId(Long id, String label) {
        if (id == null || id <= 0) {
            throw invalid(label + " 必须是正整数");
        }
    }

    private static String orEmpty(String value) {
        return value == null ? "" : value;
    }

    private static BizException invalid(String message) {
        return new BizException(StatusEnum.ILLEGAL_ARGUMENTS_MIXED, message);
    }
}
