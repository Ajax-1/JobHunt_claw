package com.git.hui.jobclaw.application.model;

/** 个人求职进度，与招聘方的岗位状态分别维护。 */
public enum ApplicationStatus {
    SAVED,
    APPLIED,
    INTERVIEW,
    OFFER,
    REJECTED,
    WITHDRAWN
}
