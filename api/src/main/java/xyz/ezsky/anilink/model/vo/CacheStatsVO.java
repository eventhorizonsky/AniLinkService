package xyz.ezsky.anilink.model.vo;

import lombok.Data;

import java.util.Map;

/**
 * 缓存统计视图对象
 */
@Data
public class CacheStatsVO {

    /** 总缓存条数 */
    private long totalCount;

    /** 有效缓存条数（未过期） */
    private long validCount;

    /** 过期缓存条数 */
    private long expiredCount;

    /** 按类型分组统计 */
    private Map<String, Long> countByType;

    /** 最近一条缓存的创建时间 */
    private String latestCreatedAt;

    /** 最早一条缓存的创建时间 */
    private String earliestCreatedAt;
}
