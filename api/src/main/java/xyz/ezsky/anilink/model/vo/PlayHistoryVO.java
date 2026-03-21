package xyz.ezsky.anilink.model.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 播放历史VO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PlayHistoryVO {
    
    private Long id;
    
    private Long userId;
    
    private Long animeId;
    
    private String animeTitle;
    
    private Long videoId;
    
    private String videoName;

    private String episodeId;
    
    /**
     * 当前播放进度（秒）
     */
    private Long progressSeconds;
    
    /**
     * 视频总时长（秒）
     */
    private Long durationSeconds;
    
    /**
     * 播放百分比 (0-100)
     */
    private Integer progressPercentage;
    
    /**
     * 是否已完成观看
     */
    private Boolean isCompleted;
    
    private LocalDateTime createdAt;
    
    private LocalDateTime lastPlayTime;
}
