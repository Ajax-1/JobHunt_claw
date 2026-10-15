package com.git.hui.jobclaw.gather.service;

import cn.hutool.core.io.IoUtil;
import cn.idev.excel.util.BooleanUtils;
import com.git.hui.jobclaw.core.bizexception.BizException;
import com.git.hui.jobclaw.core.bizexception.StatusEnum;
import com.git.hui.jobclaw.constants.gather.GatherRunnerTypes;
import com.git.hui.jobclaw.constants.gather.GatherTargetTypeEnum;
import com.git.hui.jobclaw.constants.gather.GatherTaskStateEnum;
import com.git.hui.jobclaw.core.utils.SpringUtil;
import com.git.hui.jobclaw.core.providers.ModelConfig;
import com.git.hui.jobclaw.core.providers.ModelProviders;
import com.git.hui.jobclaw.gather.convert.TaskConvert;
import com.git.hui.jobclaw.gather.dao.entity.GatherSourceEntity;
import com.git.hui.jobclaw.gather.dao.entity.GatherTaskEntity;
import com.git.hui.jobclaw.gather.dao.repository.GatherTaskRepository;
import com.git.hui.jobclaw.gather.model.GatherFileBo;
import com.git.hui.jobclaw.gather.model.GatherTaskProcessBo;
import com.git.hui.jobclaw.gather.model.GatherTaskResultBo;
import com.git.hui.jobclaw.gather.model.GatherTaskSaveBo;
import com.git.hui.jobclaw.gather.model.TaskChangeListener;
import com.git.hui.jobclaw.gather.service.helper.LocalStorageHelper;
import com.git.hui.jobclaw.util.FileTypeUtil;
import com.git.hui.jobclaw.core.utils.json.IntBaseEnum;
import com.git.hui.jobclaw.core.utils.json.JsonUtil;
import com.git.hui.jobclaw.core.apis.PageListVo;
import com.git.hui.jobclaw.web.model.req.GatherTaskSearchReq;
import com.git.hui.jobclaw.web.model.res.TaskVo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.Date;
import java.util.List;
import java.util.Objects;

/**
 * @date 2025/7/18
 */
@Slf4j
@Service
public class GatherTaskService {
    private final GatherTaskRepository gatherTaskRepository;

    private final LocalStorageHelper localStorageHelper;

    private final GatherSourceService gatherSourceService;

    private final ModelProviders modelProviders;

    @Autowired
    public GatherTaskService(GatherTaskRepository gatherTaskRepository,
                             LocalStorageHelper localStorageHelper,
                             GatherSourceService gatherSourceService,
                             ModelProviders modelProviders) {
        this.gatherTaskRepository = gatherTaskRepository;
        this.localStorageHelper = localStorageHelper;
        this.gatherSourceService = gatherSourceService;
        this.modelProviders = modelProviders;
    }


    public String tempSaveInputFile(MultipartFile file) throws IOException {
        return localStorageHelper.saveFile(file.getInputStream(), FileTypeUtil.contentType2fileType(file.getContentType()));
    }

    public GatherFileBo loadTmpFile(String path) {
        try {
            InputStream inputStream = localStorageHelper.loadFile(path);
            byte[] bytes = IoUtil.readBytes(inputStream);
            String fileType = path.substring(path.lastIndexOf(".") + 1);
            // 根据文件类型，构建对应的contentType
            String contentType = FileTypeUtil.getFileType(fileType);
            return new GatherFileBo(bytes, contentType, path);
        } catch (Exception e) {
            log.error("加载临时文件失败: {}", path, e);
            throw e;
        }
    }

    /**
     * 添加任务
     *
     * @param taskBo 任务
     * @return 任务实体
     */
    public GatherTaskEntity addTask(GatherTaskSaveBo taskBo) throws Exception {
        String file = switch (taskBo.type()) {
            case IMAGE, CSV_FILE, EXCEL_FILE -> tempSaveInputFile(taskBo.file());
            default -> null;
        };

        String persistedContent = file != null ? file : taskBo.content();
        String runnerType = GatherRunnerTypes.normalize(taskBo.runnerType());
        String model = resolveDefaultModel(taskBo.type(), taskBo.model());
        GatherSourceEntity source = gatherSourceService.resolveSource(taskBo.type().getValue(), model, persistedContent, runnerType);
        GatherTaskEntity taskEntity = buildTask(taskBo.type().getValue(), model, persistedContent, source, runnerType);
        gatherTaskRepository.saveAndFlush(taskEntity);
        gatherSourceService.attachTask(source.getId(), taskEntity.getId());

        // 发布任务新增事件，主动触发一次任务调度
        SpringUtil.getContext().publishEvent(new TaskChangeListener(this, taskEntity.getId(), GatherTaskStateEnum.INIT));
        return taskEntity;
    }

