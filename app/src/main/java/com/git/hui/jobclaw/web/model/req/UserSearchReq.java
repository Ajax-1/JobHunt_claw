package com.git.hui.jobclaw.web.model.req;

import com.git.hui.jobclaw.core.apis.PageReq;
import lombok.Data;

/**
 * @date 2025/7/17
 */
@Data
public class UserSearchReq extends PageReq {
    /**
     * 根据用户id查询
     */
    private Long userId;
    /**
     * 根据昵称查询
     */
    private String displayName;

    /**
     * 用户角色查询
     */
    private Integer role;
}
