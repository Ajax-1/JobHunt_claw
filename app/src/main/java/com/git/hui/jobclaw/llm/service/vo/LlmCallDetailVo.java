package com.git.hui.jobclaw.llm.service.vo;

import lombok.Data;
import java.util.List;

@Data
public class LlmCallDetailVo {
    private LlmCallVo invocation;
    private List<LlmRequestVo> requests;
}
