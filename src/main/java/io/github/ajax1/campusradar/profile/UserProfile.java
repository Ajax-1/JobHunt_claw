package io.github.ajax1.campusradar.profile;

import io.github.ajax1.campusradar.job.RecruitType;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.List;

/**
 * 求职档案：匹配打分和每日推送都基于它。P1 只有一份档案（id = default），多用户在 P2 随投递台账一起做。
 * 不存姓名、手机号、身份证这类个人信息，只存求职偏好和能力关键词。
 */
@Entity
@Table(name = "user_profile")
public class UserProfile {

    public static final String DEFAULT_ID = "default";

    @Id
    @Column(length = 32)
    private String id;

    /**
     * 毕业届别，如 2027
     */
    private Integer gradYear;

    /**
     * 本科 / 硕士 / 博士
     */
    @Column(length = 16)
    private String degree;

    @Column(length = 64)
    private String major;

    /**
     * 目标岗位方向，如 后端、Java、大模型应用
     */
    @Convert(converter = StringListConverter.class)
    @Column(length = 1000)
    private List<String> targetRoles = List.of();

    /**
     * 技能关键词，如 Java、Spring、Redis、MySQL
     */
    @Convert(converter = StringListConverter.class)
    @Column(length = 1000)
    private List<String> skills = List.of();

    /**
     * 意向城市，按偏好排序；为空表示不限
     */
    @Convert(converter = StringListConverter.class)
    @Column(length = 500)
    private List<String> cities = List.of();

    /**
     * 想看的招聘类型；为空表示校招和实习都要
     */
    @Convert(converter = RecruitTypeListConverter.class)
    @Column(length = 64)
    private List<RecruitType> recruitTypes = List.of();

    @Convert(converter = StringListConverter.class)
    @Column(length = 500)
    private List<String> preferredCompanies = List.of();

    @Convert(converter = StringListConverter.class)
    @Column(length = 500)
    private List<String> excludedCompanies = List.of();

    /**
     * 标题里出现就排除，如 销售、客服
     */
    @Convert(converter = StringListConverter.class)
    @Column(length = 500)
    private List<String> excludeKeywords = List.of();

    /**
     * 经历简述（项目、实习），P2 定制简历时用
     */
    @JdbcTypeCode(SqlTypes.LONG32VARCHAR)
    private String summary;

    private Instant updatedAt;

    protected UserProfile() {
    }

    public UserProfile(String id) {
        this.id = id;
    }

    public String getId() {
        return id;
    }

    public Integer getGradYear() {
        return gradYear;
    }

    public void setGradYear(Integer gradYear) {
        this.gradYear = gradYear;
    }

    public String getDegree() {
        return degree;
    }

    public void setDegree(String degree) {
        this.degree = degree;
    }

    public String getMajor() {
        return major;
    }

    public void setMajor(String major) {
        this.major = major;
    }

    public List<String> getTargetRoles() {
        return targetRoles;
    }

    public void setTargetRoles(List<String> targetRoles) {
        this.targetRoles = targetRoles;
    }

    public List<String> getSkills() {
        return skills;
    }

    public void setSkills(List<String> skills) {
        this.skills = skills;
    }

    public List<String> getCities() {
        return cities;
    }

    public void setCities(List<String> cities) {
        this.cities = cities;
    }

    public List<RecruitType> getRecruitTypes() {
        return recruitTypes;
    }

    public void setRecruitTypes(List<RecruitType> recruitTypes) {
        this.recruitTypes = recruitTypes;
    }

    public List<String> getPreferredCompanies() {
        return preferredCompanies;
    }

    public void setPreferredCompanies(List<String> preferredCompanies) {
        this.preferredCompanies = preferredCompanies;
    }

    public List<String> getExcludedCompanies() {
        return excludedCompanies;
    }

    public void setExcludedCompanies(List<String> excludedCompanies) {
        this.excludedCompanies = excludedCompanies;
    }

    public List<String> getExcludeKeywords() {
        return excludeKeywords;
    }

    public void setExcludeKeywords(List<String> excludeKeywords) {
        this.excludeKeywords = excludeKeywords;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
