package com.git.hui.jobclaw.constants.common;

import com.git.hui.jobclaw.core.utils.json.IntBaseEnum;
import lombok.Getter;

/**
 * @date 2025/7/16
 */
@Getter
public enum BaseStateEnum implements IntBaseEnum {
    DELETED_STATE(-1, "已删除"),
    DISABLE_STATE(0, "禁用"),
    NORMAL_STATE(1, "正常"),
    ;

    private Integer value;
    private String desc;

    BaseStateEnum(Integer value, String desc) {
        this.value = value;
        this.desc = desc;
    }
}
