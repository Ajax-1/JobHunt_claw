package io.github.ajax1.campusradar.job;

/**
 * 岗位检索条件，字段为空表示不限
 *
 * @param keyword     匹配标题、职能类别和 JD 正文
 * @param company     公司名，精确匹配
 * @param city        城市，如 北京
 * @param recruitType 校招或实习
 * @param gradYear    毕业届别
 * @param sinceDays   只看最近 N 天首次采到的岗位
 * @param limit       返回条数上限
 */
public record JobQuery(String keyword, String company, String city, RecruitType recruitType,
                       Integer gradYear, Integer sinceDays, int limit) {
}
