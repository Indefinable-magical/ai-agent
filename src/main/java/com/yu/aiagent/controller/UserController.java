package com.yu.aiagent.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yu.aiagent.annotation.AuthCheck;
import com.yu.aiagent.common.BaseResponse;
import com.yu.aiagent.common.DeleteRequest;
import com.yu.aiagent.common.ResultUtils;
import com.yu.aiagent.constant.UserConstant;
import com.yu.aiagent.exception.ErrorCode;
import com.yu.aiagent.exception.ThrowUtils;
import com.yu.aiagent.model.dto.user.*;
import com.yu.aiagent.model.entity.User;
import com.yu.aiagent.model.vo.LoginUserVO;
import com.yu.aiagent.model.vo.UserPreferenceVO;
import com.yu.aiagent.model.vo.UserVO;
import com.yu.aiagent.service.UserPreferenceService;
import com.yu.aiagent.service.UserService;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/**
 * 用户 控制层。
 */
@RestController
@RequestMapping("/user")
public class UserController {

    @Resource
    private UserService userService;

    @Resource
    private UserPreferenceService userPreferenceService;

    /**
     * 用户注册
     *
     * @param userRegisterRequest 用户注册请求
     * @return 注册结果
     */
    @PostMapping("/register")
    public BaseResponse<Long> userRegister(@RequestBody UserRegisterRequest userRegisterRequest) {
        ThrowUtils.throwIf(userRegisterRequest == null, ErrorCode.PARAMS_ERROR);
        String userAccount = userRegisterRequest.getUserAccount();
        String userPassword = userRegisterRequest.getUserPassword();
        String checkPassword = userRegisterRequest.getCheckPassword();
        long result = userService.userRegister(userAccount, userPassword, checkPassword);
        return ResultUtils.success(result);
    }

    /**
     * 用户登录
     *
     * @param userLoginRequest 用户登录请求
     * @param request          请求对象
     * @return 脱敏后的用户登录信息
     */
    @PostMapping("/login")
    public BaseResponse<LoginUserVO> userLogin(@RequestBody UserLoginRequest userLoginRequest, HttpServletRequest request) {
        ThrowUtils.throwIf(userLoginRequest == null, ErrorCode.PARAMS_ERROR);
        String userAccount = userLoginRequest.getUserAccount();
        String userPassword = userLoginRequest.getUserPassword();
        LoginUserVO loginUserVO = userService.userLogin(userAccount, userPassword, request);
        return ResultUtils.success(loginUserVO);
    }

    @GetMapping("/get/login")
    public BaseResponse<LoginUserVO> getLoginUser(HttpServletRequest request) {
        User loginUser = userService.getLoginUser(request);
        return ResultUtils.success(userService.getLoginUserVO(loginUser));
    }

    /**
     * 获取当前登录用户的通用偏好。
     */
    @GetMapping("/preference")
    public BaseResponse<UserPreferenceVO> getMyPreference(HttpServletRequest request) {
        User loginUser = userService.getLoginUser(request);
        return ResultUtils.success(userPreferenceService.getMyPreference(loginUser.getId()));
    }

    /**
     * 更新当前登录用户的通用偏好。
     */
    @PostMapping("/preference/update")
    public BaseResponse<UserPreferenceVO> updateMyPreference(@RequestBody UserPreferenceUpdateRequest preferenceUpdateRequest,
                                                            HttpServletRequest request) {
        User loginUser = userService.getLoginUser(request);
        return ResultUtils.success(userPreferenceService.updateMyPreference(loginUser.getId(), preferenceUpdateRequest));
    }

    /**
     * 当前用户更新自己的资料
     */
    @PostMapping("/profile/update")
    public BaseResponse<LoginUserVO> updateMyProfile(@RequestBody UserProfileUpdateRequest userProfileUpdateRequest,
                                                     HttpServletRequest request) {
        LoginUserVO loginUserVO = userService.updateMyProfile(userProfileUpdateRequest, request);
        return ResultUtils.success(loginUserVO);
    }

    /**
     * 当前用户上传头像
     */
    @PostMapping(value = "/avatar/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public BaseResponse<String> uploadMyAvatar(@RequestPart("file") MultipartFile file,
                                               HttpServletRequest request) {
        return ResultUtils.success(userService.uploadMyAvatar(file, request));
    }

    /**
     * 用户注销
     *
     * @param request 请求对象
     * @return
     */
    @PostMapping("/logout")
    public BaseResponse<Boolean> userLogout(HttpServletRequest request) {
        ThrowUtils.throwIf(request == null, ErrorCode.PARAMS_ERROR);
        boolean result = userService.userLogout(request);
        return ResultUtils.success(result);
    }

    /**
     * 创建用户
     */
    @PostMapping("/add")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Long> addUser(@RequestBody UserAddRequest userAddRequest) {
        return ResultUtils.success(userService.addUser(userAddRequest));
    }

    /**
     * 根据 id 获取用户（仅管理员）
     */
    @GetMapping("/get")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<User> getUserById(long id) {
        return ResultUtils.success(userService.getUserById(id));
    }

    /**
     * 根据 id 获取包装类
     */
    @GetMapping("/get/vo")
    public BaseResponse<UserVO> getUserVOById(long id) {
        return ResultUtils.success(userService.getUserVOById(id));
    }

    /**
     * 删除用户
     */
    @PostMapping("/delete")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Boolean> deleteUser(@RequestBody DeleteRequest deleteRequest) {
        return ResultUtils.success(userService.deleteUser(deleteRequest));
    }

    /**
     * 更新用户
     */
    @PostMapping("/update")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Boolean> updateUser(@RequestBody UserUpdateRequest userUpdateRequest,
                                            HttpServletRequest request) {
        return ResultUtils.success(userService.updateUser(userUpdateRequest, request));
    }

    /**
     * 分页获取用户封装列表（仅管理员）
     *
     * @param userQueryRequest 查询请求参数
     */
    @PostMapping("/list/page/vo")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Page<UserVO>> listUserVOByPage(@RequestBody UserQueryRequest userQueryRequest) {
        return ResultUtils.success(userService.listUserVOByPage(userQueryRequest));
    }
}
