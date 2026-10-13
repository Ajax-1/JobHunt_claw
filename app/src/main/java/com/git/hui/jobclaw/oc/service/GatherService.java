package com.git.hui.jobclaw.oc.service;

import com.git.hui.jobclaw.agents.jobfetch.service.JobInfoPersistService;
import com.git.hui.jobclaw.agents.jobfetch.service.model.FetchedDraftEntity;
import com.git.hui.jobclaw.agents.jobfetch.service.model.FetchedJobInfo;
import com.git.hui.jobclaw.constants.oc.DraftProcessEnum;
import com.git.hui.jobclaw.constants.oc.DraftStateEnum;
import com.git.hui.jobclaw.constants.oc.OcStateEnum;
import com.git.hui.jobclaw.gather.model.GatherDraftContext;
import com.git.hui.jobclaw.oc.convert.DraftConvert;
import com.git.hui.jobclaw.oc.dao.entity.OcDraftEntity;
import com.git.hui.jobclaw.oc.dao.entity.OcInfoEntity;
import com.git.hui.jobclaw.oc.dao.repository.OcDraftRepository;
import com.git.hui.jobclaw.oc.dao.repository.OcRepository;
import com.git.hui.jobclaw.oc.helper.OcInfoTransfer;
import com.git.hui.jobclaw.core.apis.PageListVo;
import com.git.hui.jobclaw.web.model.req.DraftOcUpdateReq;
import com.git.hui.jobclaw.web.model.req.DraftSearchReq;
import com.git.hui.jobclaw.web.model.res.GatherVo;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * @date 2025/7/18
 */
@Slf4j
@Service
public class GatherService implements JobInfoPersistService {

    private final OcDraftRepository draftRepository;
    private final OcRepository ocRepository;

    private final OcInfoTransfer ocInfoTransfer;

    @Autowired
    public GatherService(OcDraftRepository repository, OcRepository ocRepository, OcInfoTransfer ocInfoTransfer) {
        this.draftRepository = repository;
        this.ocRepository = ocRepository;
        this.ocInfoTransfer = ocInfoTransfer;
    }

    public PageListVo<OcDraftEntity> searchDraftList(DraftSearchReq req) {
        if (req.getState() == null) {
            req.setNotState(OcStateEnum.DELETED.getValue());
        }
        req.autoInitPage();
        return draftRepository.findList(req);
    }


    @Override
    public List<FetchedDraftEntity> listToBePublished(int size) {
        // 从db中，找到最近一个需要审核的数据
        DraftSearchReq req = new DraftSearchReq();
        req.setPage(1);
        req.setSize(size);
        req.setState(DraftStateEnum.DRAFT.getValue());
        req.setToProcess(DraftProcessEnum.UNPROCESS.getValue());
        PageListVo<OcDraftEntity> res = draftRepository.findList(req);
        return new ArrayList<>(res.getList());
    }

