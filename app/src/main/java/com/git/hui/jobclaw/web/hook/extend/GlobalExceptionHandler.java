package com.git.hui.jobclaw.web.hook.extend;

import cn.hutool.core.exceptions.ExceptionUtil;
import com.git.hui.jobclaw.core.bizexception.BizException;
import com.git.hui.jobclaw.core.apis.context.ReqInfoContext;
import com.git.hui.jobclaw.core.apis.ResVo;
import com.git.hui.jobclaw.core.agent.extraction.ExtractionReport;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 全局异常处理
 *
 * @date 2025/7/15
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /** AI-GENERATED AIDEV-NOTE: 提取失败返回逐条结果，不把部分数据包装成成功。 */
    @ExceptionHandler(ExtractionReport.IncompleteExtractionException.class)
    public ResVo<ExtractionReport<?>> handleIncompleteExtraction(ExtractionReport.IncompleteExtractionException e) {
        return ResVo.fail(422, e.getMessage(), e.getReport());
    }

    /**
     * 业务异常
     *
     * @param e
     * @return
     */
    @ExceptionHandler(value = BizException.class)
    public ResVo<String> handleBizException(BizException e) {
        return ResVo.fail(e.getCode(), e.getMsg());
    }

    /**
     * 非预期异常
     *
     * @param e
     * @return
     */
    @ExceptionHandler(value = Exception.class)
    public ResVo<String> handleException(Exception e) {
        log.warn("非预期异常 {}", ReqInfoContext.getReqInfo(), e);
        return ResVo.fail(500, "服务器开小差，请稍后再试试吧~", ExceptionUtil.stacktraceToString(e));
    }
}
