package com.git.hui.jobclaw.channels.websocket;

import com.git.hui.jobclaw.core.apis.ResVo;
import com.git.hui.jobclaw.core.apis.context.ReqInfoContext;
import jakarta.servlet.http.HttpServletResponse;
import com.git.hui.jobclaw.core.apis.context.UserRoleEnum;
import com.git.hui.jobclaw.core.apis.permission.Permission;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;

/**
 * WebSocket 文件上传 REST 端点
 * <p>
 * 文件通过 HTTP 上传，WebSocket 消息中携带文件引用 ID。
 */
@Slf4j
@RestController
@RequestMapping("/api/ws")
public class WsChannelController {

    private final Resource agentWorkspace;

    public WsChannelController(@Value("${agent.workspace}") Resource agentWorkspace) {
        this.agentWorkspace = agentWorkspace;
    }

    @PostMapping("/upload")
    @Permission(role = UserRoleEnum.NORMAL)
    public ResVo<Map<String, Object>> uploadFile(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "userId", required = false) String userId,
            HttpServletResponse response) {

        // AI-GENERATED AIDEV-NOTE: 上传目录只能由登录身份决定，不能信任客户端 userId。
        var requestInfo = ReqInfoContext.getReqInfo();
        if (requestInfo == null || requestInfo.getUserId() == null) {
            response.setStatus(401);
            return ResVo.fail(401, "请先登录");
        }
        String authenticatedUserId = String.valueOf(requestInfo.getUserId());
        if (userId != null && !authenticatedUserId.equals(userId)) {
            response.setStatus(403);
            return ResVo.fail(403, "不能为其他用户上传文件");
        }
        userId = authenticatedUserId;
        if (file.isEmpty()) {
            response.setStatus(400);
            return ResVo.fail(400, "不能上传空文件");
        }

        String fileId = UUID.randomUUID().toString();
        String originalFilename = file.getOriginalFilename();
        String extension = "";
        if (originalFilename != null && originalFilename.contains(".")) {
            String candidate = originalFilename.substring(originalFilename.lastIndexOf("."));
            if (candidate.matches("\\.[a-zA-Z0-9]{1,10}")) extension = candidate;
        }
        String savedFilename = fileId + extension;

        try {
            Path uploadDir = agentWorkspace.getFile().toPath()
                    .toAbsolutePath().normalize().resolve("ws").resolve(userId);
            Files.createDirectories(uploadDir);

            Path filePath = uploadDir.resolve(savedFilename);
            file.transferTo(filePath.toFile());

            log.info("[WS-Upload] File uploaded: userId={}, fileId={}, size={}",
                    userId, fileId, file.getSize());

            // AI-GENERATED AIDEV-NOTE: 直接返回 ResVo，避免全局响应封装导致 data 嵌套。
            return ResVo.success(Map.of(
                            "fileId", fileId,
                            "fileName", originalFilename != null ? originalFilename : savedFilename,
                            "mimeType", file.getContentType() != null ? file.getContentType() : "application/octet-stream",
                            "size", file.getSize()
            ));
        } catch (IOException e) {
            log.error("[WS-Upload] Failed to upload file for user: {}", userId, e);
            response.setStatus(500);
            return ResVo.fail(500, "文件上传失败");
        }
    }
}
