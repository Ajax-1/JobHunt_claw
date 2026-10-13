package com.git.hui.jobclaw.web.model.req;

import com.git.hui.jobclaw.core.apis.PageReq;
import lombok.Data;

import java.util.Arrays;
import java.util.List;

/**
 * 草稿列表查询
 *
 * @date 2025/7/15
 */
@Data
public class DraftSearchReq extends PageReq {
    /**
     * 主键
     */
    private Long id;
    /**
     * 多个草稿主键，逗号分隔
     */
    private String draftIds;
    /**
     * 采集源 ID。
     */
    private Long sourceId;
    /**
     * 产出草稿的采集任务 ID。
     */
    private Long sourceTaskId;
    /**
     * 执行来源：admin / agent / system。
     */
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
     * 工作地点
     */
    private String jobLocation;
    /**
     * 招聘类型
     */
    private String recruitmentType;
    /**
     * 招聘对象
     */
    private String recruitmentTarget;
    /**
     * 岗位
     */
    private String position;
    /**
     * 岗位更新时间
     */
    private String lastUpdatedTimeAfter;

    private String lastUpdatedTimeBefore;
    /**
     * 状态:
     * -1 删除
     * 0 草稿
     * 1 已发布
     */
    private Integer state;

    private Integer notState;
    /**
     * 0 表示这条记录已处理
     * 1 表示这条数据待处理
     */
    private Integer toProcess;

    // AIDEV-NOTE: AI-GENERATED task draft filter
    public List<Long> parseDraftIds() {
        if (draftIds == null || draftIds.isBlank()) {
            return List.of();
        }
        return Arrays.stream(draftIds.split(","))
                .map(String::trim)
                .filter(item -> !item.isEmpty() && item.matches("\\d+"))
                .map(Long::parseLong)
                .distinct()
                .toList();
    }
}
