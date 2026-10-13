package com.git.hui.jobclaw.gather.model;

import com.git.hui.jobclaw.constants.gather.GatherTargetTypeEnum;
import org.springframework.web.multipart.MultipartFile;

/**
 * @date 2025/7/18
 */
public record GatherTaskSaveBo(GatherTargetTypeEnum type, String model, String content, MultipartFile file,
                               String runnerType) {
    public GatherTaskSaveBo(GatherTargetTypeEnum type, String model, String content, MultipartFile file) {
        this(type, model, content, file, null);
    }
}
