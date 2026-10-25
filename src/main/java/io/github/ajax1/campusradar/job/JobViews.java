package io.github.ajax1.campusradar.job;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

/**
 * 对外返回的岗位视图：列表用摘要，详情带完整 JD
 */
public final class JobViews {

    private JobViews() {
    }

    /**
     * @param publishedAt 官网给出的发布时间，部分来源（如腾讯）没有
     * @param postedAt    排序用日期：有发布时间用发布时间，否则用首次采到的时间
     * @param snippet     关键词在 JD 里命中的片段，没传关键词或只在标题命中时为空
     */
    public record Summary(long id, String company, String title, String category, String recruitType,
                          Integer gradYear, List<String> cities, Instant publishedAt, Instant postedAt,
                          LocalDate deadline, String status, String applyUrl, String snippet) {
    }

    public record Detail(long id, String source, String company, String title, String category, String recruitType,
                         Integer gradYear, List<String> cities, String description, String requirement,
                         Instant publishedAt, LocalDate deadline, String status, String applyUrl,
                         Instant firstSeenAt, Instant lastSeenAt) {
    }

    public static Summary summary(JobPosting j, String snippet) {
        return new Summary(j.getId(), j.getCompany(), j.getTitle(), j.getCategory(), j.getRecruitType().label(),
                j.getGradYear(), cities(j.getCities()), j.getPublishedAt(), j.getPostedAt(), j.getDeadline(),
                j.getStatus().name(), j.getApplyUrl(), snippet);
    }

    public static Detail detail(JobPosting j) {
        return new Detail(j.getId(), j.getSource(), j.getCompany(), j.getTitle(), j.getCategory(),
                j.getRecruitType().label(), j.getGradYear(), cities(j.getCities()), j.getDescription(),
                j.getRequirement(), j.getPublishedAt(), j.getDeadline(), j.getStatus().name(), j.getApplyUrl(),
                j.getFirstSeenAt(), j.getLastSeenAt());
    }

    static List<String> cities(String joined) {
        if (joined == null || joined.isBlank()) {
            return List.of();
        }
        return Arrays.stream(joined.split(",")).filter(s -> !s.isBlank()).toList();
    }
}
