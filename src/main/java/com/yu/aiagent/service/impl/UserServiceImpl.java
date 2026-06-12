package com.yu.aiagent.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.yu.aiagent.common.DeleteRequest;
import com.yu.aiagent.constant.FileConstant;
import com.yu.aiagent.constant.UserConstant;
import com.yu.aiagent.exception.BusinessException;
import com.yu.aiagent.exception.ErrorCode;
import com.yu.aiagent.exception.ThrowUtils;
import com.yu.aiagent.model.dto.user.UserAddRequest;
import com.yu.aiagent.model.dto.user.UserProfileUpdateRequest;
import com.yu.aiagent.model.dto.user.UserQueryRequest;
import com.yu.aiagent.model.dto.user.UserUpdateRequest;
import com.yu.aiagent.model.cache.LoginFailureRecord;
import com.yu.aiagent.model.entity.User;
import com.yu.aiagent.model.enums.UserRoleEnum;
import com.yu.aiagent.model.vo.LoginUserVO;
import com.yu.aiagent.model.vo.UserVO;
import com.yu.aiagent.service.UserService;
import com.yu.aiagent.mapper.UserMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Locale;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import static com.yu.aiagent.constant.UserConstant.USER_LOGIN_STATE;

/**
 * 用户服务实现。
 *
 * <p>这里集中处理用户注册、登录、登录态校验、资料更新、头像上传、
 * 后台用户查询以及密码加密等和用户账号相关的核心业务逻辑。</p>
 *
 * @author 86199
 * @description 针对表【user(用户)】的数据库操作Service实现
 * @createDate 2026-05-06 14:28:35
 */
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User>
    implements UserService{

    /**
     * BCrypt 密码编码器。
     *
     * <p>BCrypt 自带随机盐和工作因子，比普通 MD5 + salt 更适合保存用户密码。</p>
     */
    private static final BCryptPasswordEncoder PASSWORD_ENCODER = new BCryptPasswordEncoder();

    /**
     * 正常账号状态。
     */
    private static final int NORMAL_USER_STATUS = 0;

    /**
     * 禁用账号状态。禁用后用户不能登录，已有登录态也会在校验时失效。
     */
    private static final int DISABLED_USER_STATUS = 1;

    /**
     * 登录失败最大次数，达到该次数后进入临时锁定。
     */
    private static final int MAX_LOGIN_FAILURE_COUNT = 5;

    /**
     * 登录失败锁定时长：10 分钟。
     */
    private static final long LOGIN_LOCK_MILLIS = 10 * 60 * 1000L;

    /**
     * 登录失败计数。
     *
     * <p>当前先用内存实现登录失败限制，部署成多实例时可以平滑替换为 Redis。</p>
     */
    private static final ConcurrentHashMap<String, LoginFailureRecord> LOGIN_FAILURE_MAP = new ConcurrentHashMap<>();

    /**
     * 头像文件大小上限：2MB。
     */
    private static final long MAX_AVATAR_SIZE = 2 * 1024 * 1024L;

    /**
     * 允许上传的头像 MIME 类型。
     *
     * <p>只靠文件后缀不可靠，因此这里使用浏览器上传时提供的 contentType 做第一层校验。</p>
     */
    private static final Set<String> ALLOW_AVATAR_CONTENT_TYPES = Set.of(
            "image/jpeg",
            "image/png",
            "image/gif",
            "image/webp"
    );

    /**
     * 用户注册。
     *
     * <p>注册流程：参数校验 -> 账号唯一性校验 -> BCrypt 加密密码 -> 保存默认用户信息。</p>
     */
    @Override
    public long userRegister(String userAccount, String userPassword, String checkPassword) {
        // 1. 校验参数
        if (StrUtil.hasBlank(userAccount, userPassword, checkPassword)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "参数为空");
        }
        if (userAccount.length() < 4) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "账号长度过短");
        }
        if (userPassword.length() < 6 || checkPassword.length() < 6) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "密码长度过短");
        }
        if (!userPassword.equals(checkPassword)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "两次输入的密码不一致");
        }
        // 2. 查询用户是否已存在
        QueryWrapper<User> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("userAccount", userAccount);
        long count = this.count(queryWrapper);
        if (count > 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "账号重复");
        }
        // 3. 加密密码
        String encryptPassword = getEncryptPassword(userPassword);
        // 4. 创建用户，插入数据库。新注册用户默认普通角色、正常状态。
        User user = new User();
        user.setUserAccount(userAccount);
        user.setUserPassword(encryptPassword);
        user.setUserName("无名");
        user.setUserRole(UserRoleEnum.USER.getValue());
        user.setUserStatus(NORMAL_USER_STATUS);
        boolean saveResult = this.save(user);
        if (!saveResult) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "注册失败，数据库错误");
        }
        return user.getId();
    }

    /**
     * 转换为登录用户 VO。
     *
     * <p>VO 不包含密码等敏感字段，适合返回给前端保存登录态。</p>
     */
    @Override
    public LoginUserVO getLoginUserVO(User user) {
        if (user == null) {
            return null;
        }
        LoginUserVO loginUserVO = new LoginUserVO();
        BeanUtil.copyProperties(user, loginUserVO);
        return loginUserVO;
    }

    /**
     * 用户登录。
     *
     * <p>登录流程：参数校验 -> 检查是否被失败次数锁定 -> 校验账号密码 ->
     * 检查账号是否禁用 -> 清理失败计数 -> 写入 Session 登录态。</p>
     */
    @Override
    public LoginUserVO userLogin(String userAccount, String userPassword, HttpServletRequest request) {
        // 1. 校验参数
        if (StrUtil.hasBlank(userAccount, userPassword)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "参数为空");
        }
        if (userAccount.length() < 4) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "账号长度过短");
        }
        if (userPassword.length() < 6) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "密码长度过短");
        }
        String loginFailureKey = getLoginFailureKey(userAccount, request);
        // 如果同一账号 + IP 已经达到失败次数上限，这里会直接拒绝，避免继续查询密码。
        checkLoginLocked(loginFailureKey);
        // 2. 查询用户是否存在
        QueryWrapper<User> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("userAccount", userAccount);
        User user = this.getOne(queryWrapper);
        if (user == null || !isPasswordMatch(userPassword, user.getUserPassword())) {
            // 登录失败会记录失败次数，并在达到上限时抛出“请求过于频繁”异常。
            recordLoginFailure(loginFailureKey);
        }
        // 管理员禁用账号后，用户不能继续登录。
        if (DISABLED_USER_STATUS == normalizeUserStatus(user.getUserStatus())) {
            throw new BusinessException(ErrorCode.FORBIDDEN_ERROR, "账号已被禁用，请联系管理员");
        }
        // 登录成功后清理失败计数，避免之前的失败影响之后的正常登录。
        LOGIN_FAILURE_MAP.remove(loginFailureKey);
        // 3. 如果用户存在，记录用户的登录态
        request.getSession().setAttribute(USER_LOGIN_STATE, user);
        // 4. 返回脱敏的用户信息
        return this.getLoginUserVO(user);
    }

    /**
     * 获取当前登录用户。
     *
     * <p>每次从 Session 中取出用户 id 后，都会重新查数据库，确保用户资料、角色、
     * 禁用状态等变化能及时生效，而不是长期依赖旧 Session 快照。</p>
     */
    @Override
    public User getLoginUser(HttpServletRequest request) {
        // 先判断用户是否登录
        Object userObj = request.getSession().getAttribute(USER_LOGIN_STATE);
        User currentUser = (User) userObj;
        if (currentUser == null || currentUser.getId() == null) {
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR);
        }
        // 从数据库查询当前用户信息
        long userId = currentUser.getId();
        currentUser = this.getById(userId);
        if (currentUser == null) {
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR);
        }
        // 如果管理员在后台禁用了该用户，需要立刻移除 Session，防止继续访问受保护接口。
        if (DISABLED_USER_STATUS == normalizeUserStatus(currentUser.getUserStatus())) {
            request.getSession().removeAttribute(USER_LOGIN_STATE);
            throw new BusinessException(ErrorCode.FORBIDDEN_ERROR, "账号已被禁用，请联系管理员");
        }
        return currentUser;
    }

    /**
     * 当前登录用户更新自己的资料。
     *
     * <p>资料和密码可以同时更新，也可以只更新其中一部分。
     * 如果任意密码字段被填写，就要求当前密码、新密码、确认密码三项全部完整。</p>
     */
    @Override
    public LoginUserVO updateMyProfile(UserProfileUpdateRequest userProfileUpdateRequest, HttpServletRequest request) {
        if (userProfileUpdateRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        User loginUser = this.getLoginUser(request);

        User updateUser = new User();
        updateUser.setId(loginUser.getId());

        // 基础资料字段允许局部更新：传 null 表示不修改该字段。
        String userName = userProfileUpdateRequest.getUserName();
        String userAvatar = userProfileUpdateRequest.getUserAvatar();
        String userProfile = userProfileUpdateRequest.getUserProfile();
        if (userName != null) {
            if (userName.length() > 80) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "昵称过长");
            }
            updateUser.setUserName(userName);
        }
        if (userAvatar != null) {
            if (userAvatar.length() > 1024) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "头像地址过长");
            }
            updateUser.setUserAvatar(userAvatar);
        }
        if (userProfile != null) {
            if (userProfile.length() > 512) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "简介过长");
            }
            updateUser.setUserProfile(userProfile);
        }

        String oldPassword = userProfileUpdateRequest.getOldPassword();
        String newPassword = userProfileUpdateRequest.getNewPassword();
        String checkPassword = userProfileUpdateRequest.getCheckPassword();
        boolean needUpdatePassword = StrUtil.isNotBlank(oldPassword)
                || StrUtil.isNotBlank(newPassword)
                || StrUtil.isNotBlank(checkPassword);
        if (needUpdatePassword) {
            // 只要用户开始改密码，就必须把三项密码信息填完整，避免半更新状态。
            if (StrUtil.hasBlank(oldPassword, newPassword, checkPassword)) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "请完整填写密码信息");
            }
            if (newPassword.length() < 6) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "新密码长度不能小于 6 位");
            }
            if (!newPassword.equals(checkPassword)) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "两次输入的新密码不一致");
            }
            if (!isPasswordMatch(oldPassword, loginUser.getUserPassword())) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "当前密码错误");
            }
            // 新密码仍然使用统一的 BCrypt 加密入口。
            updateUser.setUserPassword(this.getEncryptPassword(newPassword));
        }

        boolean result = this.updateById(updateUser);
        if (!result) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR);
        }
        // 更新成功后重新查询用户并刷新 Session，保证顶部用户信息立即展示最新资料。
        User updatedUser = this.getById(loginUser.getId());
        request.getSession().setAttribute(USER_LOGIN_STATE, updatedUser);
        return this.getLoginUserVO(updatedUser);
    }

    /**
     * 当前登录用户上传头像。
     *
     * <p>上传过程会校验文件大小、MIME 类型、文件后缀，并通过 normalize + startsWith
     * 防止路径穿越。返回值是前端可直接展示的头像 URL。</p>
     */
    @Override
    public String uploadMyAvatar(MultipartFile file, HttpServletRequest request) {
        User loginUser = this.getLoginUser(request);
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "请选择头像文件");
        }
        if (file.getSize() > MAX_AVATAR_SIZE) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "头像不能超过 2MB");
        }
        String contentType = file.getContentType();
        if (StrUtil.isBlank(contentType)
                || !ALLOW_AVATAR_CONTENT_TYPES.contains(contentType.toLowerCase(Locale.ROOT))) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "仅支持 JPG、PNG、GIF、WEBP 图片");
        }

        String suffix = getFileSuffix(file.getOriginalFilename(), contentType);
        // 文件名中加入用户 id 和 UUID，既方便排查归属，又避免同名文件覆盖。
        String fileName = loginUser.getId() + "_" + UUID.randomUUID().toString().replace("-", "") + suffix;
        Path avatarDir = Paths.get(FileConstant.FILE_SAVE_DIR, "avatar").toAbsolutePath().normalize();
        Path targetPath = avatarDir.resolve(fileName).normalize();
        // 防止构造出的目标路径逃逸出头像目录。
        if (!targetPath.startsWith(avatarDir)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }

        try {
            // 目录不存在时自动创建，便于本地开发和首次部署。
            Files.createDirectories(avatarDir);
            file.transferTo(targetPath.toFile());
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "头像上传失败");
        }

        // 返回完整访问 URL，前端保存后可以直接作为 img src 使用。
        String contextPath = request.getContextPath() == null ? "" : request.getContextPath();
        return request.getScheme() + "://" + request.getServerName() + ":" + request.getServerPort()
                + contextPath + "/files/avatar/" + fileName;
    }

    /**
     * 转换为后台用户展示 VO。
     */
    @Override
    public UserVO getUserVO(User user) {
        if (user == null) {
            return null;
        }
        UserVO userVO = new UserVO();
        BeanUtil.copyProperties(user, userVO);
        return userVO;
    }

    /**
     * 批量转换用户列表为 VO 列表。
     */
    @Override
    public List<UserVO> getUserVOList(List<User> userList) {
        if (CollUtil.isEmpty(userList)) {
            return new ArrayList<>();
        }
        return userList.stream()
                .map(this::getUserVO)
                .collect(Collectors.toList());
    }

    /**
     * 用户退出登录。
     *
     * <p>退出时只需要移除 Session 中的登录态，浏览器下次访问受保护接口会重新触发未登录逻辑。</p>
     */
    @Override
    public boolean userLogout(HttpServletRequest request) {
        // 先判断用户是否登录
        Object userObj = request.getSession().getAttribute(USER_LOGIN_STATE);
        if (userObj == null) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "用户未登录");
        }
        // 移除登录态
        request.getSession().removeAttribute(USER_LOGIN_STATE);
        return true;
    }

    /**
     * 构造后台用户分页查询条件。
     *
     * <p>支持按 id、角色、账号状态精确查询，也支持按账号、昵称、简介模糊查询。</p>
     */
    @Override
    public QueryWrapper getQueryWrapper(UserQueryRequest userQueryRequest) {
        if (userQueryRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "请求参数为空");
        }
        Long id = userQueryRequest.getId();
        String userAccount = userQueryRequest.getUserAccount();
        String userName = userQueryRequest.getUserName();
        String userProfile = userQueryRequest.getUserProfile();
        String userRole = userQueryRequest.getUserRole();
        Integer userStatus = userQueryRequest.getUserStatus();
        String sortField = userQueryRequest.getSortField();
        String sortOrder = userQueryRequest.getSortOrder();

        QueryWrapper<User> queryWrapper = new QueryWrapper<>();
        // 账号、昵称、简介使用 like，便于后台搜索；角色和状态使用 eq，保证筛选准确。
        queryWrapper.eq(id != null, "id", id)
                .eq(StrUtil.isNotBlank(userRole), "userRole", userRole)
                .eq(userStatus != null, "userStatus", userStatus)
                .like(StrUtil.isNotBlank(userAccount), "userAccount", userAccount)
                .like(StrUtil.isNotBlank(userName), "userName", userName)
                .like(StrUtil.isNotBlank(userProfile), "userProfile", userProfile)
                .orderBy(StrUtil.isNotBlank(sortField), "ascend".equals(sortOrder), sortField);

        return queryWrapper;
    }

    /**
     * 管理员创建用户。
     *
     * <p>Controller 只负责权限拦截和参数接收，默认密码、角色规范化、状态规范化和落库都放在服务层。</p>
     */
    @Override
    public Long addUser(UserAddRequest userAddRequest) {
        if (userAddRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        User user = new User();
        BeanUtil.copyProperties(userAddRequest, user);
        // 后台创建用户时使用统一默认密码，后续可扩展为首次登录强制改密。
        final String DEFAULT_PASSWORD = "123456";
        user.setUserPassword(getEncryptPassword(DEFAULT_PASSWORD));
        user.setUserRole(normalizeUserRole(user.getUserRole()));
        user.setUserStatus(normalizeAdminUserStatus(user.getUserStatus()));
        boolean result = this.save(user);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        return user.getId();
    }

    /**
     * 管理员根据 id 获取用户实体。
     */
    @Override
    public User getUserById(long id) {
        if (id <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        User user = this.getById(id);
        if (user == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR);
        }
        return user;
    }

    /**
     * 根据 id 获取脱敏用户信息。
     */
    @Override
    public UserVO getUserVOById(long id) {
        return this.getUserVO(getUserById(id));
    }

    /**
     * 管理员删除用户。
     */
    @Override
    public Boolean deleteUser(DeleteRequest deleteRequest) {
        if (deleteRequest == null || deleteRequest.getId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        return this.removeById(deleteRequest.getId());
    }

    /**
     * 管理员更新用户。
     *
     * <p>这里集中处理后台用户管理的安全边界：不能禁用当前登录账号，也不能把当前管理员账号降级。</p>
     */
    @Override
    public Boolean updateUser(UserUpdateRequest userUpdateRequest, HttpServletRequest request) {
        if (userUpdateRequest == null || userUpdateRequest.getId() == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        User loginUser = this.getLoginUser(request);
        if (loginUser.getId().equals(userUpdateRequest.getId())
                && userUpdateRequest.getUserStatus() != null
                && userUpdateRequest.getUserStatus() == DISABLED_USER_STATUS) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "不能禁用当前登录账号");
        }
        if (loginUser.getId().equals(userUpdateRequest.getId())
                && userUpdateRequest.getUserRole() != null
                && !UserRoleEnum.ADMIN.getValue().equals(userUpdateRequest.getUserRole())) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "不能修改当前登录账号的角色");
        }
        User user = new User();
        BeanUtil.copyProperties(userUpdateRequest, user);
        if (userUpdateRequest.getUserRole() != null) {
            user.setUserRole(normalizeUserRole(userUpdateRequest.getUserRole()));
        }
        if (userUpdateRequest.getUserStatus() != null) {
            user.setUserStatus(normalizeAdminUserStatus(userUpdateRequest.getUserStatus()));
        }
        boolean result = this.updateById(user);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        return true;
    }

    /**
     * 管理员分页查询脱敏用户列表。
     */
    @Override
    public Page<UserVO> listUserVOByPage(UserQueryRequest userQueryRequest) {
        if (userQueryRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        long pageNum = userQueryRequest.getPageNum();
        long pageSize = userQueryRequest.getPageSize();
        Page<User> userPage = this.page(Page.of(pageNum, pageSize), this.getQueryWrapper(userQueryRequest));
        Page<UserVO> userVOPage = new Page<>(pageNum, pageSize, userPage.getTotal());
        userVOPage.setRecords(this.getUserVOList(userPage.getRecords()));
        return userVOPage;
    }

    /**
     * 加密明文密码。
     *
     * <p>每次 BCrypt encode 都会生成新的随机盐，所以同一个明文密码多次加密结果也不同。</p>
     */
    @Override
    public String getEncryptPassword(String userPassword) {
        return PASSWORD_ENCODER.encode(userPassword);
    }

    /**
     * 校验明文密码是否匹配数据库中的 BCrypt 密文。
     */
    private boolean isPasswordMatch(String rawPassword, String encodedPassword) {
        if (StrUtil.hasBlank(rawPassword, encodedPassword)) {
            return false;
        }
        return PASSWORD_ENCODER.matches(rawPassword, encodedPassword);
    }

    /**
     * 规范化用户状态。
     *
     * <p>老数据可能没有 userStatus 字段值，这里将 null 视为正常账号。</p>
     */
    private int normalizeUserStatus(Integer userStatus) {
        return userStatus == null ? NORMAL_USER_STATUS : userStatus;
    }

    /**
     * 规范化后台用户角色。
     */
    private String normalizeUserRole(String userRole) {
        if (userRole == null) {
            return UserConstant.DEFAULT_ROLE;
        }
        UserRoleEnum userRoleEnum = UserRoleEnum.getEnumByValue(userRole);
        if (userRoleEnum == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "用户角色不合法");
        }
        return userRoleEnum.getValue();
    }

    /**
     * 规范化后台用户状态。
     */
    private Integer normalizeAdminUserStatus(Integer userStatus) {
        if (userStatus == null) {
            return NORMAL_USER_STATUS;
        }
        if (userStatus != NORMAL_USER_STATUS && userStatus != DISABLED_USER_STATUS) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "用户状态不合法");
        }
        return userStatus;
    }

    /**
     * 构造登录失败计数 key。
     *
     * <p>使用“账号 + IP”组合计数，可以避免同一 IP 输错不同账号时互相影响，也避免一个账号在不同网络下完全共享失败次数。</p>
     */
    private String getLoginFailureKey(String userAccount, HttpServletRequest request) {
        String remoteAddr = request == null ? "unknown" : request.getRemoteAddr();
        return userAccount.trim().toLowerCase(Locale.ROOT) + ":" + remoteAddr;
    }

    /**
     * 检查当前账号 + IP 是否处于登录锁定期。
     */
    private void checkLoginLocked(String loginFailureKey) {
        LoginFailureRecord record = LOGIN_FAILURE_MAP.get(loginFailureKey);
        if (record == null || record.getLockUntil() <= System.currentTimeMillis()) {
            return;
        }
        // 向上取整剩余分钟数，避免刚锁定时提示 0 分钟。
        long remainMinutes = Math.max(1, (record.getLockUntil() - System.currentTimeMillis() + 59999) / 60000);
        throw new BusinessException(ErrorCode.TOO_MANY_REQUEST, "登录失败次数过多，请 " + remainMinutes + " 分钟后再试");
    }

    /**
     * 记录一次登录失败并抛出对应业务异常。
     *
     * <p>该方法的职责是“记录并结束本次登录流程”，所以内部一定会抛异常。
     * 达到最大失败次数时设置锁定截止时间；未达到时提示剩余尝试次数。</p>
     */
    private void recordLoginFailure(String loginFailureKey) {
        LoginFailureRecord record = LOGIN_FAILURE_MAP.compute(loginFailureKey, (key, oldRecord) -> {
            LoginFailureRecord nextRecord = oldRecord == null ? new LoginFailureRecord() : oldRecord;
            // 如果锁定已经过期，则重置计数，允许用户重新尝试。
            if (nextRecord.getLockUntil() > 0 && nextRecord.getLockUntil() <= System.currentTimeMillis()) {
                nextRecord.setFailureCount(0);
                nextRecord.setLockUntil(0);
            }
            // 未处于锁定期时累计失败次数。
            if (nextRecord.getLockUntil() <= System.currentTimeMillis()) {
                nextRecord.setFailureCount(nextRecord.getFailureCount() + 1);
            }
            // 达到阈值后写入锁定截止时间。
            if (nextRecord.getFailureCount() >= MAX_LOGIN_FAILURE_COUNT) {
                nextRecord.setLockUntil(System.currentTimeMillis() + LOGIN_LOCK_MILLIS);
            }
            return nextRecord;
        });
        if (record.getFailureCount() >= MAX_LOGIN_FAILURE_COUNT) {
            throw new BusinessException(ErrorCode.TOO_MANY_REQUEST, "登录失败次数过多，请 10 分钟后再试");
        }
        int remainCount = MAX_LOGIN_FAILURE_COUNT - record.getFailureCount();
        throw new BusinessException(ErrorCode.PARAMS_ERROR, "用户不存在或密码错误，还可尝试 " + remainCount + " 次");
    }

    /**
     * 获取安全的头像文件后缀。
     *
     * <p>优先使用原文件名中的合法图片后缀；如果后缀缺失或不可信，则根据 MIME 类型兜底。</p>
     */
    private String getFileSuffix(String originalFilename, String contentType) {
        if (StrUtil.isNotBlank(originalFilename)) {
            int lastDotIndex = originalFilename.lastIndexOf(".");
            if (lastDotIndex >= 0 && lastDotIndex < originalFilename.length() - 1) {
                String suffix = originalFilename.substring(lastDotIndex).toLowerCase(Locale.ROOT);
                if (suffix.matches("\\.(jpg|jpeg|png|gif|webp)")) {
                    return suffix;
                }
            }
        }
        return switch (contentType.toLowerCase(Locale.ROOT)) {
            case "image/png" -> ".png";
            case "image/gif" -> ".gif";
            case "image/webp" -> ".webp";
            default -> ".jpg";
        };
    }

}