    public GatherTaskEntity directAddTask(GatherTaskSaveBo taskBo) throws IOException {
        String file = null;
        if (taskBo.file() != null) {
            file = tempSaveInputFile(taskBo.file());
        }

        String persistedContent = file != null ? file : taskBo.content();
        // AI-GENERATED AIDEV-NOTE: 空串兼容 MySQL 非空约束，分类后再选择文本或视觉模型。
        String model = Objects.requireNonNullElse(taskBo.model(), "");
        GatherSourceEntity source = gatherSourceService.resolveSource(0, model, persistedContent, GatherRunnerTypes.AGENT);
        GatherTaskEntity taskEntity = buildTask(0, model, persistedContent, source, GatherRunnerTypes.AGENT);
        gatherTaskRepository.saveAndFlush(taskEntity);
        gatherSourceService.attachTask(source.getId(), taskEntity.getId());
        return taskEntity;
    }

    public GatherTaskEntity addTaskFromSource(Long sourceId) {
        GatherSourceEntity source = gatherSourceService.getSource(sourceId);
        String runnerType = GatherRunnerTypes.normalize(source.getOwnerType());
        GatherTaskEntity taskEntity = buildTask(source.getType(), source.getModel(), source.getContent(), source, runnerType);
        gatherTaskRepository.saveAndFlush(taskEntity);
        gatherSourceService.attachTask(source.getId(), taskEntity.getId());
        SpringUtil.getContext().publishEvent(new TaskChangeListener(this, taskEntity.getId(), GatherTaskStateEnum.INIT));
        return taskEntity;
    }

    private GatherTaskEntity buildTask(Integer type, String model, String content, GatherSourceEntity source, String runnerType) {
        return new GatherTaskEntity()
                .setType(type == null ? 0 : type)
                .setModel(model)
                .setContent(content)
                .setSourceId(source == null ? null : source.getId())
                .setSourceVersion(source == null ? null : source.getVersion())
                .setRunnerType(GatherRunnerTypes.normalize(runnerType))
                .setState(GatherTaskStateEnum.INIT.getValue())
                .setProcessTime(null)
                .setCnt(0)
                .setCreateTime(new Date())
                .setUpdateTime(new Date());
    }

    /**
     * 获取一个未处理的任务，开始执行
     *
     * @return
     */
    public GatherTaskProcessBo pickUnProcessTaskToProcess() {
        GatherTaskEntity tasks = gatherTaskRepository.findFirstByStateOrderByCreateTimeAsc(GatherTaskStateEnum.INIT.getValue());
        if (tasks == null || tasks.getId() == null) {
            // 没找到的场景下不需要继续处理了
            return null;
        }

        return markTaskProcessing(tasks);
    }

    /**
     * 标记任务为处理中
     *
     * @param tasks
     * @return
     */
    public GatherTaskProcessBo markTaskProcessing(GatherTaskEntity tasks) {
        // 如果找到了，则尝试更新状态为处理中
        tasks.setState(GatherTaskStateEnum.PROCESSING.getValue());
        // 处理次数+1
        tasks.setCnt(tasks.getCnt() + 1);
        GatherTargetTypeEnum targetType = IntBaseEnum.getEnumByCode(GatherTargetTypeEnum.class, tasks.getType());
        String model = resolveDefaultModel(targetType, tasks.getModel());
        if (!Objects.equals(tasks.getModel(), model)) {
            tasks.setModel(model);
        }
        tasks.setProcessTime(new Date());
        tasks.setUpdateTime(new Date());
        gatherTaskRepository.saveAndFlush(tasks);
        gatherSourceService.updateTypeAndModel(tasks.getSourceId(), tasks.getType(), model);

        // 发送任务状态变更消息
        SpringUtil.getContext().publishEvent(new TaskChangeListener(this, tasks.getId(), GatherTaskStateEnum.PROCESSING));
        return new GatherTaskProcessBo(tasks.getId(), targetType, model, tasks.getContent(), tasks.getSourceId(), tasks.getSourceVersion(), tasks.getRunnerType());
    }

