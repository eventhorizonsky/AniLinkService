package xyz.ezsky.anilink.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import xyz.ezsky.anilink.model.entity.User;

import java.util.Optional;

/**
 * 用户数据访问层
 */
@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    
    /**
     * 根据用户名查询用户
     */
    Optional<User> findByUsername(String username);
    
    /**
     * 检查用户名是否存在
     */
    boolean existsByUsername(String username);
}
