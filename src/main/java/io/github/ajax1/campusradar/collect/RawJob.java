package io.github.ajax1.campusradar.collect;

import io.github.ajax1.campusradar.job.RecruitType;

import java.time.Instant;
import java.util.List;

/**
 * 采集器产出的单个岗位，字段已做过基本清洗，但还没入库
 *
 * @param externalId  来源侧岗位 ID
 * @param cities      已去掉「市」「总部」等后缀
 * @param description 岗位职责；列表接口不带 JD 的来源为空，需要再拉详情
 */
public record RawJob(String externalId, String title, String category, RecruitType recruitType,
                     Integer gradYear, List<String> cities, String description, String requirement,
                     String applyUrl, Instant publishedAt) {

    public RawJob {
        cities = cities == null ? List.of() : List.copyOf(cities);
    }

    public RawJob withDetail(String category, List<String> cities, String description, String requirement) {
        return new RawJob(externalId, title, category != null ? category : this.category, recruitType, gradYear,
                cities != null && !cities.isEmpty() ? cities : this.cities, description, requirement, applyUrl,
                publishedAt);
    }
}
