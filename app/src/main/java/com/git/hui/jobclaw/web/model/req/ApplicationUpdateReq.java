package com.git.hui.jobclaw.web.model.req;

/** 编辑时提交三个字段；空备注、空日期分别清除原有内容。 */
public record ApplicationUpdateReq(String status, String notes, String nextFollowUpDate) {
}
