package com.git.hui.jobclaw.web.model.req;

import com.git.hui.jobclaw.core.apis.PageReq;
import lombok.Data;

@Data
public class GatherSourceSearchReq extends PageReq {
    private Long sourceId;
    private Integer type;
    private String ownerType;
    private String status;
    private String keyword;
}
