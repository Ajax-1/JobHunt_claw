package com.git.hui.jobclaw.gather.model;

import com.git.hui.jobclaw.constants.gather.GatherModelEnum;
import com.git.hui.jobclaw.constants.gather.GatherModelTypeEnum;
import com.git.hui.jobclaw.core.utils.json.StringBaseEnum;

/**
 * 模型选择器
 *
 * @date 2025/7/30
 */
public record ModelSelectReq(GatherModelEnum mode, String model, GatherModelTypeEnum type) {
    public static ModelSelectReq of(GatherModelEnum mode, GatherModelTypeEnum type) {
        return new ModelSelectReq(mode, mode == null ? null : mode.getValue(), type);
    }

    public static ModelSelectReq of(String model, GatherModelTypeEnum type) {
        GatherModelEnum legacyMode = StringBaseEnum.getEnumByCode(GatherModelEnum.class, model);
        return new ModelSelectReq(legacyMode, model, type);
    }

    public boolean providerModel() {
        return model != null && model.contains("#");
    }
}
