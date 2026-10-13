package com.git.hui.jobclaw.core.apis.req;

import com.git.hui.jobclaw.core.apis.PageReq;
import lombok.Data;

import java.util.List;

/**
 * 用户偏好推荐请求
 *
 * @date 2025/8/22
 */
@Data
public class UserInterestRecommendReq extends PageReq {
    /**
     * 公司类型
     */
    private List<String> companyTypeList;
    /**
     * 公司行业
     */
    private List<String> companyIndustryList;
    /**
     * 工作地点
     */
    private List<String> jobLocationList;
    /**
     * 招聘类型
     */
    private List<String> recruitmentTypeList;
    /**
     * 岗位
     */
    private List<String> positionList;
    /**
     * 招聘对象
     */
    private String recruitmentTarget;
}
