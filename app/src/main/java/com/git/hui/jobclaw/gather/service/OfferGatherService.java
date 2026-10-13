package com.git.hui.jobclaw.gather.service;

import cn.hutool.core.io.resource.ResourceUtil;
import cn.hutool.http.HttpRequest;
import cn.idev.excel.ExcelReader;
import cn.idev.excel.FastExcel;
import cn.idev.excel.context.AnalysisContext;
import cn.idev.excel.metadata.data.ReadCellData;
import cn.idev.excel.read.listener.ReadListener;
import com.fasterxml.jackson.databind.JsonNode;
import com.git.hui.jobclaw.core.bizexception.BizException;
import com.git.hui.jobclaw.core.bizexception.StatusEnum;
import com.git.hui.jobclaw.configs.service.CommonDictService;
import com.git.hui.jobclaw.constants.gather.GatherTargetTypeEnum;
import com.git.hui.jobclaw.gather.model.GatherFileBo;
import com.git.hui.jobclaw.gather.model.GatherDraftContext;
import com.git.hui.jobclaw.gather.model.GatherOcDraftBo;
import com.git.hui.jobclaw.gather.model.GatherTaskProcessBo;
import com.git.hui.jobclaw.gather.model.GatherTaskResultBo;
import com.git.hui.jobclaw.gather.model.TaskChangeListener;
import com.git.hui.jobclaw.oc.convert.DraftConvert;
import com.git.hui.jobclaw.oc.dao.entity.OcDraftEntity;
import com.git.hui.jobclaw.oc.service.GatherService;
import com.git.hui.jobclaw.core.utils.json.IntBaseEnum;
import com.git.hui.jobclaw.core.utils.json.JsonUtil;
import com.git.hui.jobclaw.web.model.req.GatherReq;
import com.git.hui.jobclaw.web.model.res.GatherVo;
import io.micrometer.common.util.StringUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.data.util.Pair;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;
import org.springframework.util.MimeType;
import org.springframework.util.MimeTypeUtils;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * @date 2025/7/14
 */
@Slf4j
@Service
public class OfferGatherService {
    private static final String GF_JIANLI_HOST = "offer.gfjianli.com";
    private static final String GF_JIANLI_API = "https://api.gfjianli.com/api/c/resume/campusRecruitment";
    private static final int GF_JIANLI_IMPORT_LIMIT = 20;
    private static final Pattern GRADUATION_YEAR_PATTERN = Pattern.compile("(20\\d{2})\\s*届?");

    private static final AtomicBoolean SCHEDULE_LOCK = new AtomicBoolean(false);

    private final GatherAiAgent gatherAiAgent;

    private final GatherService gatherService;

    private final GatherTaskService gatherTaskService;

    private final CommonDictService commonDictService;

    @Autowired
    public OfferGatherService(GatherAiAgent gatherAiAgent, GatherService gatherService, GatherTaskService gatherTaskService, CommonDictService commonDictService) {
        this.gatherAiAgent = gatherAiAgent;
        this.gatherService = gatherService;
        this.gatherTaskService = gatherTaskService;
        this.commonDictService = commonDictService;
    }

    /**
     * 监听任务变更事件
     *
     * @param listener
     */
    @Async
    @EventListener(TaskChangeListener.class)
    public void taskListener(TaskChangeListener listener) {
        switch (listener.getState()) {
            // 触发任务执行
            case INIT -> scheduleToLoadTask();
            case SUCCEED, FAILED, PROCESSING -> {
                log.info("任务执行，可以给用户发送一个消息通知<任务编号:{} 状态:{}>", listener.getTaskId(), listener.getState());
            }
        }
    }

    /**
     * 任务驱动方式，用于执行采集任务
     */
    @Scheduled(cron = "0 0/5 * * * ?")
    public void scheduleToLoadTask() {
        if (SCHEDULE_LOCK.get()) {
            log.info("任务已经在执行中了，当前只支持任务的单线程调度~");
            return;
        }

        SCHEDULE_LOCK.set(true);
        try {
            while (true) {
                GatherTaskProcessBo bo = gatherTaskService.pickUnProcessTaskToProcess();
                if (bo == null) {
                    return;
                }

                try {
                    gatherInfo(bo);
                } catch (Exception e) {
                    log.warn("采集任务执行失败，继续调度下一条任务: taskId={}, message={}", bo.taskId(), errorMessage(e));
                }
            }
        } finally {
            SCHEDULE_LOCK.set(false);
        }
    }

