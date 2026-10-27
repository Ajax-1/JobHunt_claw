package io.github.ajax1.campusradar.match;

import io.github.ajax1.campusradar.application.ApplicationRepository;
import io.github.ajax1.campusradar.job.JobPosting;
import io.github.ajax1.campusradar.job.JobPostingRepository;
import io.github.ajax1.campusradar.job.JobStatus;
import io.github.ajax1.campusradar.job.JobViews;
import io.github.ajax1.campusradar.profile.UserProfile;
import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

/**
 * 按档案给在招岗位打分。规则式、可解释：每一分都附带理由，推送和 Agent 都能说清「为什么推这个」。
 * <pre>
 * 岗位方向 40：目标方向命中标题 30，每多命中一个 +5（封顶 40）；只命中职能类别 20；只在 JD 里 10
 * 技能     30：档案技能在标题和 JD 里出现的比例 × 30
 * 城市     10：第一意向城市 10，其他意向城市 7，岗位没写城市 5
 * 公司     10：偏好公司 10
 * 新鲜度   10：3 天内 10，7 天内 6，14 天内 3
 * </pre>
 * 硬过滤：招聘类型、届别（岗位没标届别的保留）、排除公司、标题里的排除关键词、意向城市（岗位没写城市的保留），
 * 以及方向和技能都没命中的岗位。英文关键词按词边界匹配（java 不会命中 javascript），见 {@link Terms}。
 */
@Service
@Transactional(readOnly = true)
public class JobMatcher {

    static final int PAGE_SIZE = 1000;

    private final JobPostingRepository repository;
    private final ApplicationRepository applications;
    private final EntityManager entityManager;

    public JobMatcher(JobPostingRepository repository, ApplicationRepository applications, EntityManager entityManager) {
        this.repository = repository;
        this.applications = applications;
        this.entityManager = entityManager;
    }

    /**
     * @param maxPerCompany 同一家公司最多出现几次，避免岗位多的公司占满
     * @param skipJobIds    不参与排序的岗位，如已经推送过的；投递台账里已有的岗位总是跳过
     */
    public List<MatchResult> match(UserProfile profile, int limit, int maxPerCompany, Collection<Long> skipJobIds) {
        List<String> roles = lower(profile.getTargetRoles());
        List<String> skills = lower(profile.getSkills());
        if (roles.isEmpty() && skills.isEmpty()) {
            throw new IllegalStateException("档案里还没有目标方向和技能，先用 update_profile 填写 targetRoles 或 skills");
        }
        Set<Long> skip = new HashSet<>(skipJobIds);
        skip.addAll(applications.findTrackedJobIds());
        Instant now = Instant.now();
        List<MatchResult> scored = new ArrayList<>();
        // 全量分页扫描，按 id 分页保证不漏不重；每页之后清掉持久化上下文，内存不随岗位数增长
        PageRequest page = PageRequest.of(0, PAGE_SIZE, Sort.by("id"));
        Specification<JobPosting> spec = spec(profile, roles, skills);
        while (true) {
            Slice<JobPosting> slice = repository.findAll(spec, page);
            for (JobPosting j : slice) {
                if (skip.contains(j.getId()) || excludedByKeyword(j, profile.getExcludeKeywords())) {
                    continue;
                }
                MatchResult r = score(j, profile, roles, skills, now);
                if (r != null) {
                    scored.add(r);
                }
            }
            entityManager.clear();
            if (!slice.hasNext()) {
                break;
            }
            page = page.next();
        }
        scored.sort(Comparator.comparingInt(MatchResult::score).reversed()
                .thenComparing(r -> r.job().postedAt(), Comparator.nullsLast(Comparator.reverseOrder())));

        Map<String, Integer> perCompany = new HashMap<>();
        List<MatchResult> out = new ArrayList<>();
        for (MatchResult r : scored) {
            if (out.size() >= limit) {
                break;
            }
            int n = perCompany.merge(r.job().company(), 1, Integer::sum);
            if (maxPerCompany <= 0 || n <= maxPerCompany) {
                out.add(r);
            }
        }
        return out;
    }

