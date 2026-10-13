package com.git.hui.jobclaw.web.model.res;

import com.git.hui.jobclaw.oc.dao.entity.OcDraftEntity;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * @date 2025/7/18
 */
@Data
@Accessors(chain = true)
public class GatherVo {
    private List<OcDraftEntity> insertList;
    private List<OcDraftEntity> updateList;
    private List<OcDraftEntity> unchangedList;
}
