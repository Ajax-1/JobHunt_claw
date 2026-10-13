package com.git.hui.jobclaw.oc.helper;

import com.git.hui.jobclaw.configs.service.CommonDictService;
import com.git.hui.jobclaw.constants.oc.OcConstants;
import com.git.hui.jobclaw.oc.convert.OcConvert;
import com.git.hui.jobclaw.oc.dao.entity.OcDraftEntity;
import com.git.hui.jobclaw.oc.dao.entity.OcInfoEntity;
import com.git.hui.jobclaw.user.dao.entity.UserInterestEntity;
import com.git.hui.jobclaw.web.model.res.CommonDictVo;
import com.git.hui.jobclaw.web.model.res.DictItemVo;
import io.micrometer.common.util.StringUtils;
import org.springframework.stereotype.Component;

/**
 * @date 2025/8/21
 */
@Component
public class OcInfoTransfer {
    private final CommonDictService commonDictService;

    public OcInfoTransfer(CommonDictService commonDictService) {
        this.commonDictService = commonDictService;
    }

    /**
     * 根据字典配置中的值，对草稿中的相关字段进行格式化
     *
     * @param draft 草稿数据
     */
    public OcInfoEntity autoFormatDraftInfo(OcDraftEntity draft) {
        OcInfoEntity ocEntity = OcConvert.toOc(draft);
        // 1. 公司类型
        commonDictService.queryDictForOptional(OcConstants.APP, OcConstants.COMPANY_TYPE_KEY)
                .ifPresent(dict -> {
                    // 没有匹配的公司类型，则设置为空，让后续进行编辑
                    if (dict.items().stream().noneMatch(item ->
                            ocEntity.getCompanyType().equals(item.value())
                                    || ocEntity.getCompanyType().equals(item.intro())
                    )) {
                        ocEntity.setCompanyType("");
                    }
                });

        // 招聘类型
        commonDictService.queryDictForOptional(OcConstants.APP, OcConstants.RECRUITMENT_TYPE_KEY)
                .ifPresent(dict -> {
                    if (dict.items().stream().noneMatch(item ->
                            ocEntity.getRecruitmentType().equals(item.value())
                                    || ocEntity.getRecruitmentType().equals(item.intro())
                    )) {
                        ocEntity.setRecruitmentType("");
                    }
                });

        // 招聘对象
        commonDictService.queryDictForOptional(OcConstants.APP, OcConstants.RECRUITMENT_TARGET_KEY)
                .ifPresent(dict -> {
                    if (dict.items().stream().noneMatch(item ->
                            ocEntity.getRecruitmentTarget().equals(item.value())
                                    || ocEntity.getRecruitmentTarget().equals(item.intro())
                    )) {
                        ocEntity.setRecruitmentTarget("");
                    }
                });

        // 工作地点格式化
        ocEntity.setJobLocation(OcConstants.jobLocationFormat(ocEntity.getJobLocation()));
        // 职位格式化
        ocEntity.setPosition(OcConstants.positionFormat(ocEntity.getPosition()));

        // 链接校验
        ocEntity.setRelatedLink(OcConstants.urlCheck(ocEntity.getRelatedLink()));
        ocEntity.setJobAnnouncement(OcConstants.urlCheck(ocEntity.getJobAnnouncement()));
        return ocEntity;
    }

    /**
     * 根据字典配置中的值，对用户兴趣进行格式化（主要的目的是将大模型提取的结果，与系统预设的字典进行匹配，方便后续的查询推荐）
     *
     * @param entity 用户兴趣
     */
    public void autoFormatDraftInfo(UserInterestEntity entity) {
        // 1. 公司类型
        if (StringUtils.isNotBlank(entity.getCompanyType())) {
            String[] types = entity.getCompanyType().split(",");
            CommonDictVo vo = commonDictService.queryDict(OcConstants.APP, OcConstants.COMPANY_TYPE_KEY);
            if (vo != null) {
                StringBuilder builder = new StringBuilder();
                for (String type : types) {
                    for (DictItemVo item : vo.items()) {
                        if (type.equals(item.value()) || type.equals(item.intro())) {
                            builder.append(type).append(",");
                        }
                    }
                }
                if (!builder.isEmpty()) {
                    entity.setCompanyType(builder.substring(0, builder.length() - 1));
                } else {
                    entity.setCompanyType("");
                }
            }
        }

        // 2. 招聘类型
        if (StringUtils.isNotBlank(entity.getRecruitmentType())) {
            String[] types  = entity.getRecruitmentType().split(",");
            CommonDictVo vo = commonDictService.queryDict(OcConstants.APP, OcConstants.RECRUITMENT_TYPE_KEY);
            if (vo != null) {
                boolean internship = "实习".equals(entity.getRecruitmentType());
                StringBuilder builder = new StringBuilder();
                for (String type : types) {
                    for (DictItemVo item : vo.items()) {
                        if (type.equals(item.value()) || type.equals(item.intro())) {
                            builder.append(item.value()).append(",");
                        } else if (internship && item.intro().contains("实习")) {
                            // 针对实习进行特殊处理
                            builder.append(item.intro()).append(",");
                        }
                    }
                }
                if (!builder.isEmpty()) {
                    entity.setRecruitmentType(builder.substring(0, builder.length() - 1));
                } else {
                    entity.setRecruitmentType("");
                }
            }
        }

        // 3.招聘对象
        commonDictService.queryDictForOptional(OcConstants.APP, OcConstants.RECRUITMENT_TARGET_KEY)
                .ifPresent(dict -> {
                    if (dict.items().stream().noneMatch(item ->
                            entity.getRecruitmentTarget().equals(item.value())
                                    || entity.getRecruitmentTarget().equals(item.intro())
                    )) {
                        entity.setRecruitmentTarget("");
                    }
                });

        // 4.工作地点格式化
        entity.setJobLocation(OcConstants.jobLocationFormat(entity.getJobLocation()));
        // 5.职位格式化
        entity.setPosition(OcConstants.positionFormat(entity.getPosition()));
    }
}