    /**
     * 执行任务采集
     *
     * @param bo
     * @return
     */
    public GatherVo gatherInfo(GatherTaskProcessBo bo) {
        GatherReq req = new GatherReq(bo.content(), bo.type().getValue(), bo.model());
        try {
            GatherFileBo file = null;
            if (bo.type() == GatherTargetTypeEnum.CSV_FILE || bo.type() == GatherTargetTypeEnum.EXCEL_FILE
                    || bo.type() == GatherTargetTypeEnum.IMAGE) {
                // 文件，需要先下来
                file = gatherTaskService.loadTmpFile(bo.content());
            }
            GatherVo vo = gatherInfo(req, file, new GatherDraftContext(bo.sourceId(), bo.taskId(), bo.runnerType()));

            // 保存任务执行结果
            GatherTaskResultBo res = new GatherTaskResultBo(
                    GatherTaskResultBo.SUCCESS
                    , safeDraftList(vo.getInsertList()).stream().map(OcDraftEntity::getId).collect(Collectors.toList())
                    , safeDraftList(vo.getUpdateList()).stream().map(OcDraftEntity::getId).collect(Collectors.toList())
                    , safeDraftList(vo.getUnchangedList()).stream().map(OcDraftEntity::getId).collect(Collectors.toList())
            );
            gatherTaskService.saveTaskResult(bo.taskId(), res);
            return vo;
        } catch (Exception e) {
            log.error("gather task error: {}", bo, e);
            // 任务执行失败，同样需要保存结果
            String message = errorMessage(e);
            gatherTaskService.saveTaskResult(bo.taskId(), new GatherTaskResultBo(message, List.of(), List.of(), List.of(), List.of(), List.of(message)));
            if (e instanceof BizException bizException) {
                throw bizException;
            }
            throw new BizException(StatusEnum.UNEXPECT_ERROR, message);
        }
    }

    private String errorMessage(Exception e) {
        if (e instanceof BizException bizException && !StringUtils.isBlank(bizException.getMsg())) {
            return bizException.getMsg();
        }
        return e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
    }

    public GatherVo gatherInfo(GatherReq req, GatherFileBo file) throws IOException {
        return gatherInfo(req, file, GatherDraftContext.empty());
    }

    public GatherVo gatherInfo(GatherReq req, GatherFileBo file, GatherDraftContext context) throws IOException {
        GatherTargetTypeEnum targetTypeEnum = IntBaseEnum.getEnumByCode(GatherTargetTypeEnum.class, req.type());
        Assert.notNull(targetTypeEnum, "不支持的gather类型");
        String model = req.model();
        Function<GatherReq, List<GatherOcDraftBo>> func = switch (targetTypeEnum) {
            case TEXT -> gatherByText(model, req.content());
            case HTML_TEXT -> gatherByHtmlText(model, req.content());
            case HTTP_URL -> gatherByHttpUrl(model, req.content());
            case IMAGE -> gatherByImg(model, file);
            default -> null;
        };
        if (func == null) {
            return gatherFileInfo(req, file, context);
        }
        List<GatherOcDraftBo> list = func.apply(req);
        log.info("大模型提取后业务对象是：{}", JsonUtil.toStr(list));
        GatherVo res = gatherService.saveDraftDataList(DraftConvert.convert(list), context);
        return res;
    }

    /**
     * 传入文件进行数据提取的场景，我们直接读取文件，做好分页调用大模型，避免大模型返回数据截断的问题
     * 1. 支持传入 csv, excel；
     * 2. 推荐的输入是第一行为标题，第二行开始为数据
     *
     * @return
     * @throws IOException
     */
    public GatherVo gatherFileInfo(GatherReq req, GatherFileBo file) throws IOException {
        return gatherFileInfo(req, file, GatherDraftContext.empty());
    }

