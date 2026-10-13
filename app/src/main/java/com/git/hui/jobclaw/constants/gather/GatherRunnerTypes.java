package com.git.hui.jobclaw.constants.gather;

/**
 * 采集任务的执行来源。
 */
public final class GatherRunnerTypes {
    public static final String ADMIN = "admin";
    public static final String AGENT = "agent";
    public static final String SYSTEM = "system";

    private GatherRunnerTypes() {
    }

    public static String normalize(String runnerType) {
        if (runnerType == null || runnerType.isBlank()) {
            return ADMIN;
        }
        String value = runnerType.trim().toLowerCase();
        if (AGENT.equals(value) || SYSTEM.equals(value)) {
            return value;
        }
        return ADMIN;
    }
}
