package com.git.hui.jobclaw.oc.convert;

import com.git.hui.jobclaw.agents.jobfetch.service.model.FetchedJobInfo;
import com.git.hui.jobclaw.gather.model.GatherOcDraftBo;
import com.git.hui.jobclaw.oc.dao.entity.OcDraftEntity;
import org.springframework.util.CollectionUtils;

import java.util.Collections;
import java.util.List;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.regex.Pattern;

/**
 * 转换工具
 *
 * @date 2025/7/14
 */
public class DraftConvert {
    private static final Pattern DATE_PREFIX = Pattern.compile("^(\\d{4})[-/年](\\d{1,2})[-/月](\\d{1,2})日?(?:[T\\s].*)?$");
    private static final DateTimeFormatter DRAFT_DATE = DateTimeFormatter.ofPattern("yyyy年MM月dd日");

    /** AI-GENERATED AIDEV-NOTE: 更新时间参与去重，必须在查重前统一模型返回的日期格式。 */
    static String normalizeUpdatedDate(String text) {
        if (text == null) {
            return null;
        }
        var matcher = DATE_PREFIX.matcher(text.trim());
        if (matcher.matches()) {
            try {
                return LocalDate.of(Integer.parseInt(matcher.group(1)), Integer.parseInt(matcher.group(2)),
                        Integer.parseInt(matcher.group(3))).format(DRAFT_DATE);
            } catch (java.time.DateTimeException ignored) {
                // 非法日期保留原文，不推测补全。
            }
        }
        return text;
    }

    public static OcDraftEntity convert(GatherOcDraftBo bo) {
        return new OcDraftEntity()
                .setCompanyName(bo.companyName())
                .setCompanyType(bo.companyType())
                .setCompanyIndustry(bo.companyIndustry())
                .setJobLocation(bo.jobLocation())
                .setRecruitmentType(bo.recruitmentType())
                .setRecruitmentTarget(bo.requirementTarget())
                .setPosition(bo.position())
                .setDeliveryProgress(bo.deliveryProgress())
                .setLastUpdatedTime(normalizeUpdatedDate(bo.lastUpdatedTime()))
                .setDeadline(bo.deadline())
                .setRelatedLink(bo.relatedLink())
                .setJobAnnouncement(bo.jobAnnouncement())
                .setInternalReferralCode(bo.internalReferralCode())
                .setRemarks(bo.remarks())
                .setSalary(bo.salary())
                .setEducation(bo.education())
                .setExperience(bo.experience())
                ;
    }

    public static List<OcDraftEntity> convert(List<GatherOcDraftBo> bos) {
        if (CollectionUtils.isEmpty(bos)) {
            return Collections.emptyList();
        }
        // 注意下面返回的是不可编辑的列表
        return bos.stream().map(DraftConvert::convert).toList();
    }


    public static OcDraftEntity covert(FetchedJobInfo jobInfo) {
        return new OcDraftEntity()
                .setCompanyName(jobInfo.getCompanyName())
                .setCompanyType(jobInfo.getCompanyType())
                .setCompanyIndustry(jobInfo.getCompanyIndustry())
                .setJobLocation(jobInfo.getJobLocation())
                .setRecruitmentType(jobInfo.getRecruitmentType())
                .setRecruitmentTarget(jobInfo.getRecruitmentTarget())
                .setPosition(jobInfo.getPosition())
                .setDeliveryProgress(jobInfo.getDeliveryProgress())
                .setLastUpdatedTime(normalizeUpdatedDate(jobInfo.getLastUpdatedTime()))
                .setDeadline(jobInfo.getDeadline())
                .setRelatedLink(jobInfo.getRelatedLink())
                .setJobAnnouncement(jobInfo.getJobAnnouncement())
                .setInternalReferralCode(jobInfo.getInternalReferralCode())
                .setRemarks(jobInfo.getRemarks())
                .setSalary(jobInfo.getSalary())
                .setEducation(jobInfo.getEducation())
                .setExperience(jobInfo.getExperience());

    }

}
