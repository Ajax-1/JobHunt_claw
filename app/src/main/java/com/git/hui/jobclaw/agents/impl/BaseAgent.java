package com.git.hui.jobclaw.agents.impl;

import com.git.hui.jobclaw.core.apis.context.ReqInfoContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;

/**
 * @date 2025/8/13
 */
@Slf4j
public abstract class BaseAgent {
    /**
     * 返回唯一的AgentName
     *
     * @return
     */
    public abstract String agentName();

    /**
     * 统一的日志打印
     *
     * @param template
     * @param input
     */
    public void log(String template, Object... input) {
        if (log.isDebugEnabled()) {
            log.debug("[{}]: " + template, agentName(), input);
        }
    }

    public void printAgentStartLog(Object req) {
        log("in query: {}", req);
        sendInfo("start", req);
    }

    public void printAgentEndLog(Object res) {
        log("out res: {}", res);
        sendInfo("end", res);
    }

    public void sendInfo(String cmd, Object info) {
        ReqInfoContext.ReqInfo reqInfo = ReqInfoContext.getReqInfo();
        if (reqInfo == null) {
            return;
        }
        try {
            Object emitter = reqInfo.getContextVar(ReqInfoContext.REQ_INFO_KEY);
            if (emitter instanceof SseEmitter sseEmitter) {
                sseEmitter.send(Map.of("agent", agentName(), "cmd", cmd, "info", info));
            }
        } catch (IOException e) {
            log.error("同步任务信息给前端异常! cmd:{}, info:{}", cmd, info, e);
        }
    }
}
