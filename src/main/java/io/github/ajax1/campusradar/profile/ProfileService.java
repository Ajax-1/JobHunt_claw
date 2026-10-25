package io.github.ajax1.campusradar.profile;

import io.github.ajax1.campusradar.collect.CompanyDirectory;
import io.github.ajax1.campusradar.job.RecruitType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

@Service
public class ProfileService {

    private final UserProfileRepository repository;
    private final CompanyDirectory companies;

    public ProfileService(UserProfileRepository repository, CompanyDirectory companies) {
        this.repository = repository;
        this.companies = companies;
    }

    /**
     * 还没建过档案时返回一份空档案（不落库）
     */
    @Transactional(readOnly = true)
    public UserProfile current() {
        return repository.findById(UserProfile.DEFAULT_ID).orElseGet(() -> new UserProfile(UserProfile.DEFAULT_ID));
    }

    @Transactional(readOnly = true)
    public ProfileView view() {
        return ProfileView.of(current());
    }

    /**
     * 部分更新：字段为 null 不改，列表传空数组清空。公司名统一解析成库里的标准名，认不出的报错。
     */
    @Transactional
    public ProfileView update(ProfileView change) {
        UserProfile p = repository.findById(UserProfile.DEFAULT_ID).orElseGet(() -> new UserProfile(UserProfile.DEFAULT_ID));
        if (change.gradYear() != null) {
            if (change.gradYear() < 2000 || change.gradYear() > 2100) {
                throw new IllegalArgumentException("gradYear 应为四位年份，如 2027");
            }
            p.setGradYear(change.gradYear());
        }
        if (change.degree() != null) {
            p.setDegree(blankToNull(change.degree()));
        }
        if (change.major() != null) {
            p.setMajor(blankToNull(change.major()));
        }
        if (change.targetRoles() != null) {
            p.setTargetRoles(clean(change.targetRoles()));
        }
        if (change.skills() != null) {
            p.setSkills(clean(change.skills()));
        }
        if (change.cities() != null) {
            p.setCities(clean(change.cities()).stream().map(ProfileService::stripCitySuffix).distinct().toList());
        }
        if (change.recruitTypes() != null) {
            List<RecruitType> types = new ArrayList<>();
            for (String t : clean(change.recruitTypes())) {
                RecruitType type = RecruitType.parse(t);
                if (type == null) {
                    throw new IllegalArgumentException("recruitTypes 只能包含 校招、实习，收到：" + t);
                }
                types.add(type);
            }
            p.setRecruitTypes(types.stream().distinct().toList());
        }
        if (change.preferredCompanies() != null) {
            p.setPreferredCompanies(resolveCompanies(change.preferredCompanies()));
        }
        if (change.excludedCompanies() != null) {
            p.setExcludedCompanies(resolveCompanies(change.excludedCompanies()));
        }
        if (change.excludeKeywords() != null) {
            p.setExcludeKeywords(clean(change.excludeKeywords()));
        }
        if (change.summary() != null) {
            p.setSummary(blankToNull(change.summary()));
        }
        p.setUpdatedAt(Instant.now());
        return ProfileView.of(repository.save(p));
    }

    private List<String> resolveCompanies(List<String> names) {
        List<String> out = new ArrayList<>();
        for (String name : clean(names)) {
            out.add(companies.resolve(name).orElseThrow(() -> new IllegalArgumentException(
                    "没有收录公司「" + name + "」。已收录：" + String.join("、", companies.companies()))));
        }
        return out.stream().distinct().toList();
    }

    /**
     * 去首尾空格、去空串、去重保序
     */
    static List<String> clean(List<String> values) {
        LinkedHashSet<String> out = new LinkedHashSet<>();
        for (String v : values) {
            if (v != null && !v.isBlank()) {
                out.add(v.trim());
            }
        }
        return List.copyOf(out);
    }

    private static String stripCitySuffix(String city) {
        return city.endsWith("市") && city.length() > 2 ? city.substring(0, city.length() - 1) : city;
    }

    private static String blankToNull(String s) {
        return s.isBlank() ? null : s.trim();
    }
}
