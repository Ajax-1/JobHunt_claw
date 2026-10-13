package com.git.hui.jobclaw.web.model.res;

import lombok.Data;
import lombok.experimental.Accessors;

/**
 * @date 2025/7/17
 */
@Data
@Accessors(chain = true)
public class UserVo {
    private Long userId;
    private String displayName;
    private String avatar;
    private String wxId;
    private Integer role;
    private Integer state;
    private String email;
    private String intro;
    // vip用户的有效期
    private Long expireTime;
    private Long createTime;
    private Long updateTime;

    private String dingDingId;

    private String feiShuId;

    /**
     * mcp配置信息
     */
    private McpConfigVo config;

    /**
     * 用户的兴趣信息
     */
    private UserInterestVo interest;
}
