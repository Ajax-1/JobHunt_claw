package io.github.ajax1.campusradar.job;

import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

@Service
@Transactional(readOnly = true)
public class JobQueryService {

    static final int MAX_LIMIT = 50;
    /**
     * 先按条件取这么多候选，再在内存里打分、穿插公司
     */
    static final int CANDIDATES = 1000;
    private static final int SNIPPET_RADIUS = 40;

    private final JobPostingRepository repository;

    public JobQueryService(JobPostingRepository repository) {
        this.repository = repository;
    }

    /**
     * 只查在招岗位。有关键词时按命中位置打分（标题 > 类别 > JD）；没限定公司时结果在各公司之间轮流穿插，
     * 避免岗位最多的公司刷屏。同分按 postedAt 倒序。
     */
    public List<JobViews.Summary> search(JobQuery q) {
        int limit = Math.clamp(q.limit() <= 0 ? 20 : q.limit(), 1, MAX_LIMIT);
        List<String> words = words(q.keyword());
        Sort sort = Sort.by(Sort.Order.desc("postedAt").nullsLast(), Sort.Order.desc("id"));
        List<JobPosting> candidates = repository.findAll(spec(q, words), PageRequest.of(0, CANDIDATES, sort)).getContent();

        List<JobPosting> ranked = candidates;
        if (!words.isEmpty()) {
            Map<JobPosting, Integer> scores = new LinkedHashMap<>();
            candidates.forEach(j -> scores.put(j, score(j, words)));
            ranked = candidates.stream()
                    .sorted(Comparator.comparingInt((JobPosting j) -> scores.get(j)).reversed())
                    .toList();
        }
        if (q.company() == null || q.company().isBlank()) {
            ranked = interleaveByCompany(ranked);
        }
        return ranked.stream()
                .limit(limit)
                .map(j -> JobViews.summary(j, snippet(j, words)))
                .toList();
    }

    public Optional<JobViews.Detail> get(long id) {
        return repository.findById(id).map(JobViews::detail);
    }

    static Specification<JobPosting> spec(JobQuery q, List<String> words) {
        return (root, query, cb) -> {
            List<Predicate> ps = new ArrayList<>();
            ps.add(cb.equal(root.get("status"), JobStatus.OPEN));
            if (notBlank(q.company())) {
                ps.add(cb.equal(root.get("company"), q.company().trim()));
            }
            if (notBlank(q.city())) {
                ps.add(cb.like(root.<String>get("cities"), "%," + stripCitySuffix(q.city().trim()) + ",%"));
            }
            if (q.recruitType() != null) {
                ps.add(cb.equal(root.get("recruitType"), q.recruitType()));
            }
            if (q.gradYear() != null) {
                ps.add(cb.equal(root.get("gradYear"), q.gradYear()));
            }
            if (q.sinceDays() != null && q.sinceDays() > 0) {
                ps.add(cb.greaterThanOrEqualTo(root.get("postedAt"),
                        Instant.now().minus(q.sinceDays(), ChronoUnit.DAYS)));
            }
            // 多个词之间是「且」的关系，每个词命中标题、类别或 JD 任一处即可
            for (String word : words) {
                String like = "%" + word + "%";
                ps.add(cb.or(
                        cb.like(cb.lower(root.<String>get("title")), like),
                        cb.like(cb.lower(root.<String>get("category")), like),
                        // JD 是长文本，Hibernate 的 lower() 参数校验不接受，先显式转成 String
                        cb.like(cb.lower(root.get("description").as(String.class)), like),
                        cb.like(cb.lower(root.get("requirement").as(String.class)), like)));
            }
            return cb.and(ps.toArray(Predicate[]::new));
        };
    }

    /**
     * 每个词：标题命中 5 分，职能类别 2 分，JD 1 分
     */
    static int score(JobPosting j, List<String> words) {
        int score = 0;
        for (String w : words) {
            if (contains(j.getTitle(), w)) {
                score += 5;
            }
            if (contains(j.getCategory(), w)) {
                score += 2;
            }
            if (contains(j.getDescription(), w) || contains(j.getRequirement(), w)) {
                score += 1;
            }
        }
        return score;
    }

    /**
     * 保持各公司内部的顺序，按公司首次出现的先后轮流取一条
     */
    static List<JobPosting> interleaveByCompany(List<JobPosting> ranked) {
        Map<String, Iterator<JobPosting>> byCompany = new LinkedHashMap<>();
        Map<String, List<JobPosting>> groups = new LinkedHashMap<>();
        for (JobPosting j : ranked) {
            groups.computeIfAbsent(j.getCompany(), k -> new ArrayList<>()).add(j);
        }
        groups.forEach((k, v) -> byCompany.put(k, v.iterator()));
        List<JobPosting> out = new ArrayList<>(ranked.size());
        while (out.size() < ranked.size()) {
            for (Iterator<JobPosting> it : byCompany.values()) {
                if (it.hasNext()) {
                    out.add(it.next());
                }
            }
        }
        return out;
    }

    /**
     * 第一个在 JD 里命中的关键词前后各取一段；只在标题里命中时不返回片段
     */
    static String snippet(JobPosting j, List<String> words) {
        for (String w : words) {
            for (String text : Arrays.asList(j.getDescription(), j.getRequirement())) {
                if (text == null) {
                    continue;
                }
                int at = text.toLowerCase(Locale.ROOT).indexOf(w);
                if (at >= 0) {
                    int from = Math.max(0, at - SNIPPET_RADIUS);
                    int to = Math.min(text.length(), at + w.length() + SNIPPET_RADIUS);
                    String s = text.substring(from, to).replaceAll("\\s+", " ").trim();
                    return (from > 0 ? "…" : "") + s + (to < text.length() ? "…" : "");
                }
            }
        }
        return null;
    }

    static List<String> words(String keyword) {
        if (!notBlank(keyword)) {
            return List.of();
        }
        return Arrays.stream(keyword.trim().toLowerCase(Locale.ROOT).split("\\s+")).distinct().toList();
    }

    /**
     * 用户可能输入「北京市」，库里统一存「北京」
     */
    static String stripCitySuffix(String city) {
        return city.endsWith("市") && city.length() > 2 ? city.substring(0, city.length() - 1) : city;
    }

    private static boolean contains(String text, String lowerWord) {
        return text != null && text.toLowerCase(Locale.ROOT).contains(lowerWord);
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }
}
