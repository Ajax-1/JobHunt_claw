package com.git.hui.jobclaw.oc.service;

import com.git.hui.jobclaw.constants.oc.OcStateEnum;
import com.git.hui.jobclaw.core.apis.PageListVo;
import com.git.hui.jobclaw.core.bizexception.BizException;
import com.git.hui.jobclaw.core.bizexception.StatusEnum;
import com.git.hui.jobclaw.oc.convert.OcConvert;
import com.git.hui.jobclaw.oc.dao.entity.OcInfoEntity;
import com.git.hui.jobclaw.oc.dao.repository.OcDraftRepository;
import com.git.hui.jobclaw.oc.dao.repository.OcRepository;
import com.git.hui.jobclaw.core.apis.service.IJobSearchService;
import com.git.hui.jobclaw.core.apis.models.OcJobInfo;
import com.git.hui.jobclaw.core.apis.req.UserInterestRecommendReq;
import com.git.hui.jobclaw.util.DateUtil;
import com.git.hui.jobclaw.web.model.req.OcSaveReq;
import com.git.hui.jobclaw.web.model.req.OcSearchReq;
import com.git.hui.jobclaw.web.model.res.OcVo;
import io.micrometer.common.util.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * @date 2025/7/14
 */
@Service
public class OcService implements IJobSearchService {
    private final OcDraftRepository draftRepository;
    private final OcRepository ocRepository;

    @Autowired
    public OcService(OcDraftRepository repository, OcRepository ocRepository) {
        this.draftRepository = repository;
        this.ocRepository = ocRepository;
    }


    // -------------------------------------------- oc 相关服务 ------------------------------------------

    /**
     * 查询今日上新的列表
     *
     * @return
     */
    public List<OcVo> searchTodayOcList() {
        Date today = new Date(System.currentTimeMillis() / 86400_000 * 86400_000);
        List<OcInfoEntity> list = ocRepository.searchOcInfoEntitiesByCreateTimeAfterAndState(today, OcStateEnum.PUBLISHED.getValue());
        return OcConvert.toVoList(list);
    }

    public PageListVo<OcVo> searchOcList(OcSearchReq req) {
        if (req.getNotState() == null) {
            // 不支持查询已删除状态的数据
            req.setNotState(OcStateEnum.DELETED.getValue());
        }
        req.autoInitPage();
        PageListVo<OcInfoEntity> list = ocRepository.findList(req);
        List<OcVo> voList = OcConvert.toVoList(list.getList());
        return PageListVo.of(voList, list.getTotal(), req.getPage(), req.getSize());
    }

    public OcVo detail(Long id) {
        OcInfoEntity entity = ocRepository.getReferenceById(id);
        if (entity.getState() == null || entity.getState().equals(OcStateEnum.DELETED.getValue())) {
            throw new BizException(StatusEnum.RECORDS_NOT_EXISTS, id);
        }

        return OcConvert.toVo(entity);
    }

    public boolean updateOc(OcSaveReq req) {
        OcInfoEntity entity = ocRepository.getReferenceById(req.getId());
        if (entity.getState() == null || entity.getState().equals(OcStateEnum.DELETED.getValue())) {
            throw new BizException(StatusEnum.RECORDS_NOT_EXISTS, req.getId());
        }

        // 做增量更新
        if (StringUtils.isNotBlank(req.getCompanyName())) {
            entity.setCompanyName(req.getCompanyName());
        }
        if (StringUtils.isNotBlank(req.getCompanyType())) {
            entity.setCompanyType(req.getCompanyType());
        }
        if (StringUtils.isNotBlank(req.getCompanyIndustry())) {
            entity.setCompanyIndustry(req.getCompanyIndustry());
        }
        if (StringUtils.isNotBlank(req.getJobLocation())) {
            entity.setJobLocation(req.getJobLocation());
        }
        if (StringUtils.isNotBlank(req.getRecruitmentType())) {
            entity.setRecruitmentType(req.getRecruitmentType());
        }
        if (StringUtils.isNotBlank(req.getRecruitmentTarget())) {
            entity.setRecruitmentTarget(req.getRecruitmentTarget());
        }
        if (StringUtils.isNotBlank(req.getPosition())) {
            entity.setPosition(req.getPosition());
        }
        if (StringUtils.isNotBlank(req.getLastUpdatedTime())) {
            entity.setLastUpdatedTime(DateUtil.toDateOrNow(req.getLastUpdatedTime()));
        }
        if (StringUtils.isNotBlank(req.getDeadline())) {
            entity.setDeadline(req.getDeadline());
        }
        if (StringUtils.isNotBlank(req.getRelatedLink())) {
            entity.setRelatedLink(req.getRelatedLink());
        }
        if (StringUtils.isNotBlank(req.getJobAnnouncement())) {
            entity.setJobAnnouncement(req.getJobAnnouncement());
        }
        if (StringUtils.isNotBlank(req.getInternalReferralCode())) {
            entity.setInternalReferralCode(req.getInternalReferralCode());
        }
        if (StringUtils.isNotBlank(req.getRemarks())) {
            entity.setRemarks(req.getRemarks());
        }
        if (req.getState() != null) {
            entity.setState(req.getState());
        }
        entity.setUpdateTime(new Date());
        ocRepository.saveAndFlush(entity);
        return true;
    }


    public boolean updateState(Long id, OcStateEnum state) {
        OcInfoEntity entity = ocRepository.getReferenceById(id);
        if (entity.getState() == null || entity.getState().equals(OcStateEnum.DELETED.getValue())) {
            throw new BizException(StatusEnum.RECORDS_NOT_EXISTS, id);
        }

        if (entity.getState().equals(state.getValue())) {
            return true;
        }
        entity.setState(state.getValue());
        entity.setUpdateTime(new Date());
        ocRepository.saveAndFlush(entity);
        return true;
    }


    public PageListVo<OcVo> recommendForUser(UserInterestRecommendReq req) {
        PageListVo<OcInfoEntity> list = ocRepository.recommend(req);
        List<OcVo> voList = OcConvert.toVoList(list.getList());
        return PageListVo.of(voList, list.getTotal(), req.getPage(), req.getSize());
    }

    @Override
    public PageListVo<OcJobInfo> recommend(UserInterestRecommendReq req) {
        PageListVo<OcInfoEntity> list = ocRepository.recommend(req);
        List<OcJobInfo> voList = new ArrayList<>(list.getList());
        return PageListVo.of(voList, list.getTotal(), req.getPage(), req.getSize());
    }
}
