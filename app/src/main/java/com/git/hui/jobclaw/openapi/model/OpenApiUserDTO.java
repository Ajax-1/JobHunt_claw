package com.git.hui.jobclaw.openapi.model;

import lombok.Data;

import java.io.Serializable;

@Data
public class OpenApiUserDTO implements Serializable {
    private static final long serialVersionUID = 4663622879892017339L;
    /**
     * 用户id
     */
    private Long userId;

    /**
     * 用户昵称
     */
    private String userName;

    /**
     * 登录用户名
     */
    private String loginName;

    /**
     * 用户角色 admin, normal
     */
    private String role;

    /**
     * 用户图像
     */
    private String photo;

    /**
     * 用户的邮箱
     */
    private String email;

    /**
     * 个人简介
     */
    private String profile;
    /**
     * 职位
     */
    private String position;

    /**
     * 公司
     */
    private String company;


    private String wxId;

    /**
     * 星球id
     */
    private String zsxqId;

    /**
     * 星球到期时间(秒)
     */
    private Long zsxqExpireTime;
}