    public GatherVo gatherFileInfo(GatherReq req, GatherFileBo file, GatherDraftContext context) throws IOException {
        Pair<String, List<String>> pair;
        if (req.type().equals(GatherTargetTypeEnum.CSV_FILE.getValue())) {
            pair = parseContentsFromCsv(file);
        } else if (req.type().equals(GatherTargetTypeEnum.EXCEL_FILE.getValue())) {
            pair = parseContentsFromExcel(file);
        } else {
            throw new BizException(StatusEnum.UNEXPECT_ERROR, "不支持的文件类型");
        }

        // AI-GENERATED AIDEV-NOTE: 全部批次校验通过才保存，失败不得被吞掉后标记成功。
        List<GatherOcDraftBo> list = gatherAiAgent.gatherRecords(req.model(),
                GatherSourceRecords.rows(pair.getFirst(), pair.getSecond())).requireComplete();
        return gatherService.saveDraftDataList(DraftConvert.convert(list), context);
    }

    private List<OcDraftEntity> safeDraftList(List<OcDraftEntity> list) {
        return list == null ? List.of() : list;
    }


    /**
     * 直接根据用户传入的文本，进行解析获取职位信息
     *
     * @return
     */
    private Function<GatherReq, List<GatherOcDraftBo>> gatherByText(String model, String txt) {
        if (commonDictService.prodEnv() && StringUtils.isBlank(txt)) {
            // 生产环境，传入了空字符串，不做任何处理
            return (s) -> List.of();
        } else {
            // 开发测试时，不传就用默认的输入参数
            final String testText = StringUtils.isBlank(txt) ? ResourceUtil.readUtf8Str("data/oc.txt") : txt;
            return (s) -> {
                return gatherAiAgent.gatherByText(model, testText);
            };
        }
    }

    private Function<GatherReq, List<GatherOcDraftBo>> gatherByHtmlText(String model, String text) {
        if (commonDictService.prodEnv() && StringUtils.isBlank(text)) {
            // 生产环境，传入了空字符串，不做任何处理
            return (s) -> List.of();
        }

        String testText = StringUtils.isBlank(text) ? ResourceUtil.readUtf8Str("data/oc-html.txt") : text;
        return (s) -> {
            return gatherAiAgent.gatherByAutoSplit(model, testText);
        };
    }

    private Function<GatherReq, List<GatherOcDraftBo>> gatherByHttpUrl(String model, String filePath) {
        if (!filePath.matches(".*https?://[\\w.-]+(?:\\.[\\w\\.-]+)+[/\\w\\.-]*.*")) {
            throw new BizException(StatusEnum.UNEXPECT_ERROR, "请输入包含合法url地址的文本");
        }

        String sourceUrl = extractFirstUrl(filePath);
        if (isGfJianliOfferUrl(sourceUrl)) {
            return (s) -> gatherFromGfJianli(sourceUrl);
        }

        return (s) -> {
            // AI-GENERATED AIDEV-NOTE: 先取得完整网页，拒绝把 HTTP 错误页当作空采集结果。
            try (var response = HttpRequest.get(sourceUrl).timeout(20_000).execute()) {
                if (!response.isOk() || StringUtils.isBlank(response.body())) {
                    throw new BizException(StatusEnum.UNEXPECT_ERROR, "网页获取失败：HTTP " + response.getStatus());
                }
                String input = "<header>采集要求：" + org.jsoup.nodes.Entities.escape(filePath)
                        + "</header>" + response.body();
                return gatherAiAgent.gatherByAutoSplit(model, input);
            }
        };
    }

    private String extractFirstUrl(String text) {
        Matcher matcher = Pattern.compile("https?://\\S+").matcher(text);
        if (!matcher.find()) {
            return text;
        }
        return matcher.group().replaceAll("[，。,;；]+$", "");
    }

    private boolean isGfJianliOfferUrl(String url) {
        try {
            String host = URI.create(url).getHost();
            return host != null && (GF_JIANLI_HOST.equals(host) || host.endsWith("." + GF_JIANLI_HOST));
        } catch (Exception e) {
            return false;
        }
    }

