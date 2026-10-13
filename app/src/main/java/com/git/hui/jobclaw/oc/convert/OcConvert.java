package com.git.hui.jobclaw.oc.convert;

import com.git.hui.jobclaw.oc.dao.entity.OcDraftEntity;
import com.git.hui.jobclaw.oc.dao.entity.OcInfoEntity;
import com.git.hui.jobclaw.util.DateUtil;
import com.git.hui.jobclaw.web.model.res.OcVo;

import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 转换类
 *
 * @date 2025/7/14
 */
public class OcConvert {

    public static OcInfoEntity toOc(OcDraftEntity draft) {
        return new OcInfoEntity()
                .setDraftId(draft.getId())
                .setSourceId(draft.getSourceId())
                .setSourceTaskId(draft.getSourceTaskId())
                .setRunnerType(draft.getRunnerType())
                .setCompanyName(draft.getCompanyName())
                .setCompanyType(draft.getCompanyType())
                .setCompanyIndustry(draft.getCompanyIndustry())
                .setJobLocation(draft.getJobLocation())
                .setRecruitmentType(draft.getRecruitmentType())
                .setRecruitmentTarget(draft.getRecruitmentTarget())
                .setPosition(draft.getPosition())
                .setDeliveryProgress(draft.getDeliveryProgress())
                // 日期转时间
                .setLastUpdatedTime(DateUtil.toDateOrNow(draft.getLastUpdatedTime()))
                .setDeadline(draft.getDeadline())
                .setRelatedLink(draft.getRelatedLink())
                .setJobAnnouncement(draft.getJobAnnouncement())
                .setInternalReferralCode(draft.getInternalReferralCode())
                .setRemarks(draft.getRemarks())
                .setState(draft.getState())
                .setCreateTime(draft.getCreateTime())
                .setUpdateTime(new Date());
    }

    public static OcVo toVo(OcInfoEntity entity) {
        return new OcVo()
                .setId(entity.getId())
                .setDraftId(entity.getDraftId())
                .setSourceId(entity.getSourceId())
                .setSourceTaskId(entity.getSourceTaskId())
                .setRunnerType(entity.getRunnerType())
                .setCompanyName(entity.getCompanyName())
                .setCompanyType(entity.getCompanyType())
                .setCompanyIndustry(entity.getCompanyIndustry())
                .setJobLocation(entity.getJobLocation())
                .setRecruitmentType(entity.getRecruitmentType())
                .setRecruitmentTarget(entity.getRecruitmentTarget())
                .setPosition(entity.getPosition())
                .setDeliveryProgress(entity.getDeliveryProgress())
                .setLastUpdatedTime(DateUtil.time2date(entity.getLastUpdatedTime().getTime()))
                .setDeadline(entity.getDeadline())
                .setRelatedLink(entity.getRelatedLink())
                .setJobAnnouncement(entity.getJobAnnouncement())
                .setInternalReferralCode(entity.getInternalReferralCode())
                .setRemarks(entity.getRemarks())
                .setState(entity.getState())
                .setCreateTime(entity.getCreateTime().getTime())
                .setUpdateTime(entity.getUpdateTime().getTime());
    }

    public static List<OcVo> toVoList(List<OcInfoEntity> list) {
        if (list == null || list.isEmpty()) {
            return Collections.emptyList();
        }
        return list.stream().map(entity -> toVo(entity)).collect(Collectors.toList());
    }
}
