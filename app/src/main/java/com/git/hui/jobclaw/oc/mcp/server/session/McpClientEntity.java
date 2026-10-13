package com.git.hui.jobclaw.oc.mcp.server.session;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.Data;
import lombok.experimental.Accessors;
import org.hibernate.annotations.DynamicUpdate;

import java.util.Date;

/**
 * @date 2025/10/14
 */
@Data
@Accessors(chain = true)
// 动态更新字段
@DynamicUpdate
@Entity(name = "mcp_client")
public class McpClientEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "session_id")
    private String sessionId;

    @Column(name = "user_id")
    private Long userId;

    /**
     * 对应的是用户发送 initialnize 方法时的传递参数
     */
    @Column(name = "init_info")
    private String initInfo;

    /**
     * 对应的是用户发送 notifactions/initialized 方法时的参数
     */
    @Column(name = "notify_info")
    private String notifyInfo;

    @Column(name = "create_time")
    private Date createTime;
}
