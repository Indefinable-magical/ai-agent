package com.yu.aiagent.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yu.aiagent.common.DeleteRequest;
import com.yu.aiagent.model.dto.user.UserAddRequest;
import com.yu.aiagent.model.dto.user.UserProfileUpdateRequest;
import com.yu.aiagent.model.dto.user.UserQueryRequest;
import com.yu.aiagent.model.dto.user.UserUpdateRequest;
import com.yu.aiagent.model.entity.User;
import com.baomidou.mybatisplus.extension.service.IService;
import com.yu.aiagent.model.vo.LoginUserVO;
import com.yu.aiagent.model.vo.UserVO;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
* @author 86199
* @description 针对表【user(用户)】的数据库操作Service
* @createDate 2026-05-06 14:28:35
*/
public interface UserService extends IService<User> {

    /**
     * 用户注册
     *
     * @param userAccount   用户账户
     * @param userPassword  用户密码
     * @param checkPassword 校验密码
     * @return 新用户 id
     */
    long userRegister(String userAccount, String userPassword, String checkPassword);

    /**
     * 获取脱敏的已登录用户信息
     *
     * @return
     */
    LoginUserVO getLoginUserVO(User user);

    /**
     * 用户登录
     *
     * @param userAccount  用户账户
     * @param userPassword 用户密码
     * @param request
     * @return 脱敏后的用户信息
     */
    LoginUserVO userLogin(String userAccount, String userPassword, HttpServletRequest request);

    /**
     * 获取当前登录用户
     *
     * @param request
     * @return
     */
    User getLoginUser(HttpServletRequest request);

    /**
     * 当前用户更新自己的资料
     *
     * @param userProfileUpdateRequest 用户资料更新请求
     * @param request                  请求对象
     * @return 更新后的登录用户信息
     */
    LoginUserVO updateMyProfile(UserProfileUpdateRequest userProfileUpdateRequest, HttpServletRequest request);

    /**
     * 上传当前登录用户头像文件
     *
     * @param file    头像文件
     * @param request 请求对象
     * @return 可访问的头像地址
     */
    String uploadMyAvatar(MultipartFile file, HttpServletRequest request);

    /**
     * 获取脱敏后的用户信息
     *
     * @param user 用户信息
     * @return
     */
    UserVO getUserVO(User user);

    /**
     * 获取脱敏后的用户信息（分页）
     *
     * @param userList 用户列表
     * @return
     */
    List<UserVO> getUserVOList(List<User> userList);

    /**
     * 用户注销
     *
     * @param request
     * @return 退出登录是否成功
     */
    boolean userLogout(HttpServletRequest request);

    /**
     * 根据查询条件构造数据查询参数
     *
     * @param userQueryRequest
     * @return
     */
    QueryWrapper getQueryWrapper(UserQueryRequest userQueryRequest);

    /**
     * 管理员创建用户
     *
     * @param userAddRequest 用户新增请求
     * @return 新用户 id
     */
    Long addUser(UserAddRequest userAddRequest);

    /**
     * 管理员根据 id 获取用户
     *
     * @param id 用户 id
     * @return 用户实体
     */
    User getUserById(long id);

    /**
     * 根据 id 获取脱敏用户信息
     *
     * @param id 用户 id
     * @return 脱敏用户信息
     */
    UserVO getUserVOById(long id);

    /**
     * 管理员删除用户
     *
     * @param deleteRequest 删除请求
     * @return 是否删除成功
     */
    Boolean deleteUser(DeleteRequest deleteRequest);

    /**
     * 管理员更新用户
     *
     * @param userUpdateRequest 用户更新请求
     * @param request           请求对象，用于识别当前登录管理员
     * @return 是否更新成功
     */
    Boolean updateUser(UserUpdateRequest userUpdateRequest, HttpServletRequest request);

    /**
     * 管理员分页查询脱敏用户列表
     *
     * @param userQueryRequest 查询请求
     * @return 用户分页 VO
     */
    Page<UserVO> listUserVOByPage(UserQueryRequest userQueryRequest);

    /**
     * 加密
     *
     * @param userPassword 用户密码
     * @return 加密后的用户密码
     */
    String getEncryptPassword(String userPassword);

}