    @Override
    public boolean updateDraft(long id, FetchedJobInfo jobInfo) {
        OcDraftEntity entity = draftRepository.findById(id).orElse(null);
        if (entity != null) {
            // 覆盖数据，只有jobInfo的字段有数据时，才进行覆盖
            if (!StringUtils.isBlank(jobInfo.getCompanyName())) {
                entity.setCompanyName(jobInfo.getCompanyName());
            }
            if (!StringUtils.isBlank(jobInfo.getCompanyType())) {
                entity.setCompanyType(jobInfo.getCompanyType());
            }
            if (!StringUtils.isBlank(jobInfo.getCompanyIndustry())) {
                entity.setCompanyIndustry(jobInfo.getCompanyIndustry());
            }
            if (!StringUtils.isBlank(jobInfo.getJobLocation())) {
                entity.setJobLocation(jobInfo.getJobLocation());
            }
            if (!StringUtils.isBlank(jobInfo.getRecruitmentType())) {
                entity.setRecruitmentType(jobInfo.getRecruitmentType());
            }
            if (!StringUtils.isBlank(jobInfo.getRecruitmentTarget())) {
                entity.setRecruitmentTarget(jobInfo.getRecruitmentTarget());
            }
            if (!StringUtils.isBlank(jobInfo.getPosition())) {
                entity.setPosition(jobInfo.getPosition());
            }
            if (!StringUtils.isBlank(jobInfo.getSalary())) {
                entity.setSalary(jobInfo.getSalary());
            }
            if (!StringUtils.isBlank(jobInfo.getEducation())) {
                entity.setEducation(jobInfo.getEducation());
            }
            if (!StringUtils.isBlank(jobInfo.getExperience())) {
                entity.setExperience(jobInfo.getExperience());
            }
            if (!StringUtils.isBlank(jobInfo.getDeliveryProgress())) {
                entity.setDeliveryProgress(jobInfo.getDeliveryProgress());
            }
            if (!StringUtils.isBlank(jobInfo.getLastUpdatedTime())) {
                entity.setLastUpdatedTime(jobInfo.getLastUpdatedTime());
            }
            if (!StringUtils.isBlank(jobInfo.getDeadline())) {
                entity.setDeadline(jobInfo.getDeadline());
            }
            if (!StringUtils.isBlank(jobInfo.getRelatedLink())) {
                entity.setRelatedLink(jobInfo.getRelatedLink());
            }
            if (!StringUtils.isBlank(jobInfo.getJobAnnouncement())) {
                entity.setJobAnnouncement(jobInfo.getJobAnnouncement());
            }
            if (!StringUtils.isBlank(jobInfo.getInternalReferralCode())) {
                entity.setInternalReferralCode(jobInfo.getInternalReferralCode());
            }
            if (!StringUtils.isBlank(jobInfo.getRemarks())) {
                entity.setRemarks(jobInfo.getRemarks());
            }

            entity.setUpdateTime(new Date());
            entity.autoInitVal();
            draftRepository.save(entity);
            return true;
        }
        return false;
    }

    @Override
    public SaveRes save(List<FetchedJobInfo> jobInfos) {
        if (jobInfos == null || jobInfos.isEmpty()) {
            return new SaveRes(0, 0);
        }
        List<OcDraftEntity> draftList = jobInfos.stream().map(DraftConvert::covert).collect(Collectors.toList());
        GatherVo vo = saveDraftDataList(draftList);
        return new SaveRes(vo.getInsertList().size(), vo.getUpdateList().size());
    }

    @Override
    @Transactional
    public int publishDrafts(List<Long> draftIds) {
        if (draftIds == null || draftIds.isEmpty()) {
            return 0;
        }
        List<OcInfoEntity> published = moveToOc(draftIds);
        return published.size();
    }

    /**
     * 保存草稿数据列表
     * 此方法首先检查数据库中是否已存在相同的数据条目，以避免重复存储
     * 如果数据条目不存在，则将其状态设置为草稿，并标记为未处理，然后保存到数据库
     * 如果数据条目已存在，则更新其状态为未处理，并更新最后修改时间
     *
     * @param dataList 要保存的草稿数据列表，包含多个GatherOcDraftEntity对象
     */
    public GatherVo saveDraftDataList(List<OcDraftEntity> dataList) {
        return saveDraftDataList(dataList, GatherDraftContext.empty());
    }

