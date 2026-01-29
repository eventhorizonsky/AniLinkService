package xyz.ezsky.anilink.service;

import cn.hutool.crypto.SecureUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import xyz.ezsky.anilink.model.entity.User;
import xyz.ezsky.anilink.model.entity.Role;
import xyz.ezsky.anilink.model.entity.UserRole;
import xyz.ezsky.anilink.repository.UserRepository;
import xyz.ezsky.anilink.repository.RoleRepository;
import xyz.ezsky.anilink.repository.UserRoleRepository;
import xyz.ezsky.anilink.model.vo.UserInfoVO;

import java.util.Optional;
import java.util.List;

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
    
    /**
     * 根据用户名查询用户
     */
    public Optional<User> findByUsername(String username) {
        return userRepository.findByUsername(username);
    }
    
    /**
     * 验证用户登录
     * @param username 用户名
     * @param password 明文密码
     * @return 用户对象（如果验证成功），否则为空
     */
    public Optional<User> validateLogin(String username, String password) {
        Optional<User> userOpt = userRepository.findByUsername(username);
        
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
        // 密码使用MD5加密存储
        user.setPasswordHash(encodePassword(password));
        
        return userRepository.save(user);
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

        return userInfoVO;
    }
}

