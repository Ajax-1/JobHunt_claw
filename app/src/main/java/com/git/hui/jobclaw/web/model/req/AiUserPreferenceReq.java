package com.git.hui.jobclaw.web.model.req;

import com.git.hui.jobclaw.web.model.res.AiUserPreferenceVo;
import lombok.Data;

import java.util.List;

/**
 * 用户偏好配置
 * @date 2026/4/23
 */
@Data
public class AiUserPreferenceReq {

    private String collector;
    private List<String> channels;
    private AiUserPreferenceVo.UserPreferenceModelVo models;
    // 用户更新哪个provider就是指定更新哪个
    private AiUserPreferenceVo.UserProviderConfigVo provider;
    // true 表示删除这个 provider
    private boolean deleteProvider;
    private AiUserPreferenceVo.ModelConfigVo model;
    // true 删除这个 model
    private boolean deleteModel;
}
