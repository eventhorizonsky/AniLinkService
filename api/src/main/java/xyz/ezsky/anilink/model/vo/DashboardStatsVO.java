package xyz.ezsky.anilink.model.vo;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 看板统计数据视图对象
 */
@Data
@NoArgsConstructor
public class DashboardStatsVO {

    /**
     * 收录番剧数量
     */
    private Long animeCount;

    /**
     * 媒体文件数量
     */
    private Long mediaFileCount;

    /**
     * 已匹配媒体文件数量
     */
    private Long matchedCount;

    /**
     * 未匹配媒体文件数量（未匹配 + 未找到匹配）
     */
    private Long unmatchedCount;

    /**
     * 字幕数量
     */
    private Long subtitleCount;

    /**
     * 弹幕发送数量
     */
    private Long danmakuCount;

    /**
     * RSS 订阅数量
     */
    private Long rssSubscriptionCount;

    /**
     * 启用的 RSS 订阅数量
     */
    private Long rssEnabledCount;

    /**
     * 媒体库数量
     */
    private Long libraryCount;

    /**
     * 用户数量
     */
    private Long userCount;

    /**
     * 待匹配队列中文件数量
     */
    private Long pendingMatchQueueCount;

    /**
     * 媒体文件总大小（字节）
     */
    private Long mediaTotalSizeBytes;
}
