package com.git.hui.jobclaw.core.apis.models;

import java.util.Date;

/**
 * 全局字典表接口
 *
 * @date 2025/7/21
 */
public interface CommonDict {
    /**
     * 获取字典ID
     *
     * @return 字典ID
     */
    Long getId();

    /**
     * 获取应用标识
     *
     * @return 应用标识，如：oc、system等
     */
    String getApp();

    /**
     * 获取作用域
     *
     * @return 作用域，0-全局，1-用户级
     */
    Integer getScope();

    /**
     * 获取字典键
     *
     * @return 字典键
     */
    String getKey();

    /**
     * 获取字典值
     *
     * @return 字典值
     */
    String getValue();

    /**
     * 获取字典说明
     *
     * @return 字典说明/介绍
     */
    String getIntro();

    /**
     * 获取备注信息
     *
     * @return 备注信息
     */
    String getRemark();

    /**
     * 获取状态
     *
     * @return 状态：0-禁用，1-启用
     */
    Integer getState();

    /**
     * 获取创建时间
     *
     * @return 创建时间
     */
    Date getCreateTime();

    /**
     * 获取更新时间
     *
     * @return 更新时间
     */
    Date getUpdateTime();
}
