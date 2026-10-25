package io.github.ajax1.campusradar.job;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 加字段后给老数据补值。ddl-auto=update 只会加列，不会填数据。
 */
@Component
public class JobDataBackfill {

    private static final Logger log = LoggerFactory.getLogger(JobDataBackfill.class);

    private final JobPostingRepository repository;

    public JobDataBackfill(JobPostingRepository repository) {
        this.repository = repository;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void backfill() {
        int n = repository.backfillPostedAt();
        if (n > 0) {
            log.info("补齐 postedAt {} 条", n);
        }
    }
}
