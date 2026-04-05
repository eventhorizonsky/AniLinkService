package xyz.ezsky.anilink.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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
     * 根据邮箱查询用户
     */
    Optional<User> findByEmail(String email);

    /**
     * 根据用户名或邮箱查询用户
     */
    Optional<User> findByUsernameOrEmail(String username, String email);

    /**
     * 根据远程访问令牌查询用户
     */
    Optional<User> findByRemoteAccessToken(String remoteAccessToken);

    Optional<User> findByMcpApiKey(String mcpApiKey);
    
    /**
     * 检查用户名是否存在
     */
    boolean existsByUsername(String username);

    /**
     * 检查邮箱是否存在
     */
    boolean existsByEmail(String email);

    /**
     * 按用户名/邮箱模糊分页查询
     */
    Page<User> findByUsernameContainingIgnoreCaseOrEmailContainingIgnoreCase(
            String usernameKeyword,
            String emailKeyword,
            Pageable pageable
    );
}
