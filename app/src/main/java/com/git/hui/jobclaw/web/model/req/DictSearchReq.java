package com.git.hui.jobclaw.web.model.req;

import com.git.hui.jobclaw.core.apis.PageReq;
import lombok.Data;

/**
 * @date 2025/7/21
 */
@Data
public class DictSearchReq extends PageReq {
    private String app;
    private String key;
    private Integer state;
}
