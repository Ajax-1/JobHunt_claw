package com.git.hui.jobclaw.gather.convert;

import com.git.hui.jobclaw.gather.dao.entity.GatherSourceEntity;
import com.git.hui.jobclaw.web.model.res.GatherSourceVo;

import java.util.List;

public class GatherSourceConvert {
    public static GatherSourceVo toVo(GatherSourceEntity source) {
        return new GatherSourceVo()
                .setSourceId(source.getId())
                .setType(source.getType())
                .setTitle(source.getTitle())
                .setContent(source.getContent())
                .setSourceHash(source.getSourceHash())
                .setContentHash(source.getContentHash())
                .setVersion(source.getVersion())
                .setModel(source.getModel())
                .setOwnerType(source.getOwnerType())
                .setRunnerMode(source.getRunnerMode())
                .setStatus(source.getStatus())
                .setLastTaskId(source.getLastTaskId())
                .setLastResultSummary(source.getLastResultSummary())
                .setLastInsertCount(source.getLastInsertCount())
                .setLastUpdateCount(source.getLastUpdateCount())
                .setLastUnchangedCount(source.getLastUnchangedCount())
                .setLastFailedCount(source.getLastFailedCount())
                .setLastSuccessTime(source.getLastSuccessTime() == null ? null : source.getLastSuccessTime().getTime())
                .setConsecutiveFailCount(source.getConsecutiveFailCount())
                .setCreateTime(source.getCreateTime() == null ? null : source.getCreateTime().getTime())
                .setUpdateTime(source.getUpdateTime() == null ? null : source.getUpdateTime().getTime());
    }

    public static List<GatherSourceVo> toVo(List<GatherSourceEntity> sources) {
        if (sources == null || sources.isEmpty()) {
            return List.of();
        }
        return sources.stream().map(GatherSourceConvert::toVo).toList();
    }
}
