package xyz.ezsky.anilink.service;

import cn.hutool.crypto.SecureUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import xyz.ezsky.anilink.model.entity.User;
import xyz.ezsky.anilink.model.entity.Role;
import xyz.ezsky.anilink.model.entity.UserRole;
import xyz.ezsky.anilink.model.dto.UpdateUserManageRequest;
import xyz.ezsky.anilink.model.vo.PageVO;
import xyz.ezsky.anilink.model.vo.RoleOptionVO;
import xyz.ezsky.anilink.model.vo.UserManageVO;
import xyz.ezsky.anilink.repository.UserRepository;
import xyz.ezsky.anilink.repository.RoleRepository;
import xyz.ezsky.anilink.repository.UserRoleRepository;
import xyz.ezsky.anilink.model.vo.UserInfoVO;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 用户服务类
 */
@Service
public class UserService {
    
    @Autowired
    private UserRepository userRepository;
    
    @Autowired
    private RoleRepository roleRepository;
    
    @Autowired
    private UserRoleRepository userRoleRepository;

    @Autowired
    private RoleInterfaceImpl roleInterfaceImpl;
    
    /**
     * 根据用户名查询用户
     */
    public Optional<User> findByUsername(String username) {
        return userRepository.findByUsername(username);
    }

    /**
     * 根据邮箱查询用户
     */
    public Optional<User> findByEmail(String email) {
        return userRepository.findByEmail(email);
    }

    public Optional<User> findById(Long userId) {
        return userRepository.findById(userId);
    }
    
    /**
     * 验证用户登录
     * @param username 用户名
     * @param password 明文密码
     * @return 用户对象（如果验证成功），否则为空
     */
    public Optional<User> validateLogin(String account, String password) {
        Optional<User> userOpt = userRepository.findByUsernameOrEmail(account, account);
        
        if (userOpt.isEmpty()) {
            return Optional.empty();
        }
        
        User user = userOpt.get();
        
        // 检查用户是否激活
        if (!user.getIsActive()) {
            return Optional.empty();
        }

        // 使用MD5验证密码
        if (encodePassword(password).equals(user.getPasswordHash())) {
            return Optional.of(user);
        }
        
        return Optional.empty();
    }
    
    /**
     * 创建用户
     */
    public User createUser(String username, String password, String email) {
        User user = new User();
        user.setUsername(username);
        user.setEmail(email);
        user.setIsActive(true);
        user.setRemoteAccessToken(generateUniqueRemoteAccessToken());
        // 密码使用MD5加密存储
        user.setPasswordHash(encodePassword(password));
        
        return userRepository.save(user);
    }

    /**
     * 注册用户并分配默认 user 角色
     */
    @Transactional
    public User registerUser(String username, String password, String email) {
        if (userRepository.existsByUsername(username)) {
            throw new RuntimeException("用户名已存在");
        }
        if (userRepository.existsByEmail(email)) {
            throw new RuntimeException("邮箱已被注册");
        }

        User user = createUser(username, password, email);

        Role userRole = roleRepository.findByRoleCode("user");
        if (userRole == null) {
            throw new RuntimeException("系统角色 user 不存在，请检查初始化数据");
        }

        UserRole relation = new UserRole();
        relation.setUserId(user.getId());
        relation.setRoleId(userRole.getId());
        userRoleRepository.save(relation);

        return user;
    }
    
    /**
     * 创建管理员账号（带超级管理员角色）
     */
    public User createAdminUser(String username, String password, String email) {
        // 创建用户
        User admin = createUser(username, password, email);
        
        // 查找超级管理员角色
        Role superAdminRole = roleRepository.findByRoleCode("super-admin");
        
        if (superAdminRole != null) {
            // 为用户分配超级管理员角色
            UserRole userRole = new UserRole();
            userRole.setUserId(admin.getId());
            userRole.setRoleId(superAdminRole.getId());
            userRoleRepository.save(userRole);
        }
        
        return admin;
    }
    
    /**
     * 更新管理员账号密码
     */
    public void updateAdminPassword(String username, String newPassword) {
        Optional<User> userOpt = userRepository.findByUsername(username);
        
        if (userOpt.isPresent()) {
            User user = userOpt.get();
            user.setPasswordHash(encodePassword(newPassword));
            userRepository.save(user);
        }
    }
    
