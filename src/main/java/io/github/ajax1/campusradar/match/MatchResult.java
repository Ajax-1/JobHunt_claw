package io.github.ajax1.campusradar.match;

import io.github.ajax1.campusradar.job.JobViews;

import java.util.List;

/**
 * @param score   0–100，构成见 {@link JobMatcher}
 * @param reasons 每一项得分的来由，如「标题命中：后端」「技能 3/5：Java、Redis、MySQL」
 */
public record MatchResult(JobViews.Summary job, int score, List<String> reasons) {
}
