package com.git.hui.jobclaw.gather.service;

import com.git.hui.jobclaw.constants.gather.GatherRunnerTypes;
import com.git.hui.jobclaw.core.apis.PageListVo;
import com.git.hui.jobclaw.core.utils.json.JsonUtil;
import com.git.hui.jobclaw.gather.convert.GatherSourceConvert;
import com.git.hui.jobclaw.gather.dao.entity.GatherSourceEntity;
import com.git.hui.jobclaw.gather.dao.entity.GatherTaskEntity;
import com.git.hui.jobclaw.gather.dao.repository.GatherSourceRepository;
import com.git.hui.jobclaw.gather.dao.repository.GatherTaskRepository;
import com.git.hui.jobclaw.gather.model.GatherTaskResultBo;
import com.git.hui.jobclaw.oc.dao.repository.OcDraftRepository;
import com.git.hui.jobclaw.oc.dao.repository.OcRepository;
import com.git.hui.jobclaw.web.model.req.GatherSourceSearchReq;
import com.git.hui.jobclaw.web.model.res.GatherSourceVo;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Date;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Service
public class GatherSourceService {
    public static final String STATUS_ACTIVE = "active";
    public static final String STATUS_ARCHIVED = "archived";
    public static final String MODE_DRAFT_ONLY = "draft_only";
    public static final String MODE_AGENT_PUBLISH = "agent_publish";

    private static final int TITLE_MAX_LEN = 80;

    private final GatherSourceRepository gatherSourceRepository;

    private final GatherTaskRepository gatherTaskRepository;

    private final OcDraftRepository ocDraftRepository;

    private final OcRepository ocRepository;

    public GatherSourceService(GatherSourceRepository gatherSourceRepository,
                               GatherTaskRepository gatherTaskRepository,
                               OcDraftRepository ocDraftRepository,
                               OcRepository ocRepository) {
        this.gatherSourceRepository = gatherSourceRepository;
        this.gatherTaskRepository = gatherTaskRepository;
        this.ocDraftRepository = ocDraftRepository;
        this.ocRepository = ocRepository;
    }

    public GatherSourceEntity resolveSource(Integer type, String model, String content, String runnerType) {
        String normalizedRunner = GatherRunnerTypes.normalize(runnerType);
        String normalizedContent = normalizeContent(content);
        String contentHash = hash(normalizedContent);
        String sourceHash = contentHash;
        Date now = new Date();

        GatherSourceEntity source = gatherSourceRepository.findFirstBySourceHashAndStatusNot(sourceHash, STATUS_ARCHIVED);
        if (source == null) {
            source = gatherSourceRepository.findFirstByContentHashAndStatusNotOrderByTypeDescIdAsc(contentHash, STATUS_ARCHIVED);
        }
        if (source == null) {
            source = new GatherSourceEntity()
                    .setType(type)
                    .setTitle(buildTitle(content))
                    .setContent(content)
                    .setSourceHash(sourceHash)
                    .setContentHash(contentHash)
                    .setVersion(1)
                    .setModel(model)
                    .setOwnerType(normalizedRunner)
                    .setRunnerMode(GatherRunnerTypes.AGENT.equals(normalizedRunner) ? MODE_AGENT_PUBLISH : MODE_DRAFT_ONLY)
                    .setStatus(STATUS_ACTIVE)
                    .setLastInsertCount(0)
                    .setLastUpdateCount(0)
                    .setLastUnchangedCount(0)
                    .setLastFailedCount(0)
                    .setConsecutiveFailCount(0)
                    .setCreateTime(now)
                    .setUpdateTime(now);
        } else {
            source.setModel(model);
            source.setOwnerType(normalizedRunner);
            source.setRunnerMode(GatherRunnerTypes.AGENT.equals(normalizedRunner) ? MODE_AGENT_PUBLISH : MODE_DRAFT_ONLY);
            source.setUpdateTime(now);
            if (source.getType() == null || source.getType() == 0) {
                source.setType(type);
            }
            if (source.getTitle() == null || source.getTitle().isBlank()) {
                source.setTitle(buildTitle(content));
            }
            if (source.getContent() == null || source.getContent().isBlank()) {
                source.setContent(content);
            }
        }

        return gatherSourceRepository.saveAndFlush(source);
    }

