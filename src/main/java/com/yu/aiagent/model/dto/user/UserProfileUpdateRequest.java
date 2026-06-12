package com.yu.aiagent.model.dto.user;

import lombok.Data;

import java.io.Serializable;

/**
 * 当前用户资料更新请求
 */
@Data
public class UserProfileUpdateRequest implements Serializable {

    /**
     * 用户昵称
     */
    private String userName;

    /**
     * 用户头像
     */
    private String userAvatar;

    /**
     * 用户简介
     */
    private String userProfile;

    /**
     * 当前密码，修改密码时必填
     */
    private String oldPassword;

    /**
     * 新密码，修改密码时必填
     */
    private String newPassword;

    /**
     * 确认新密码，修改密码时必填
     */
    private String checkPassword;

    private static final long serialVersionUID = 1L;
}
