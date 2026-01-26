package xyz.ezsky.anilink.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import xyz.ezsky.anilink.model.entity.UserRole;

import java.util.List;

/**
 * 用户角色关联数据访问层
 */
@Repository
public interface UserRoleRepository extends JpaRepository<UserRole, Long> {
    
    /**
     * 根据用户ID查询用户角色关联
     */
    List<UserRole> findByUserId(Long userId);
    
    /**
     * 根据用户ID删除用户角色关联
     */
    void deleteByUserId(Long userId);
    
    /**
     * 检查用户是否拥有某个角色
     */
    boolean existsByUserIdAndRoleId(Long userId, Long roleId);
    
    /**
     * 直接联表查询用户拥有的所有角色代码
     */
    @Query("SELECT r.roleCode FROM Role r INNER JOIN UserRole ur ON r.id = ur.roleId WHERE ur.userId = :userId AND r.isActive = true")
    List<String> findRoleCodesByUserId(@Param("userId") Long userId);
}

