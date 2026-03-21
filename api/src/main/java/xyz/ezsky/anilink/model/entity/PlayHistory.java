package xyz.ezsky.anilink.model.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 播放历史记录实体类
 * 记录用户的播放进度和历史
 */
@Entity
@Table(name = "play_history", indexes = {
    @Index(name = "idx_history_user_anime", columnList = "user_id,anime_id"),
    @Index(name = "idx_history_user", columnList = "user_id"),
    @Index(name = "idx_history_updated", columnList = "last_play_time")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PlayHistory {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "user_id", nullable = false)
    private Long userId;
    
    @Column(name = "anime_id", nullable = false)
    private Long animeId;
    
    @Column(name = "anime_title", length = 500)
    private String animeTitle;
    
    /**
     * 视频文件ID
     */
    @Column(name = "video_id")
    private Long videoId;
    
    /**
     * 视频文件名称
     */
    @Column(name = "video_name", length = 1000)
    private String videoName;

    @Column(name = "episode_id", length = 255)
    private String episodeId;
    
    /**
     * 当前播放进度（秒）
     */
    @Column(name = "progress_seconds")
    private Long progressSeconds = 0L;
    
    /**
     * 视频总时长（秒）
     */
    @Column(name = "duration_seconds")
    private Long durationSeconds;
    
    /**
     * 播放百分比 (0-100)
     */
    @Column(name = "progress_percentage")
    private Integer progressPercentage = 0;
    
    /**
     * 是否已完成观看
     */
    @Column(name = "is_completed", nullable = false)
    private Boolean isCompleted = false;
    
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    @Column(name = "last_play_time")
    private LocalDateTime lastPlayTime;
    
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        lastPlayTime = LocalDateTime.now();
    }
    
    @PreUpdate
    protected void onUpdate() {
        lastPlayTime = LocalDateTime.now();
    }
}