    private String resolveDefaultModel(GatherTargetTypeEnum type, String model) {
        if (model != null && !model.isBlank()) {
            return model;
        }
        if (type == null) {
            return model;
        }
        ModelConfig.ModelType modelType = type == GatherTargetTypeEnum.IMAGE ? ModelConfig.ModelType.VISION : ModelConfig.ModelType.TEXT;
        return modelProviders.getDefaultModelSelection(modelType);
    }

    /**
     * 保存任务处理结果
     *
     * @param taskId 任务id
     * @param res    处理结果
     */
    public void saveTaskResult(Long taskId, GatherTaskResultBo res) {
        GatherTaskEntity tasks = gatherTaskRepository.findById(taskId).orElse(null);
        if (tasks == null) {
            return;
        }

        String content = JsonUtil.toStr(res);
        tasks.setResult(content);
        GatherTaskStateEnum state = res.isSuccess() ? GatherTaskStateEnum.SUCCEED : GatherTaskStateEnum.FAILED;
        tasks.setState(state.getValue());
        tasks.setUpdateTime(new Date());

        gatherTaskRepository.saveAndFlush(tasks);
        gatherSourceService.refreshResult(tasks.getSourceId(), tasks.getId(), res);
        // 发送任务状态变更消息
        SpringUtil.getContext().publishEvent(new TaskChangeListener(this, tasks.getId(), state));
    }

    /**
     * 将状态设置为未处理，用于再次调度，适用于失败或者处理结果不满足预期的场景
     *
     * @param taskId
     */
    public boolean resetTaskState(Long taskId) {
        GatherTaskEntity tasks = gatherTaskRepository.findById(taskId).orElse(null);
        if (tasks == null) {
            throw new BizException(StatusEnum.RECORDS_NOT_EXISTS, taskId + "非法");
        }

        tasks.setState(GatherTaskStateEnum.INIT.getValue());
        tasks.setUpdateTime(new Date());
        gatherTaskRepository.saveAndFlush(tasks);
        // 发送任务状态变更消息
        SpringUtil.getContext().publishEvent(new TaskChangeListener(this, tasks.getId(), GatherTaskStateEnum.INIT));
        return true;
    }

    /** 删除已结束的采集任务，避免失败记录长期占据任务队列。 */
    public boolean deleteTask(Long taskId) {
        GatherTaskEntity task = gatherTaskRepository.findById(taskId).orElse(null);
        if (task == null) {
            throw new BizException(StatusEnum.RECORDS_NOT_EXISTS, taskId + "非法");
        }
        if (Objects.equals(task.getState(), GatherTaskStateEnum.PROCESSING.getValue())) {
            throw new BizException(StatusEnum.ILLEGAL_ARGUMENTS_MIXED, "任务处理中，暂不能删除");
        }
        gatherTaskRepository.delete(task);
        gatherTaskRepository.flush();
        return true;
    }

    /**
     * 条件查询任务列表
     *
     * @param req 查询条件
     * @return 任务列表
     */

    public PageListVo<TaskVo> searchList(GatherTaskSearchReq req) {

        req.autoInitPage();
        PageListVo<GatherTaskEntity> page = gatherTaskRepository.findList(req);
        List<TaskVo> list = TaskConvert.toVo(page.getList(), (s) -> {
            GatherTargetTypeEnum target = IntBaseEnum.getEnumByCode(GatherTargetTypeEnum.class, s.getType());
            if (target != null && BooleanUtils.isTrue(target.getFile())) {
                return localStorageHelper.buildFileHttpUrl(s.getContent());
            } else {
                return s.getContent();
            }
        });
        return PageListVo.of(list, page.getTotal(), page.getPage(), page.getSize());
    }

    public GatherTaskEntity getTask(Long taskId) {
        GatherTaskEntity tasks = gatherTaskRepository.findById(taskId).orElse(null);
        if (tasks == null) {
            throw new BizException(StatusEnum.RECORDS_NOT_EXISTS, taskId + "非法");
        }
        return tasks;
    }
}