    public void attachTask(Long sourceId, Long taskId) {
        if (sourceId == null || taskId == null) {
            return;
        }
        gatherSourceRepository.findById(sourceId).ifPresent(source -> {
            source.setLastTaskId(taskId);
            source.setUpdateTime(new Date());
            gatherSourceRepository.saveAndFlush(source);
        });
    }

    public void updateTypeIfUnknown(Long sourceId, Integer type) {
        if (sourceId == null || type == null || type == 0) {
            return;
        }
        gatherSourceRepository.findById(sourceId).ifPresent(source -> {
            if (source.getType() == null || source.getType() == 0) {
                source.setType(type);
                source.setUpdateTime(new Date());
                gatherSourceRepository.saveAndFlush(source);
            }
        });
    }

    public void updateTypeAndModel(Long sourceId, Integer type, String model) {
        if (sourceId == null) {
            return;
        }
        gatherSourceRepository.findById(sourceId).ifPresent(source -> {
            boolean changed = false;
            if (type != null && type != 0 && (source.getType() == null || source.getType() == 0)) {
                source.setType(type);
                changed = true;
            }
            if (model != null && !model.isBlank() && !Objects.equals(source.getModel(), model)) {
                source.setModel(model);
                changed = true;
            }
            if (changed) {
                source.setUpdateTime(new Date());
                gatherSourceRepository.saveAndFlush(source);
            }
        });
    }

    public void refreshResult(Long sourceId, Long taskId, GatherTaskResultBo result) {
        if (sourceId == null || result == null) {
            return;
        }
        gatherSourceRepository.findById(sourceId).ifPresent(source -> {
            int insert = result.insertDraftIds().size();
            int update = result.updateDraftIds().size();
            int unchanged = result.unchangedDraftIds().size();
            int failed = result.failedItems().size();
            Date now = new Date();

            source.setLastTaskId(taskId);
            source.setLastInsertCount(insert);
            source.setLastUpdateCount(update);
            source.setLastUnchangedCount(unchanged);
            source.setLastFailedCount(failed);
            source.setLastResultSummary(JsonUtil.toStr(summaryMap(result, insert, update, unchanged, failed)));
            source.setUpdateTime(now);
            if (result.isSuccess()) {
                source.setLastSuccessTime(now);
                source.setConsecutiveFailCount(0);
                source.setStatus(STATUS_ACTIVE);
            } else {
                source.setConsecutiveFailCount(source.getConsecutiveFailCount() == null ? 1 : source.getConsecutiveFailCount() + 1);
            }
            gatherSourceRepository.saveAndFlush(source);
        });
    }

    @Transactional
    public PageListVo<GatherSourceVo> searchList(GatherSourceSearchReq req) {
        backfillLegacyTasks();
        archiveDuplicateSources();
        backfillLegacyArtifacts();
        req.autoInitPage();
        PageListVo<GatherSourceEntity> page = gatherSourceRepository.findList(req);
        return PageListVo.of(GatherSourceConvert.toVo(page.getList()), page.getTotal(), page.getPage(), page.getSize());
    }

    public GatherSourceEntity getSource(Long sourceId) {
        Assert.notNull(sourceId, "sourceId can not be null");
        return gatherSourceRepository.findById(sourceId).orElseThrow(() -> new IllegalArgumentException(sourceId + "非法"));
    }

    @Transactional
    public GatherSourceVo detail(Long sourceId) {
        backfillLegacyTasks();
        archiveDuplicateSources();
        backfillLegacyArtifacts();
        return GatherSourceConvert.toVo(getSource(sourceId));
    }

    @Transactional
    private void backfillLegacyTasks() {
        List<GatherTaskEntity> tasks = gatherTaskRepository.findTop100BySourceIdIsNullOrderByIdAsc();
        if (tasks.isEmpty()) {
            return;
        }
        for (GatherTaskEntity task : tasks) {
            String runnerType = GatherRunnerTypes.normalize(task.getRunnerType());
            GatherSourceEntity source = resolveSource(task.getType(), task.getModel(), task.getContent(), runnerType);
            task.setSourceId(source.getId());
            task.setSourceVersion(source.getVersion());
            task.setRunnerType(runnerType);
            gatherTaskRepository.save(task);
            refreshLegacyResult(source.getId(), task);
        }
        gatherTaskRepository.flush();
    }