    /**
     * 密码编码
     */
    public String encodePassword(String password) {
        return SecureUtil.md5(password);
    }

    @Transactional
    public String getOrCreateRemoteAccessToken(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("用户不存在"));
        if (user.getRemoteAccessToken() == null || user.getRemoteAccessToken().isBlank()) {
            user.setRemoteAccessToken(generateUniqueRemoteAccessToken());
            userRepository.save(user);
        }
        return user.getRemoteAccessToken();
    }

    @Transactional
    public String regenerateRemoteAccessToken(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("用户不存在"));
        user.setRemoteAccessToken(generateUniqueRemoteAccessToken());
        userRepository.save(user);
        return user.getRemoteAccessToken();
    }

    @Transactional
    public void bindBangumiAccount(Long userId, String accessToken, Long bangumiUserId, String bangumiUsername, String bangumiNickname) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("用户不存在"));
        user.setBangumiAccessToken(accessToken);
        user.setBangumiUserId(bangumiUserId);
        user.setBangumiUsername(bangumiUsername);
        user.setBangumiNickname(bangumiNickname);
        userRepository.save(user);
    }

    @Transactional
    public void clearBangumiBinding(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("用户不存在"));
        user.setBangumiAccessToken(null);
        user.setBangumiUserId(null);
        user.setBangumiUsername(null);
        user.setBangumiNickname(null);
        userRepository.save(user);
    }

    /**
     * 根据用户ID获取用户信息（包含角色代码列表）
     * @param userId 用户ID
     * @return 用户信息
     */
    public UserInfoVO getUserInfoById(Long userId) {
        User user = userRepository.findById(userId).orElse(null);
        if (user == null) {
            return null;
        }

        List<String> roleCodes = userRoleRepository.findRoleCodesByUserId(userId);

        UserInfoVO userInfoVO = new UserInfoVO();
        userInfoVO.setId(user.getId());
        userInfoVO.setUsername(user.getUsername());
        userInfoVO.setEmail(user.getEmail());
        userInfoVO.setRoleCodeList(roleCodes);
        userInfoVO.setIsActive(user.getIsActive());
        userInfoVO.setBangumiBound(user.getBangumiAccessToken() != null && !user.getBangumiAccessToken().isBlank());
        userInfoVO.setBangumiUsername(user.getBangumiUsername());
        userInfoVO.setBangumiNickname(user.getBangumiNickname());

        return userInfoVO;
    }

    /**
     * 分页查询用户列表（管理端）
     */
    public PageVO<UserManageVO> getUsersPage(int page, int pageSize, String keyword) {
        Pageable pageable = PageRequest.of(page, pageSize);
        Page<User> userPage;

        if (keyword == null || keyword.isBlank()) {
            userPage = userRepository.findAll(pageable);
        } else {
            String normalized = keyword.trim();
            userPage = userRepository.findByUsernameContainingIgnoreCaseOrEmailContainingIgnoreCase(
                    normalized,
                    normalized,
                    pageable
            );
        }

        List<UserManageVO> content = userPage.getContent().stream()
                .map(this::toUserManageVO)
                .collect(Collectors.toList());

        return PageVO.<UserManageVO>builder()
                .content(content)
                .totalElements(userPage.getTotalElements())
                .totalPages(userPage.getTotalPages())
                .currentPage(userPage.getNumber())
                .pageSize(userPage.getSize())
                .hasNext(userPage.hasNext())
                .hasPrevious(userPage.hasPrevious())
                .build();
    }

    /**
     * 获取可分配角色列表（仅启用角色）
     */
    public List<RoleOptionVO> getActiveRoleOptions() {
        return roleRepository.findByIsActiveTrue().stream()
                .map(role -> new RoleOptionVO(
                        role.getId(),
                        role.getRoleCode(),
                        role.getRoleName(),
                        role.getDescription()
                ))
                .collect(Collectors.toList());
    }

    /**
     * 管理端更新用户资料、角色、启用状态
     */
    @Transactional
    public void updateUserByAdmin(Long userId, UpdateUserManageRequest request, Long operatorUserId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("用户不存在"));

        String username = request.getUsername() == null ? "" : request.getUsername().trim();
        if (username.isBlank()) {
            throw new RuntimeException("用户名不能为空");
        }

        Optional<User> byUsername = userRepository.findByUsername(username);
        if (byUsername.isPresent() && !byUsername.get().getId().equals(userId)) {
            throw new RuntimeException("用户名已存在");
        }

        String email = request.getEmail();
        if (email != null) {
            email = email.trim();
            if (email.isBlank()) {
                email = null;
            }
        }
        if (email != null) {
            Optional<User> byEmail = userRepository.findByEmail(email);
            if (byEmail.isPresent() && !byEmail.get().getId().equals(userId)) {
                throw new RuntimeException("邮箱已被占用");
            }
        }

        Boolean isActive = request.getIsActive();
        if (isActive == null) {
            throw new RuntimeException("启用状态不能为空");
        }
        if (Boolean.FALSE.equals(isActive) && userId.equals(operatorUserId)) {
            throw new RuntimeException("不能禁用当前登录账号");
        }

        List<String> requestedRoleCodes = request.getRoleCodeList();
        if (requestedRoleCodes == null || requestedRoleCodes.isEmpty()) {
            throw new RuntimeException("至少需要分配一个角色");
        }

        Set<String> normalizedRoleCodes = requestedRoleCodes.stream()
                .filter(code -> code != null && !code.isBlank())
                .map(String::trim)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        if (normalizedRoleCodes.isEmpty()) {
            throw new RuntimeException("至少需要分配一个角色");
        }

        List<Role> roles = roleRepository.findByRoleCodeInAndIsActiveTrue(new ArrayList<>(normalizedRoleCodes));
        if (roles.size() != normalizedRoleCodes.size()) {
            throw new RuntimeException("存在无效或已禁用角色");
        }

        user.setUsername(username);
        user.setEmail(email);
        user.setIsActive(isActive);
        userRepository.save(user);

        Set<Long> targetRoleIds = roles.stream()
                .map(Role::getId)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        List<UserRole> existingRelations = userRoleRepository.findByUserId(userId);
        Set<Long> existingRoleIds = existingRelations.stream()
                .map(UserRole::getRoleId)
                .collect(Collectors.toCollection(HashSet::new));

        List<UserRole> toDelete = existingRelations.stream()
                .filter(rel -> !targetRoleIds.contains(rel.getRoleId()))
                .collect(Collectors.toList());
        if (!toDelete.isEmpty()) {
            userRoleRepository.deleteAll(toDelete);
        }

        for (Long roleId : targetRoleIds) {
            if (!existingRoleIds.contains(roleId)) {
                UserRole userRole = new UserRole();
                userRole.setUserId(userId);
                userRole.setRoleId(roleId);
                userRoleRepository.save(userRole);
            }
        }

        roleInterfaceImpl.refreshUserRoleCache(userId);
    }

    private UserManageVO toUserManageVO(User user) {
        List<String> roleCodes = userRoleRepository.findRoleCodesByUserId(user.getId());
        return new UserManageVO(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getIsActive(),
                roleCodes,
                user.getCreatedAt(),
                user.getUpdatedAt()
        );
    }

    private String generateUniqueRemoteAccessToken() {
        for (int i = 0; i < 10; i++) {
            String candidate = UUID.randomUUID().toString().replace("-", "")
                    + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
            if (userRepository.findByRemoteAccessToken(candidate).isEmpty()) {
                return candidate;
            }
        }
        throw new RuntimeException("生成远程访问密钥失败，请重试");
    }

    @Transactional
    public String getOrCreateMcpApiKey(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("用户不存在"));
        if (user.getMcpApiKey() == null || user.getMcpApiKey().isBlank()) {
            user.setMcpApiKey(generateUniqueMcpApiKey());
            userRepository.save(user);
        }
        return user.getMcpApiKey();
    }

    @Transactional
    public String regenerateMcpApiKey(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("用户不存在"));
        user.setMcpApiKey(generateUniqueMcpApiKey());
        userRepository.save(user);
        return user.getMcpApiKey();
    }

    private String generateUniqueMcpApiKey() {
        for (int i = 0; i < 10; i++) {
            String candidate = UUID.randomUUID().toString().replace("-", "")
                    + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
            if (userRepository.findByMcpApiKey(candidate).isEmpty()) {
                return candidate;
            }
        }
        throw new RuntimeException("生成 MCP API Key 失败，请重试");
    }
}

