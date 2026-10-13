package com.git.hui.jobclaw.core.apis.service;

import com.git.hui.jobclaw.core.apis.models.CommonDict;

import java.util.List;

/**
 *
 * @date 2026/4/21
 */
public interface ICommonDictService {

    List<CommonDict> queryByApp(String app);
}
