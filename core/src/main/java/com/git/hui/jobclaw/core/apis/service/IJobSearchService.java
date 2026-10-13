package com.git.hui.jobclaw.core.apis.service;

import com.git.hui.jobclaw.core.apis.PageListVo;
import com.git.hui.jobclaw.core.apis.models.OcJobInfo;
import com.git.hui.jobclaw.core.apis.req.UserInterestRecommendReq;

/**
 *
 * @date 2026/4/21
 */
public interface IJobSearchService {

    PageListVo<OcJobInfo> recommend(UserInterestRecommendReq req);

}