    private List<GatherOcDraftBo> gatherFromGfJianli(String sourceUrl) {
        String token = queryParam(sourceUrl, "token");
        HttpRequest request = HttpRequest.get(GF_JIANLI_API)
                .header("User-Agent", "Mozilla/5.0 JobClaw")
                .form("page", "1")
                .form("limit", String.valueOf(GF_JIANLI_IMPORT_LIMIT))
                .timeout(15_000);
        if (!StringUtils.isBlank(token)) {
            request.header("token", token);
        }

        String response = request.execute().body();
        if (StringUtils.isBlank(response)) {
            throw new BizException(StatusEnum.UNEXPECT_ERROR, "GF简历接口无响应");
        }

        JsonNode root = JsonUtil.toJsonNode(response);
        if (root.path("code").asInt() != 200) {
            throw new BizException(StatusEnum.UNEXPECT_ERROR, "GF简历接口返回异常:" + root.path("msg").asText("未知错误"));
        }

        JsonNode listNode = root.path("data").path("list");
        if (!listNode.isArray()) {
            throw new BizException(StatusEnum.UNEXPECT_ERROR, "GF简历接口返回数据缺少list");
        }

        List<GatherOcDraftBo> list = new ArrayList<>();
        for (JsonNode row : listNode) {
            GatherOcDraftBo bo = toGfJianliDraft(row, sourceUrl);
            if (bo != null) {
                list.add(bo);
            }
        }
        log.info("GF简历链接解析完成: url={}, credential={}, cnt={}", maskSensitiveUrl(sourceUrl), StringUtils.isBlank(token) ? "public" : "token", list.size());
        return list;
    }

    private String queryParam(String url, String key) {
        String query = URI.create(url).getRawQuery();
        if (StringUtils.isBlank(query)) {
            return "";
        }
        for (String item : query.split("&")) {
            String[] pair = item.split("=", 2);
            String itemKey = decode(pair[0]);
            if (key.equals(itemKey)) {
                return pair.length > 1 ? decode(pair[1]) : "";
            }
        }
        return "";
    }

    private String maskSensitiveUrl(String url) {
        if (StringUtils.isBlank(url)) {
            return "";
        }
        return url.replaceAll("(?i)(token=)[^&\\s]+", "$1***");
    }

    private String decode(String value) {
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }

    private GatherOcDraftBo toGfJianliDraft(JsonNode row, String sourceUrl) {
        String company = text(row, "company");
        String title = text(row, "title");
        if (StringUtils.isBlank(company) && StringUtils.isBlank(title)) {
            return null;
        }

        String referralLink = firstNotBlank(text(row, "referralMethod"), sourceUrl);
        String positions = firstNotBlank(text(row, "positions"), title);
        String remarks = buildGfJianliRemarks(row, title);
        return new GatherOcDraftBo(
                company,
                inferCompanyType(company, text(row, "industry")),
                text(row, "industry"),
                text(row, "workLocation"),
                inferRecruitmentType(title, text(row, "infoType")),
                inferRecruitmentTarget(title, text(row, "infoType")),
                positions,
                firstNotBlank(text(row, "recruitmentStatus"), text(row, "progressCheck"), "未投递"),
                normalizeDate(firstNotBlank(text(row, "recordTime"), text(row, "createTime"))),
                normalizeDate(text(row, "expirationTime")),
                referralLink,
                referralLink,
                firstNotBlank(text(row, "referralCode"), "-"),
                remarks,
                null,
                null,
                null
        );
    }

    private String text(JsonNode node, String key) {
        JsonNode value = node.path(key);
        if (value.isMissingNode() || value.isNull()) {
            return "";
        }
        return value.asText("").trim();
    }

    private String firstNotBlank(String... values) {
        for (String value : values) {
            if (!StringUtils.isBlank(value)) {
                return value;
            }
        }
        return "";
    }

    private String inferCompanyType(String company, String industry) {
        String source = firstNotBlank(company, "") + " " + firstNotBlank(industry, "");
        if (source.matches(".*(银行|农信|农商).*")) {
            return "银行";
        }
        if (source.matches(".*(大学|学院|学校).*")) {
            return "学校";
        }
        if (source.matches(".*(中航|航空|航天|国网|国家|中国|中铁|中建|中核|中电|兵器|船舶|光电).*")) {
            return "央国企";
        }
        return "民企";
    }

    private String inferRecruitmentType(String title, String infoType) {
        String source = firstNotBlank(title, "") + " " + firstNotBlank(infoType, "");
        if (source.contains("实习")) {
            return source.contains("暑期") ? "暑期实习" : "日常实习";
        }
        if (source.contains("春招")) {
            return "春招";
        }
        if (source.contains("补录")) {
            return "补录";
        }
        if (source.contains("提前批") || source.contains("2027")) {
            return "秋招提前批";
        }
        return "秋招";
    }

