package com.git.hui.jobclaw.core.apis;

import lombok.Data;

/**
 * @date 2025/7/15
 */
@Data
public class PageReq {
    private Integer page;
    private Integer size;

    public void autoInitPage() {
        if (page == null || page <= 0) {
            page = 1;
        }
        if (size == null) {
            size = 10;
        }
    }
}