    public GatherVo saveDraftDataList(List<OcDraftEntity> dataList, GatherDraftContext context) {
        // 以公司名称 + 工作地点 + 更新时间 + 岗位 作为去重条件
        // 保存数据之前，需要先从数据库和自身的情况，做一个去重
        dataList = removeSameDraftItem(dataList);

        // 基于db的数据，判断哪些是更新，哪些是插入
        List<OcDraftEntity> insertList = new ArrayList<>();
        List<OcDraftEntity> updateList = new ArrayList<>();
        List<OcDraftEntity> unchangedList = new ArrayList<>();
        for (OcDraftEntity data : dataList) {
            List<OcDraftEntity> existingRecords = draftRepository.findByUniqueKey(
                    data.getCompanyName(),
                    data.getJobLocation(),
                    data.getLastUpdatedTime(),
                    data.getPosition()
            );

            if (existingRecords.isEmpty()) {
                // 没有重复数据，设置必要的状态和时间字段后，新增数据
                data.setToProcess(DraftProcessEnum.UNPROCESS.getValue());
                data.setState(DraftStateEnum.DRAFT.getValue());
                data.setCreateTime(new Date());
                data.setUpdateTime(new Date());
                attachDraftContext(data, context);
                data.autoInitVal();
                draftRepository.save(data);
                insertList.add(data);
            } else {
                // 存在重复数据，更新已有的记录
                for (OcDraftEntity existing : existingRecords) {
                    data.autoInitVal();
                    existing.autoInitVal();
                    if (!isDraftChanged(existing, data)) {
                        attachDraftContext(existing, context);
                        if (context != null && (context.sourceId() != null || context.taskId() != null)) {
                            draftRepository.save(existing);
                        }
                        unchangedList.add(existing);
                        continue;
                    }
                    // 更新数据ID，保持状态，但将处理状态改为未处理
                    data.setId(existing.getId());
                    data.setState(existing.getState());
                    data.setToProcess(DraftProcessEnum.UNPROCESS.getValue());
                    data.setCreateTime(existing.getCreateTime());
                    data.setUpdateTime(new Date());
                    attachDraftContext(data, context);
                    data.autoInitVal();
                    // 保存更新后的记录
                    draftRepository.save(data);
                    updateList.add(data);
                }
            }
        }
        draftRepository.flush();
        return new GatherVo().setInsertList(insertList).setUpdateList(updateList).setUnchangedList(unchangedList);
    }

    private void attachDraftContext(OcDraftEntity draft, GatherDraftContext context) {
        if (context == null) {
            return;
        }
        if (context.sourceId() != null) {
            draft.setSourceId(context.sourceId());
        }
        if (context.taskId() != null) {
            draft.setSourceTaskId(context.taskId());
        }
        draft.setRunnerType(context.normalizedRunnerType());
    }

    private boolean isDraftChanged(OcDraftEntity existing, OcDraftEntity next) {
        return !Objects.equals(existing.getCompanyName(), next.getCompanyName())
                || !Objects.equals(existing.getCompanyType(), next.getCompanyType())
                || !Objects.equals(existing.getCompanyIndustry(), next.getCompanyIndustry())
                || !Objects.equals(existing.getJobLocation(), next.getJobLocation())
                || !Objects.equals(existing.getRecruitmentType(), next.getRecruitmentType())
                || !Objects.equals(existing.getRecruitmentTarget(), next.getRecruitmentTarget())
                || !Objects.equals(existing.getPosition(), next.getPosition())
                || !Objects.equals(existing.getDeliveryProgress(), next.getDeliveryProgress())
                || !Objects.equals(existing.getLastUpdatedTime(), next.getLastUpdatedTime())
                || !Objects.equals(existing.getDeadline(), next.getDeadline())
                || !Objects.equals(existing.getRelatedLink(), next.getRelatedLink())
                || !Objects.equals(existing.getJobAnnouncement(), next.getJobAnnouncement())
                || !Objects.equals(existing.getInternalReferralCode(), next.getInternalReferralCode())
                || !Objects.equals(existing.getRemarks(), next.getRemarks())
                || !Objects.equals(existing.getSalary(), next.getSalary())
                || !Objects.equals(existing.getEducation(), next.getEducation())
                || !Objects.equals(existing.getExperience(), next.getExperience());
    }

