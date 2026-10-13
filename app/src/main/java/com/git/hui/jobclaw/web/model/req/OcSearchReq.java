package com.git.hui.jobclaw.web.model.req;

import com.git.hui.jobclaw.core.apis.PageReq;
import lombok.Data;

/**
 * @date 2025/7/15
 */
@Data
public class OcSearchReq extends PageReq {
    private Long id;
    private Long sourceId;
    private Long sourceTaskId;
    private String runnerType;
    /**
     * 公司名称
     */
    private String companyName;
    /**
     * 公司类型
     */
    private String companyType;
    /**
     * 公司行业
     */
    private String companyIndustry;
    /**
     * 工作地点
     */
    private String jobLocation;
    /**
     * 招聘类型
     */
    private String recruitmentType;

    /**
     * 招聘类型，排除在外（如只想找校招相关的，排除掉实习信息）
     */
    private String recruitmentTypeExcept;

    /**
     * 招聘对象
     */
    private String recruitmentTarget;
    /**
     * 岗位
     */
    private String position;
    /**
     * 投递进度
     */
    private String deliveryProgress;
    /**
     * 岗位更新时间，时间戳
     */
    private Long lastUpdatedTimeAfter;

    private Long lastUpdatedTimeBefore;
    /**
     * 状态:
     * -1 删除
     * 0 草稿
     * 1 已发布
     */
    private Integer state;

    private Integer notState;
}