    @Transactional
    private void archiveDuplicateSources() {
        List<GatherSourceEntity> sources = gatherSourceRepository.findByStatusNotOrderByContentHashAscTypeDescIdAsc(STATUS_ARCHIVED);
        GatherSourceEntity current = null;
        for (GatherSourceEntity source : sources) {
            if (source.getContentHash() == null || source.getContentHash().isBlank()) {
                continue;
            }
            if (current == null || !Objects.equals(current.getContentHash(), source.getContentHash())) {
                current = source;
                continue;
            }
            gatherTaskRepository.moveSourceTasks(source.getId(), current.getId());
            ocDraftRepository.moveSourceContext(source.getId(), current.getId());
            ocRepository.moveSourceContext(source.getId(), current.getId());
            source.setStatus(STATUS_ARCHIVED);
            source.setUpdateTime(new Date());
            gatherSourceRepository.save(source);
        }
        gatherSourceRepository.flush();
    }

    private void backfillLegacyArtifacts() {
        if (!ocDraftRepository.existsBySourceIdIsNull() && !ocRepository.existsBySourceIdIsNull()) {
            return;
        }
        List<GatherTaskEntity> tasks = gatherTaskRepository.findTop500BySourceIdIsNotNullOrderByIdDesc();
        if (tasks.isEmpty()) {
            return;
        }
        for (GatherTaskEntity task : tasks) {
            if (task.getSourceId() == null || task.getResult() == null || task.getResult().isBlank()) {
                continue;
            }
            List<Long> draftIds = extractDraftIds(task.getResult());
            if (draftIds.isEmpty()) {
                continue;
            }
            String runnerType = GatherRunnerTypes.normalize(task.getRunnerType());
            ocDraftRepository.updateSourceContextByIds(draftIds, task.getSourceId(), task.getId(), runnerType);
            ocRepository.updateSourceContextByDraftIds(draftIds, task.getSourceId(), task.getId(), runnerType);
        }
        ocDraftRepository.flush();
        ocRepository.flush();
    }

    private void refreshLegacyResult(Long sourceId, GatherTaskEntity task) {
        if (task.getResult() == null || task.getResult().isBlank()) {
            attachTask(sourceId, task.getId());
            return;
        }
        try {
            GatherTaskResultBo result = JsonUtil.toObj(task.getResult(), GatherTaskResultBo.class);
            refreshResult(sourceId, task.getId(), result);
        } catch (Exception e) {
            attachTask(sourceId, task.getId());
        }
    }

    private List<Long> extractDraftIds(String result) {
        try {
            GatherTaskResultBo bo = JsonUtil.toObj(result, GatherTaskResultBo.class);
            Set<Long> ids = new LinkedHashSet<>();
            ids.addAll(bo.insertDraftIds());
            ids.addAll(bo.updateDraftIds());
            ids.addAll(bo.unchangedDraftIds());
            ids.addAll(bo.skipDraftIds());
            return ids.stream().filter(Objects::nonNull).toList();
        } catch (Exception e) {
            return List.of();
        }
    }

    private Map<String, Object> summaryMap(GatherTaskResultBo result, int insert, int update, int unchanged, int failed) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("msg", result.msg());
        summary.put("insert", insert);
        summary.put("update", update);
        summary.put("unchanged", unchanged);
        summary.put("skip", result.skipDraftIds().size());
        summary.put("failed", failed);
        return summary;
    }

    private String normalizeContent(String content) {
        if (content == null) {
            return "";
        }
        String value = content.trim().replaceAll("\\s+", " ");
        if (value.toLowerCase(Locale.ROOT).startsWith("http://") || value.toLowerCase(Locale.ROOT).startsWith("https://")) {
            return value.replaceAll("#.*$", "");
        }
        return value;
    }

    private String buildTitle(String content) {
        String normalized = normalizeContent(content);
        if (normalized.isBlank()) {
            return "空投料";
        }
        return normalized.length() <= TITLE_MAX_LEN ? normalized : normalized.substring(0, TITLE_MAX_LEN) + "...";
    }

    private String hash(String content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(content.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 不可用", e);
        }
    }
}
