package xyz.ezsky.anilink.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import xyz.ezsky.anilink.model.entity.SiteConfig;

import java.util.Optional;

/**
 * 站点配置数据访问层
 */
@Repository
public interface SiteConfigRepository extends JpaRepository<SiteConfig, Long> {
    
    /**
     * 根据配置键查询配置
     */
    Optional<SiteConfig> findByConfigKey(String configKey);
    
    /**
     * 检查配置键是否存在
     */
    boolean existsByConfigKey(String configKey);
}
