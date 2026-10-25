package io.github.ajax1.campusradar.api;

import io.github.ajax1.campusradar.collect.CompanyDirectory;
import io.github.ajax1.campusradar.collect.SourceStatus;
import io.github.ajax1.campusradar.collect.SourceStatusService;
import io.github.ajax1.campusradar.job.JobQuery;
import io.github.ajax1.campusradar.job.JobQueryService;
import io.github.ajax1.campusradar.job.JobViews;
import io.github.ajax1.campusradar.job.RecruitType;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api")
public class JobController {

    private final JobQueryService queryService;
    private final SourceStatusService sourceStatusService;
    private final CompanyDirectory companies;

    public JobController(JobQueryService queryService, SourceStatusService sourceStatusService,
                         CompanyDirectory companies) {
        this.queryService = queryService;
        this.sourceStatusService = sourceStatusService;
        this.companies = companies;
    }

    @GetMapping("/jobs")
    public List<JobViews.Summary> search(@RequestParam(required = false) String keyword,
                                         @RequestParam(required = false) String company,
                                         @RequestParam(required = false) String city,
                                         @RequestParam(required = false) String recruitType,
                                         @RequestParam(required = false) Integer gradYear,
                                         @RequestParam(required = false) Integer sinceDays,
                                         @RequestParam(defaultValue = "20") int limit) {
        String resolved = null;
        if (company != null && !company.isBlank()) {
            resolved = companies.resolve(company).orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "没有收录公司「" + company + "」。已收录：" + String.join("、", companies.companies())));
        }
        return queryService.search(new JobQuery(keyword, resolved, city, RecruitType.parse(recruitType), gradYear,
                sinceDays, limit));
    }

    @GetMapping("/jobs/{id}")
    public ResponseEntity<JobViews.Detail> get(@PathVariable long id) {
        return ResponseEntity.of(queryService.get(id));
    }

    @GetMapping("/sources")
    public List<SourceStatus> sources() {
        return sourceStatusService.list();
    }
}
