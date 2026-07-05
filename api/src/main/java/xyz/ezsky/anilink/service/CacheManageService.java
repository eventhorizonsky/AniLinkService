package xyz.ezsky.anilink.service;

import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import xyz.ezsky.anilink.model.vo.CacheClearResultVO;
import xyz.ezsky.anilink.model.vo.CacheStatsVO;
import xyz.ezsky.anilink.repository.ApiCacheRepository;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 缓存管理服务
 *
 * <p>提供缓存统计、分级清理和定时自动清理过期缓存功能</p>
 */
@Service
@Log4j2
public class CacheManageService {

    /** 缓存 Key 前缀常量 */
    private static final String PREFIX_COMMENT = "dandan:comment";
    private static final String PREFIX_BANGUMI = "dandan:bangumi";

    /** 缓存类型定义：按前缀匹配 */
    private static final Map<String, String> CACHE_TYPES = new LinkedHashMap<>();
    static {
        CACHE_TYPES.put("comment", PREFIX_COMMENT);
        CACHE_TYPES.put("bangumi", PREFIX_BANGUMI);
    }

    @Autowired
    private ApiCacheRepository apiCacheRepository;

    // ==================== 缓存统计 ====================

    /**
     * 获取缓存统计信息
     */
    public CacheStatsVO getCacheStats() {
        LocalDateTime now = LocalDateTime.now();
        CacheStatsVO vo = new CacheStatsVO();

        vo.setTotalCount(apiCacheRepository.count());
        vo.setValidCount(apiCacheRepository.countByExpireTimeAfter(now));
        vo.setExpiredCount(apiCacheRepository.countByExpireTimeBefore(now));

        // 按类型统计
        Map<String, Long> countByType = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : CACHE_TYPES.entrySet()) {
            countByType.put(entry.getKey(), apiCacheRepository.countByCacheKeyStartingWith(entry.getValue()));
        }
        vo.setCountByType(countByType);

        // 最早和最晚创建时间
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        apiCacheRepository.findFirstByOrderByCreatedAtAsc().ifPresent(c ->
            vo.setEarliestCreatedAt(c.getCreatedAt() != null ? c.getCreatedAt().format(fmt) : null));
        apiCacheRepository.findFirstByOrderByCreatedAtDesc().ifPresent(c ->
            vo.setLatestCreatedAt(c.getCreatedAt() != null ? c.getCreatedAt().format(fmt) : null));

        return vo;
    }

    // ==================== 分级清理 ====================

    /**
     * 清理所有过期缓存（Level 1 — 最安全）
     *
     * @return 清理结果
     */
    public CacheClearResultVO clearExpired() {
        long before = apiCacheRepository.count();
        int deleted = apiCacheRepository.deleteAllByExpireTimeBefore(LocalDateTime.now());
        long after = apiCacheRepository.count();
        log.info("缓存清理(Level1-过期): 删除 {} 条, 清理前 {} 条, 剩余 {} 条", deleted, before, after);
        return CacheClearResultVO.of("expired", null, deleted, before, after);
    }

    /**
     * 按类型清理缓存（Level 2 — 精准清理）
     *
     * @param cacheType 缓存类型：comment / bangumi
     * @return 清理结果
     */
    public CacheClearResultVO clearByType(String cacheType) {
        String prefix = CACHE_TYPES.get(cacheType);
        if (prefix == null) {
            throw new IllegalArgumentException("未知的缓存类型: " + cacheType + "，可用类型: " + CACHE_TYPES.keySet());
        }

        long before = apiCacheRepository.count();
        int deleted = apiCacheRepository.deleteAllByCacheKeyStartingWith(prefix);
        long after = apiCacheRepository.count();
        log.info("缓存清理(Level2-按类型[{}]): 删除 {} 条, 清理前 {} 条, 剩余 {} 条", cacheType, deleted, before, after);
        return CacheClearResultVO.of("type", cacheType, deleted, before, after);
    }

    /**
     * 清空全部缓存（Level 3 — 核选项）
     *
     * @return 清理结果
     */
    public CacheClearResultVO clearAll() {
        long before = apiCacheRepository.count();
        int deleted = apiCacheRepository.deleteAllCaches();
        long after = apiCacheRepository.count();
        log.warn("缓存清理(Level3-全部): 删除 {} 条, 清理前 {} 条, 剩余 {} 条", deleted, before, after);
        return CacheClearResultVO.of("all", null, deleted, before, after);
    }

    /**
     * 获取可用的缓存类型列表（供前端展示）
     */
    public Map<String, String> getAvailableCacheTypes() {
        return new LinkedHashMap<>(CACHE_TYPES);
    }

    // ==================== 定时自动清理 ====================

    /**
     * 每小时自动清理一次过期缓存
     * 在整点过 7 分触发，避免与其他整点任务碰撞
     */
    @Scheduled(cron = "0 7 * * * *")
    public void scheduledCleanExpired() {
        try {
            CacheClearResultVO result = clearExpired();
            if (result.getDeletedCount() > 0) {
                log.info("定时清理过期缓存完成: 删除 {} 条, 剩余 {} 条",
                        result.getDeletedCount(), result.getAfterCount());
            }
        } catch (Exception e) {
            log.error("定时清理过期缓存失败", e);
        }
    }
}
