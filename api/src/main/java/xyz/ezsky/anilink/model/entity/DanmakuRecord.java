package xyz.ezsky.anilink.model.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 弹幕发送记录实体类
 * 记录用户通过本服务发送的弹幕
 */
@Entity
@Table(name = "danmaku_records", indexes = {
    @Index(name = "idx_danmaku_user", columnList = "user_id"),
    @Index(name = "idx_danmaku_episode", columnList = "episode_id"),
    @Index(name = "idx_danmaku_anime", columnList = "anime_id"),
    @Index(name = "idx_danmaku_created", columnList = "created_at")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DanmakuRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "username", nullable = false, length = 100)
    private String username;

    @Column(name = "episode_id", nullable = false)
    private Long episodeId;

    @Column(name = "anime_id")
    private Long animeId;

    @Column(name = "anime_title", length = 500)
    private String animeTitle;

    @Column(name = "video_id")
    private Long videoId;

    @Column(name = "episode_title", length = 500)
    private String episodeTitle;

    @Column(name = "time")
    private Double time;

    @Column(name = "mode")
    private Integer mode = 1;

    @Column(name = "color")
    private Integer color = 16777215;

    @Column(name = "comment", columnDefinition = "TEXT")
    private String comment;

    @Column(name = "cid")
    private Long cid;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
