package xyz.ezsky.anilink.service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import cn.dev33.satoken.stp.StpInterface;
import xyz.ezsky.anilink.repository.UserRoleRepository;

/**
 * 自定义权限加载接口实现类
 */
@Component
public class RoleInterfaceImpl implements StpInterface {
    
    @Autowired
    private UserRoleRepository userRoleRepository;
    
    /**
     * 内存缓存：userId -> roleCodeList
     */
    private final ConcurrentHashMap<Long, List<String>> roleCache = new ConcurrentHashMap<>();

    /**
     * 返回一个账号所拥有的权限码集合 
     */
    @Override
    public List<String> getPermissionList(Object loginId, String loginType) {
        // 暂时返回空列表，可根据业务需求实现权限查询
        return new ArrayList<>();
    }

    /**
     * 返回一个账号所拥有的角色标识集合 (权限与角色可分开校验)
     */
    @Override
    public List<String> getRoleList(Object loginId, String loginType) {
        Long userId = Long.parseLong(loginId.toString());
        
        // 先查缓存
        if (roleCache.containsKey(userId)) {
            return roleCache.get(userId);
        }
        
        // 缓存未命中，直接联表查询数据库
        List<String> roleCodes = userRoleRepository.findRoleCodesByUserId(userId);
        
        // 缓存结果
        roleCache.put(userId, roleCodes);
        
        return roleCodes;
    }
    
    /**
     * 刷新指定用户的角色缓存
     */
    public void refreshUserRoleCache(Long userId) {
        roleCache.remove(userId);
    }
    
    /**
     * 刷新所有角色缓存
     */
    public void refreshAllRoleCache() {
        roleCache.clear();
    }

}

