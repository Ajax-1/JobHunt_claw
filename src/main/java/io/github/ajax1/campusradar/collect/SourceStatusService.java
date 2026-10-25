package io.github.ajax1.campusradar.collect;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class SourceStatusService {

    private final CollectService collectService;
    private final CollectRunRepository runs;
    private final JobUpsertService jobs;

    public SourceStatusService(CollectService collectService, CollectRunRepository runs, JobUpsertService jobs) {
        this.collectService = collectService;
        this.runs = runs;
        this.jobs = jobs;
    }

    public List<SourceStatus> list() {
        return collectService.sources().stream().map(source -> {
            JobCollector c = collectService.collector(source).orElseThrow();
            return runs.findFirstBySourceOrderByStartedAtDesc(source)
                    .map(r -> new SourceStatus(source, c.company(), jobs.countOpen(source), r.getStatus().name(),
                            r.getStartedAt(), r.getFetched(), r.getMessage()))
                    .orElseGet(() -> new SourceStatus(source, c.company(), jobs.countOpen(source), null, null, null,
                            "尚未采集"));
        }).toList();
    }
}