    private List<OcDraftEntity> removeSameDraftItem(List<OcDraftEntity> dataList) {
        List<OcDraftEntity> list = new ArrayList<>(dataList.size());
        Set<String> sets = new HashSet<>(dataList.size());
        for (OcDraftEntity item : dataList) {
            String key = item.getCompanyName() + item.getJobLocation() + item.getLastUpdatedTime() + item.getPosition();
            if (sets.contains(key)) {
                continue;
            }
            sets.add(key);
            list.add(item);
        }
        return list;

        // 不使用下面迭代器的方式，因为传入的 dataList 可能是不可变的列表
//        Iterator<OcDraftEntity> iterator = dataList.iterator();
//        while (iterator.hasNext()) {
//            OcDraftEntity data = iterator.next();
//            String key = data.getCompanyName() + data.getJobLocation() + data.getLastUpdatedTime() + data.getPosition();
//            if (sets.contains(key)) {
//                // 已经存在，则移除当前这一个
//                iterator.remove();
//            } else {
//                sets.add(key);
//            }
//        }
    }

    @Transactional
    public boolean deleteDraft(Long draftId) {
        return draftRepository.updateStateById(draftId, DraftStateEnum.DELETED.getValue()) > 0;
    }

    /**
     * 更新草稿状态
     * 此方法首先尝试根据提供的ID查找现有的草稿对象如果找不到，则创建一个新的草稿对象并设置创建时间
     * 无论找到与否，都将使用请求对象中的属性更新草稿对象的属性最后，保存更新后的草稿对象到数据库
     *
     * @param req 包含要更新的草稿信息的请求对象
     * @return 返回更新操作是否成功的结果
     */
    public boolean updateDraft(DraftOcUpdateReq req) {
        OcDraftEntity draft = draftRepository.findById(req.getId()).orElse(null);
        if (draft == null) {
            // req -> entity
            draft = new OcDraftEntity();
            BeanUtils.copyProperties(req, draft);
            draft.setCreateTime(new Date());
            draft.setToProcess(DraftProcessEnum.UNPROCESS.getValue());
        } else {
            // 执行更新，将待更新字段设置为true
            BeanUtils.copyProperties(req, draft);
            draft.setToProcess(DraftProcessEnum.UNPROCESS.getValue());
        }
        draft.setUpdateTime(new Date());
        draftRepository.saveAndFlush(draft);
        return true;
    }

    /**
     * 将草稿数据移动到正式数据中
     *
     * @param draftIds
     */
    @Transactional
    public List<OcInfoEntity> moveToOc(List<Long> draftIds) {
        // 1. 获取草稿数据
        List<OcDraftEntity> draftData = draftRepository.findAllById(draftIds);

        // 2. 从正式库中，查询草稿数据，对于已经已经存在的，使用更细；不存在的，使用插入
        List<OcInfoEntity> ocDatas = ocRepository.findByDraftIdInAndStateNot(draftIds, OcStateEnum.DELETED.getValue());
        Map<Long, OcInfoEntity> ocMap = ocDatas.stream().collect(Collectors.toMap(OcInfoEntity::getDraftId, v -> v));

        List<OcInfoEntity> insertList = new ArrayList<>();
        List<OcInfoEntity> updateList = new ArrayList<>();
        draftData.forEach(draft -> {
            OcInfoEntity ocEntity = ocInfoTransfer.autoFormatDraftInfo(draft);
            ocEntity.setState(OcStateEnum.PUBLISHED.getValue());
            if (ocMap.containsKey(draft.getId())) {
                // 更新
                ocEntity.setId(ocMap.get(draft.getId()).getId());
                updateList.add(ocEntity);
            } else {
                // 插入
                insertList.add(ocEntity);
            }
        });

        // 3. 批量插入
        ocRepository.saveAll(insertList);

        // 4. 批量更新
        ocRepository.saveAll(updateList);
        ocRepository.flush();

        // 5. 将草稿中的数据状态更新为已处理
        draftRepository.updateProcessAndStateByIds(draftIds, DraftProcessEnum.PROCEED.getValue(), DraftStateEnum.PUBLISHED.getValue());
        draftRepository.flush();

        insertList.addAll(updateList);
        return insertList;
    }
}
