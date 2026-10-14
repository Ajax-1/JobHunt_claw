package com.git.hui.jobclaw.application.service;

import com.git.hui.jobclaw.application.dao.entity.JobApplicationEntity;
import com.git.hui.jobclaw.application.dao.repository.JobApplicationRepository;
import com.git.hui.jobclaw.application.model.ApplicationStatus;
import com.git.hui.jobclaw.constants.oc.OcStateEnum;
import com.git.hui.jobclaw.core.bizexception.BizException;
import com.git.hui.jobclaw.oc.dao.entity.OcInfoEntity;
import com.git.hui.jobclaw.oc.dao.repository.OcRepository;
import com.git.hui.jobclaw.web.model.req.ApplicationUpdateReq;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class JobApplicationServiceTest {
    private JobApplicationRepository applications;
    private OcRepository jobs;
    private JobApplicationService service;

    @BeforeEach
    void setUp() {
        applications = mock(JobApplicationRepository.class);
        jobs = mock(OcRepository.class);
        service = new JobApplicationService(applications, jobs);
    }

    @Test
    void saveIsIdempotentAndKeepsJobSnapshot() {
        var existing = new JobApplicationEntity().setId(7L).setUserId(10L).setJobId(3L)
                .setStatus(ApplicationStatus.APPLIED).setNotes("已投递");
        when(applications.findByUserIdAndJobId(10L, 3L)).thenReturn(Optional.of(existing));

        var result = service.save(10L, 3L);

        assertThat(result.id()).isEqualTo(7L);
        verifyNoInteractions(jobs);
        verify(applications, never()).saveAndFlush(any());
    }

    @Test
    void saveUsesPublishedJobAndDoesNotReusePublicProgress() {
        when(applications.findByUserIdAndJobId(10L, 3L)).thenReturn(Optional.empty());
        var job = new OcInfoEntity().setId(3L).setState(OcStateEnum.PUBLISHED.getValue())
                .setCompanyName("Acme").setPosition("Java 工程师").setJobLocation("北京")
                .setRelatedLink("https://example.test/job/3").setJobAnnouncement("公告").setDeadline("2026-10-31")
                .setDeliveryProgress("公共字段");
        when(jobs.findById(3L)).thenReturn(Optional.of(job));
        when(applications.saveAndFlush(any())).thenAnswer(invocation -> {
            JobApplicationEntity value = invocation.getArgument(0);
            return value.setId(8L);
        });

        var result = service.save(10L, 3L);

        assertThat(result.status()).isEqualTo(ApplicationStatus.SAVED);
        assertThat(result.companyName()).isEqualTo("Acme");
        assertThat(result.position()).isEqualTo("Java 工程师");
        var captor = ArgumentCaptor.forClass(JobApplicationEntity.class);
        verify(applications).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getNotes()).isEmpty();
        assertThat(captor.getValue().getStatus()).isEqualTo(ApplicationStatus.SAVED);
        assertThat(captor.getValue().getJobId()).isEqualTo(3L);
    }

    @Test
    void updateAndDeleteOnlyUseCurrentUserOwnership() {
        var application = new JobApplicationEntity().setId(8L).setUserId(10L).setJobId(3L)
                .setStatus(ApplicationStatus.SAVED).setNotes("");
        when(applications.findByIdAndUserId(8L, 10L)).thenReturn(Optional.of(application));
        when(applications.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var result = service.update(10L, 8L, new ApplicationUpdateReq("INTERVIEW", "一面通过", "2026-10-20"));

        assertThat(result.status()).isEqualTo(ApplicationStatus.INTERVIEW);
        assertThat(result.notes()).isEqualTo("一面通过");
        assertThat(result.nextFollowUpDate().toString()).isEqualTo("2026-10-20");
        verify(applications).findByIdAndUserId(8L, 10L);
        service.delete(10L, 8L);
        verify(applications).delete(application);
        verify(applications, never()).findByIdAndUserId(8L, 11L);
    }

    @Test
    void rejectsInvalidStatusDateAndOversizedNotes() {
        var application = new JobApplicationEntity().setId(8L).setUserId(10L).setJobId(3L);
        when(applications.findByIdAndUserId(8L, 10L)).thenReturn(Optional.of(application));

        assertThatThrownBy(() -> service.update(10L, 8L, new ApplicationUpdateReq("UNKNOWN", "", null)))
                .isInstanceOfSatisfying(BizException.class,
                        exception -> assertThat(exception.getMsg()).contains("有效的求职状态"));
        assertThatThrownBy(() -> service.update(10L, 8L, new ApplicationUpdateReq("SAVED", "", "2026-02-30")))
                .isInstanceOfSatisfying(BizException.class,
                        exception -> assertThat(exception.getMsg()).contains("yyyy-MM-dd"));
        assertThatThrownBy(() -> service.update(10L, 8L, new ApplicationUpdateReq("SAVED", "x".repeat(4001), null)))
                .isInstanceOfSatisfying(BizException.class,
                        exception -> assertThat(exception.getMsg()).contains("4000"));
        verify(applications, never()).saveAndFlush(any());
    }
}