    static Specification<JobPosting> spec(UserProfile p, List<String> roles, List<String> skills) {
        return (root, query, cb) -> {
            List<Predicate> ps = new ArrayList<>();
            ps.add(cb.equal(root.get("status"), JobStatus.OPEN));
            if (!p.getRecruitTypes().isEmpty()) {
                ps.add(root.get("recruitType").in(p.getRecruitTypes()));
            }
            if (p.getGradYear() != null) {
                ps.add(cb.or(cb.isNull(root.get("gradYear")), cb.equal(root.get("gradYear"), p.getGradYear())));
            }
            if (!p.getExcludedCompanies().isEmpty()) {
                ps.add(cb.not(root.get("company").in(p.getExcludedCompanies())));
            }
            if (!p.getCities().isEmpty()) {
                List<Predicate> any = new ArrayList<>();
                any.add(cb.isNull(root.get("cities")));
                for (String c : p.getCities()) {
                    any.add(cb.like(root.<String>get("cities"), "%," + c + ",%"));
                }
                ps.add(cb.or(any.toArray(Predicate[]::new)));
            }
            // 至少命中一个方向或技能（含同义写法），先在库里粗筛；这里是子串匹配，精确判断在 score 里
            List<Predicate> hit = new ArrayList<>();
            for (String w : Stream.concat(roles.stream(), skills.stream()).flatMap(t -> Terms.variants(t).stream())
                    .distinct().toList()) {
                String like = "%" + w + "%";
                hit.add(cb.like(cb.lower(root.<String>get("title")), like));
                hit.add(cb.like(cb.lower(root.<String>get("category")), like));
                hit.add(cb.like(cb.lower(root.get("description").as(String.class)), like));
                hit.add(cb.like(cb.lower(root.get("requirement").as(String.class)), like));
            }
            ps.add(cb.or(hit.toArray(Predicate[]::new)));
            return cb.and(ps.toArray(Predicate[]::new));
        };
    }

    /**
     * 方向和技能都没命中时返回 null
     */
    static MatchResult score(JobPosting j, UserProfile p, List<String> roles, List<String> skills, Instant now) {
        String title = lowerOrEmpty(j.getTitle());
        String category = lowerOrEmpty(j.getCategory());
        String jd = lowerOrEmpty(j.getDescription()) + "\n" + lowerOrEmpty(j.getRequirement());
        List<String> reasons = new ArrayList<>();
        int score = 0;

        // 岗位方向
        List<String> inTitle = roles.stream().filter(r -> Terms.contains(title, r)).toList();
        List<String> inCategory = roles.stream().filter(r -> Terms.contains(category, r)).toList();
        List<String> inJd = roles.stream().filter(r -> Terms.contains(jd, r)).toList();
        int roleScore = 0;
        if (!inTitle.isEmpty()) {
            roleScore = Math.min(40, 30 + 5 * (inTitle.size() - 1));
            reasons.add("标题命中：" + original(p.getTargetRoles(), inTitle));
        } else if (!inCategory.isEmpty()) {
            roleScore = 20;
            reasons.add("职能类别命中：" + original(p.getTargetRoles(), inCategory));
        } else if (!inJd.isEmpty()) {
            roleScore = 10;
            reasons.add("JD 提到：" + original(p.getTargetRoles(), inJd));
        }
        score += roleScore;

        // 技能
        List<String> skillHits = skills.stream().filter(s -> Terms.contains(title, s) || Terms.contains(jd, s)).toList();
        if (!skillHits.isEmpty()) {
            score += Math.round(30f * skillHits.size() / skills.size());
            reasons.add("技能 " + skillHits.size() + "/" + skills.size() + "：" + original(p.getSkills(), skillHits));
        }
        if (roleScore == 0 && skillHits.isEmpty()) {
            return null;
        }

        // 城市
        List<String> jobCities = JobViews.summary(j, null).cities();
        if (!p.getCities().isEmpty()) {
            if (jobCities.isEmpty()) {
                score += 5;
            } else if (jobCities.contains(p.getCities().getFirst())) {
                score += 10;
                reasons.add("城市：" + p.getCities().getFirst());
            } else {
                p.getCities().stream().filter(jobCities::contains).findFirst().ifPresent(c -> reasons.add("城市：" + c));
                score += 7;
            }
        }

        // 公司
        if (p.getPreferredCompanies().contains(j.getCompany())) {
            score += 10;
            reasons.add("偏好公司");
        }

        // 新鲜度
        if (j.getPostedAt() != null) {
            long days = Duration.between(j.getPostedAt(), now).toDays();
            if (days <= 3) {
                score += 10;
                reasons.add(days == 0 ? "今天发布" : days + " 天前发布");
            } else if (days <= 7) {
                score += 6;
                reasons.add(days + " 天前发布");
            } else if (days <= 14) {
                score += 3;
            }
        }
        return new MatchResult(JobViews.summary(j, null), Math.min(score, 100), reasons);
    }

    static boolean excludedByKeyword(JobPosting j, List<String> excludeKeywords) {
        String title = lowerOrEmpty(j.getTitle());
        return excludeKeywords.stream().anyMatch(k -> Terms.contains(title, k));
    }

    /**
     * 理由里用用户原来的写法（大小写），而不是小写后的匹配词
     */
    private static String original(List<String> originals, List<String> lowerHits) {
        Set<String> hits = Set.copyOf(lowerHits);
        return String.join("、", originals.stream().filter(o -> hits.contains(o.toLowerCase(Locale.ROOT))).toList());
    }

    private static List<String> lower(List<String> values) {
        return values.stream().map(v -> v.toLowerCase(Locale.ROOT)).distinct().toList();
    }

    private static String lowerOrEmpty(String s) {
        return s == null ? "" : s.toLowerCase(Locale.ROOT);
    }
}