    private String inferRecruitmentTarget(String title, String infoType) {
        String source = firstNotBlank(title, "") + " " + firstNotBlank(infoType, "");
        Matcher matcher = GRADUATION_YEAR_PATTERN.matcher(source);
        while (matcher.find()) {
            String year = matcher.group(1);
            if ("2025".equals(year) || "2026".equals(year) || "2027".equals(year)) {
                return year + "年毕业生";
            }
        }
        if (source.contains("实习")) {
            return "其他";
        }
        return "2026年毕业生";
    }

    private String normalizeDate(String dateText) {
        if (StringUtils.isBlank(dateText)) {
            return "招满为止";
        }
        String date = dateText.length() >= 10 ? dateText.substring(0, 10) : dateText;
        if (date.matches("\\d{4}-\\d{2}-\\d{2}")) {
            return date.substring(0, 4) + "年" + date.substring(5, 7) + "月" + date.substring(8, 10) + "日";
        }
        return dateText;
    }

    private String buildGfJianliRemarks(JsonNode row, String title) {
        List<String> parts = new ArrayList<>();
        addIfPresent(parts, title);
        addIfPresent(parts, text(row, "remarks"));
        addIfPresent(parts, text(row, "recruitmentRemarks"));
        addIfPresent(parts, text(row, "mainBusiness"));
        addIfPresent(parts, "GF简历ID:" + text(row, "id"));
        return String.join("；", parts);
    }

    private void addIfPresent(List<String> parts, String value) {
        if (!StringUtils.isBlank(value) && !"GF简历ID:".equals(value)) {
            parts.add(value);
        }
    }

    private Function<GatherReq, List<GatherOcDraftBo>> gatherByImg(String model, GatherFileBo file) throws IOException {
        byte[] bytes;
        MimeType type;
        if (file == null && commonDictService.prodEnv()) {
            // 生产环境，没有传图片，直接返回空
            return (s) -> List.of();
        }


        if (file == null) {
            // 使用默认的图片进行兜底
            Resource resource = new ClassPathResource("data/oc-img2.jpg");
            bytes = resource.getContentAsByteArray();
            type = MimeTypeUtils.IMAGE_JPEG;
        } else {
            bytes = file.bytes();
            type = MimeTypeUtils.parseMimeType(file.contentType());
        }
        return (s) -> {
            return gatherAiAgent.gatherByImgAutoSplit(model, type, bytes);
        };
    }

    private Pair<String, List<String>> parseContentsFromCsv(GatherFileBo file) throws IOException {
        byte[] bytes;
        if (file == null) {
            // 使用默认的图片进行兜底
            Resource resource = new ClassPathResource("data/oc.csv");
            bytes = resource.getContentAsByteArray();
        } else {
            bytes = file.bytes();
        }

        // AI-GENERATED AIDEV-NOTE: CSV 引号内换行属于字段内容，不能按物理行拆分。
        var records = GatherSourceRecords.csv(new String(bytes, StandardCharsets.UTF_8));
        return Pair.of("CSV 各记录已包含表头", records.stream().map(record -> record.content()).toList());
    }

    private Pair<String, List<String>> parseContentsFromExcel(GatherFileBo file) throws IOException {
        byte[] bytes;
        if (file == null) {
            // 使用默认的图片进行兜底
            Resource resource = new ClassPathResource("data/oc.xlsx");
            bytes = resource.getContentAsByteArray();
        } else {
            bytes = file.bytes();
        }

        final String[] title = new String[1];
        List<String> contents = new ArrayList<>();
        ExcelReader reader = FastExcel.read(new ByteArrayInputStream(bytes), new ReadListener<LinkedHashMap>() {
            @Override
            public void invokeHead(Map headMap, AnalysisContext context) {
                StringBuilder builder = new StringBuilder();
                for (Object entry : headMap.entrySet()) {
                    ReadCellData val = (ReadCellData) ((Map.Entry) entry).getValue();
                    builder.append(val.getStringValue());
                    builder.append(",");
                }
                title[0] = builder.toString();
            }

            @Override
            public void invoke(LinkedHashMap map, AnalysisContext analysisContext) {
                StringBuilder builder = new StringBuilder();
                for (Object entry : map.entrySet()) {
                    String val = (String) ((Map.Entry) entry).getValue();
                    builder.append(val);
                    builder.append(",");
                }
                contents.add(builder.toString());
            }

            @Override
            public void doAfterAllAnalysed(AnalysisContext analysisContext) {
            }
        }).build();
        reader.readAll();
        reader.close();

        return Pair.of(title[0], contents);
    }
}
