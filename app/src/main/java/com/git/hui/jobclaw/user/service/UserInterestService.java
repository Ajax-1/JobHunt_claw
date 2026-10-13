package com.git.hui.jobclaw.user.service;

import com.git.hui.jobclaw.core.apis.context.ReqInfoContext;
import com.git.hui.jobclaw.gather.service.ai.OcAiModelContext;
import com.git.hui.jobclaw.oc.helper.OcInfoTransfer;
import com.git.hui.jobclaw.user.convert.UserConvert;
import com.git.hui.jobclaw.user.dao.entity.UserInterestEntity;
import com.git.hui.jobclaw.user.dao.repository.UserInterestRepository;
import com.git.hui.jobclaw.user.model.UserInterestBo;
import com.git.hui.jobclaw.core.apis.PageReq;
import com.git.hui.jobclaw.core.apis.req.UserInterestRecommendReq;
import com.git.hui.jobclaw.web.model.res.UserInterestVo;
import com.google.common.base.Splitter;
import io.micrometer.common.util.StringUtils;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.util.Date;

/**
 * 用户订阅偏好服务
 *
 * @date 2025/8/21
 */
@Service
public class UserInterestService {

    private final OcAiModelContext aiModelFacade;

    private final UserInterestRepository userInterestRepository;

    private final OcInfoTransfer ocInfoTransfer;

    public UserInterestService(OcAiModelContext aiModelFacade, UserInterestRepository userInterestRepository, OcInfoTransfer ocInfoTransfer) {
        this.aiModelFacade = aiModelFacade;
        this.userInterestRepository = userInterestRepository;
        this.ocInfoTransfer = ocInfoTransfer;
    }

    /**
     * 提交用户的偏好信息
     *
     * @param text
     */
    public void submitInterest(String text) {
        ChatClient chatClient = this.aiModelFacade.getMainChatClient();
        // 根据用户传入的信息，提取需要关注的内容
        UserInterestBo userInterestBo = chatClient.prompt()
                .user("我现在是一个准备找工作的人，下面是我的个人信息，我希望从中提取出用于筛选招聘岗位的关键信息: \n" + text)
                .call().entity(UserInterestBo.class);
        // 查询之前的记录
        Long userId = ReqInfoContext.getReqInfo().getUserId();
        UserInterestEntity entity = userInterestRepository.findByUserId(userId);
        if (entity == null) {
            // 不存在时创建新的实体；若存在则执行更新
            entity = new UserInterestEntity();
            entity.setUserId(userId);
            entity.setCreateTime(new Date());
        }
        entity.setInterest(text)
                .setCompanyType(userInterestBo.companyType())
                .setCompanyIndustry(userInterestBo.companyIndustry())
                .setJobLocation(userInterestBo.jobLocation())
                .setRecruitmentType(userInterestBo.recruitmentType())
                .setRecruitmentTarget(userInterestBo.recruitmentTarget())
                .setPosition(userInterestBo.position())
                .setUpdateTime(new Date());

        // 数据清洗转换
        ocInfoTransfer.autoFormatDraftInfo(entity);
        userInterestRepository.saveAndFlush(entity);
    }

    public UserInterestVo getUserInterest(Long userId) {
        UserInterestEntity entity = userInterestRepository.findByUserId(userId);
        return UserConvert.toInterestVo(entity);
    }

    /**
     * 构建用户偏好推荐request
     *
     * @param page
     * @return
     */
    public UserInterestRecommendReq buildInterestRecommendReq(PageReq page) {
        Long userId = ReqInfoContext.getReqInfo().getUserId();
        UserInterestEntity entity = userInterestRepository.findByUserId(userId);
        if (entity == null) {
            return null;
        }

        page.autoInitPage();
        UserInterestRecommendReq req = new UserInterestRecommendReq();
        req.setPage(page.getPage());
        req.setSize(page.getSize());

        if (StringUtils.isNotBlank(entity.getCompanyType())) {
            req.setCompanyTypeList(Splitter.on(",").splitToList(entity.getCompanyType()));
        }
        if (StringUtils.isNotBlank(entity.getCompanyIndustry())) {
            req.setCompanyIndustryList(Splitter.on(",").splitToList(entity.getCompanyIndustry()));
        }
        if (StringUtils.isNotBlank(entity.getJobLocation())) {
            req.setJobLocationList(Splitter.on(",").splitToList(entity.getJobLocation()));
        }
        if (StringUtils.isNotBlank(entity.getRecruitmentType())) {
            req.setRecruitmentTypeList(Splitter.on(",").splitToList(entity.getRecruitmentType()));
        }
        if (StringUtils.isNotBlank(entity.getRecruitmentTarget())) {
            req.setRecruitmentTarget(entity.getRecruitmentTarget());
        }
        if (StringUtils.isNotBlank(entity.getPosition())) {
            req.setPositionList(Splitter.on("/").splitToList(entity.getPosition()));
        }
        return req;
    }
}
