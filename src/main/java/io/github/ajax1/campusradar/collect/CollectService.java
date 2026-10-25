package io.github.ajax1.campusradar.collect;

import io.github.ajax1.campusradar.RadarProperties;
import io.github.ajax1.campusradar.job.JobPosting;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 跑一个数据源的完整采集：拉列表 → 补详情 → 入库 → 下线消失的岗位 → 健康检查
 */
@Service
public class CollectService {

    private static final Logger log = LoggerFactory.getLogger(CollectService.class);

    /**
     * 本轮数量低于上次成功采集的这个比例，判定为异常：标 WARN，并且不做下线，防止误删
     */
    static final double DROP_THRESHOLD = 0.5;

    private final Map<String, JobCollector> collectors;
    private final JobUpsertService upsert;
    private final CollectRunRepository runs;
    private final RadarProperties.Collect config;
    private final Map<String, Boolean> running = new ConcurrentHashMap<>();

    @Autowired
    public CollectService(CollectorRegistry registry, JobUpsertService upsert, CollectRunRepository runs,
                          RadarProperties properties) {
        this(registry.all(), upsert, runs, properties);
    }

    public CollectService(List<JobCollector> collectors, JobUpsertService upsert, CollectRunRepository runs,
                          RadarProperties properties) {
        this.collectors = collectors.stream()
                .collect(Collectors.toMap(JobCollector::source, Function.identity(), (a, b) -> a, LinkedHashMap::new));
        this.upsert = upsert;
        this.runs = runs;
        this.config = properties.collect();
    }

    public List<String> sources() {
        return List.copyOf(collectors.keySet());
    }

    public Optional<JobCollector> collector(String source) {
        return Optional.ofNullable(collectors.get(source));
    }

    /**
     * 依次采集所有启用的数据源；单个来源失败不影响其他来源
     */
    public List<CollectRun> runAll() {
        List<CollectRun> out = new ArrayList<>();
        for (String source : collectors.keySet()) {
            if (config.disabledSources().contains(source)) {
                continue;
            }
            out.add(run(source));
        }
        return out;
    }

    public CollectRun run(String source) {
        JobCollector collector = collectors.get(source);
        if (collector == null) {
            throw new IllegalArgumentException("未知数据源: " + source);
        }
        if (running.putIfAbsent(source, Boolean.TRUE) != null) {
            throw new IllegalStateException(source + " 正在采集中");
        }
        try {
            return doRun(collector);
        } finally {
            running.remove(source);
        }
    }

    private CollectRun doRun(JobCollector collector) {
        String source = collector.source();
        Instant start = Instant.now();
        CollectRun run = runs.save(new CollectRun(source, start));
        HttpFetcher http = new HttpFetcher(config.requestIntervalMs());
        HttpFetcher detailHttp = collector.needsDetail() ? new HttpFetcher(config.detailIntervalMs()) : null;
        try {
            List<RawJob> raws = dedupe(collector.collect(http));
            int stale = 0;
            for (RawJob raw : raws) {
                RawJob full = raw;
                if (detailHttp != null) {
                    full = withDetail(collector, raw, detailHttp, run);
                    if (full == null) {
                        stale++;
                        continue;
                    }
                }
                run.count(upsert.upsert(source, collector.company(), full, start));
            }
            run.setFetched(raws.size() - stale);
            run.setRequests(http.requestCount() + (detailHttp == null ? 0 : detailHttp.requestCount()));

            long previous = runs.findFirstBySourceAndStatusInOrderByStartedAtDesc(source,
                            List.of(CollectRun.Status.SUCCESS, CollectRun.Status.WARN))
                    .filter(r -> !r.getId().equals(run.getId()))
                    .map(CollectRun::getFetched)
                    .orElse(0);
            if (previous > 0 && raws.size() < previous * DROP_THRESHOLD) {
                run.finish(CollectRun.Status.WARN, "本轮 " + raws.size() + " 条，上次 " + previous
                        + " 条，疑似采集不完整，跳过下线");
            } else if (raws.isEmpty()) {
                run.finish(CollectRun.Status.WARN, "本轮没有采到任何岗位，跳过下线");
            } else {
                run.setClosed(upsert.closeNotSeenSince(source, start));
                run.finish(CollectRun.Status.SUCCESS, run.detailNote());
            }
        } catch (RuntimeException e) {
            log.warn("采集失败 source={}", source, e);
            run.finish(CollectRun.Status.FAILED, e.getMessage());
        }
        CollectRun saved = runs.save(run);
        log.info("采集结束 source={} status={} fetched={} created={} updated={} closed={} requests={} msg={}",
                source, saved.getStatus(), saved.getFetched(), saved.getCreated(), saved.getUpdated(),
                saved.getClosed(), saved.getRequests(), saved.getMessage());
        return saved;
    }

    /**
     * 只对新岗位或之前没拿到 JD 的岗位拉详情；已有 JD 的沿用库里的内容，避免每天把详情全部重拉一遍。
     * 返回 null 表示岗位已失效。
     */
    private RawJob withDetail(JobCollector collector, RawJob raw, HttpFetcher http, CollectRun run) {
        Optional<JobPosting> existing = upsert.find(collector.source(), raw.externalId());
        if (existing.isPresent() && existing.get().getDescription() != null) {
            JobPosting e = existing.get();
            return raw.withDetail(e.getCategory(), null, e.getDescription(), e.getRequirement());
        }
        try {
            return collector.fetchDetail(raw, http);
        } catch (RuntimeException e) {
            run.detailFailed(e.getMessage());
            log.info("详情拉取失败 source={} id={}: {}", collector.source(), raw.externalId(), e.getMessage());
            return raw;
        }
    }

    private static List<RawJob> dedupe(List<RawJob> raws) {
        Map<String, RawJob> byId = new LinkedHashMap<>();
        for (RawJob r : raws) {
            if (r.externalId() != null && r.title() != null && r.recruitType() != null) {
                byId.putIfAbsent(r.externalId(), r);
            }
        }
        return List.copyOf(byId.values());
    }
}
