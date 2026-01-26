package xyz.ezsky.anilink.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import xyz.ezsky.anilink.model.entity.Role;

/**
 * 角色数据访问层
 */
@Repository
public interface RoleRepository extends JpaRepository<Role, Long> {
    
    /**
     * 根据角色代码查询角色
     */
    Role findByRoleCode(String roleCode);
}
