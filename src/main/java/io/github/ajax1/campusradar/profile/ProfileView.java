package io.github.ajax1.campusradar.profile;

import io.github.ajax1.campusradar.job.RecruitType;

import java.time.Instant;
import java.util.List;

/**
 * 对外返回和更新用的档案结构。更新时字段为 null 表示不改，列表传空数组表示清空。
 *
 * @param recruitTypes 校招 / 实习
 */
public record ProfileView(Integer gradYear, String degree, String major, List<String> targetRoles,
                          List<String> skills, List<String> cities, List<String> recruitTypes,
                          List<String> preferredCompanies, List<String> excludedCompanies,
                          List<String> excludeKeywords, String summary, Instant updatedAt) {

    static ProfileView of(UserProfile p) {
        return new ProfileView(p.getGradYear(), p.getDegree(), p.getMajor(), p.getTargetRoles(), p.getSkills(),
                p.getCities(), p.getRecruitTypes().stream().map(RecruitType::label).toList(),
                p.getPreferredCompanies(), p.getExcludedCompanies(), p.getExcludeKeywords(), p.getSummary(),
                p.getUpdatedAt());
    }
}
