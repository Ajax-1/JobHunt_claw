package com.git.hui.jobclaw.web.model.res;

import java.util.List;

/**
 * 前台使用的字典列表
 *
 * @date 2025/7/21
 */
public record CommonDictVo(String app, List<DictItemVo> items) {
}
