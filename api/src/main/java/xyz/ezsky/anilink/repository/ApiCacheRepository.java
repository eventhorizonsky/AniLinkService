package xyz.ezsky.anilink.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import xyz.ezsky.anilink.model.entity.ApiCache;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface ApiCacheRepository extends JpaRepository<ApiCache, Long> {

    Optional<ApiCache> findByCacheKey(String cacheKey);

    Optional<ApiCache> findByCacheKeyAndExpireTimeAfter(String cacheKey, LocalDateTime time);

    // ===== 缓存统计 =====

    /** 总缓存条数 */
    long count();

    /** 有效缓存条数（未过期） */
    long countByExpireTimeAfter(LocalDateTime now);

    /** 过期缓存条数 */
    long countByExpireTimeBefore(LocalDateTime now);

    /** 按 cacheKey 前缀统计条数 */
    long countByCacheKeyStartingWith(String prefix);

    // ===== 缓存清理 =====

    /** 删除所有过期缓存 */
    @Modifying
    @Transactional
    @Query("DELETE FROM ApiCache a WHERE a.expireTime < :now")
    int deleteAllByExpireTimeBefore(@Param("now") LocalDateTime now);

    /** 按 cacheKey 前缀删除 */
    @Modifying
    @Transactional
    @Query("DELETE FROM ApiCache a WHERE a.cacheKey LIKE CONCAT(:prefix, '%')")
    int deleteAllByCacheKeyStartingWith(@Param("prefix") String prefix);

    /** 清空全部缓存 */
    @Modifying
    @Transactional
    @Query("DELETE FROM ApiCache")
    int deleteAllCaches();

    /** 查询最早创建的缓存（用于获取时间范围） */
    Optional<ApiCache> findFirstByOrderByCreatedAtAsc();

    /** 查询最晚创建的缓存（用于获取时间范围） */
    Optional<ApiCache> findFirstByOrderByCreatedAtDesc();
}
