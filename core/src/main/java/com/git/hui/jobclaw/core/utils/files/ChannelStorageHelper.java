package com.git.hui.jobclaw.core.utils.files;

import cn.hutool.core.io.IoUtil;
import cn.hutool.http.HttpUtil;
import com.git.hui.jobclaw.core.bizexception.BizException;
import com.git.hui.jobclaw.core.bizexception.StatusEnum;
import com.git.hui.jobclaw.core.utils.FileUtils;
import com.git.hui.jobclaw.core.utils.MD5Utils;
import com.github.hui.quick.plugin.base.file.FileReadUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Random;

/**
 * @date 2025/7/18
 */
@Slf4j
@Component
public class ChannelStorageHelper {
    private Path parent;

    public ChannelStorageHelper(@Value("${agent.workspace}") Resource agentWorkspace) {
        try {
            Path workspacePath = agentWorkspace.getFile().toPath();
            this.parent = workspacePath.resolve("conversations");
        } catch (Exception e) {
            throw new RuntimeException("Failed to resolve workspace path", e);
        }
    }


    public String autoDownloadFile(String jobClawUserId, String channel, String url, String fileType) {
        var inputBytes = loadFileBytes(url);
        return autoSaveFile(jobClawUserId, channel, inputBytes, fileType);
    }


    public String autoSaveFile(String jobClawUserId, String channel, byte[] inputBytes, String fileType) {
        var tmpSavePath = parent.resolve(jobClawUserId).resolve("files");
        FileUtils.ensureDirectory(tmpSavePath);

        String fileName = channel + "_" + MD5Utils.md5(inputBytes) + "." + fileType;
        Path filePath = tmpSavePath.resolve(fileName);

        try {
            Files.write(filePath, inputBytes);
            log.info("File saved to: {}", filePath.toAbsolutePath());
            return filePath.toAbsolutePath().toString();
        } catch (IOException e) {
            log.error("Failed to save file to {}", filePath, e);
            throw new BizException(StatusEnum.UNEXPECT_ERROR, "附件存储异常!");
        }
    }

    /**
     * 读取资源内容
     *
     * @param path
     * @return
     */
    public InputStream loadFile(String path) {
        try {
            return FileReadUtil.createByteRead(path);
        } catch (java.nio.file.NoSuchFileException e) {
            throw new BizException(StatusEnum.RECORDS_NOT_EXISTS, "文件不存在: " + path);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public byte[] loadFileBytes(String path) {
        if (path.startsWith("http")) {
            return HttpUtil.downloadBytes(path);
        }

        return IoUtil.readBytes(loadFile(path));
    }

}
