package xyz.ezsky.anilink.model.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 追番记录实体类
 * 用户追番关系表，记录用户追番的番剧
 */
@Entity
@Table(name = "anime_follow", indexes = {
    @Index(name = "idx_follow_user_anime", columnList = "user_id,anime_id"),
    @Index(name = "idx_follow_user", columnList = "user_id"),
    @Index(name = "idx_follow_updated", columnList = "updated_at")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AnimeFollow {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "user_id", nullable = false)
    private Long userId;
    
    @Column(name = "anime_id")
    private Long animeId;

    @Column(name = "bangumi_subject_id")
    private Long bangumiSubjectId;

    @Column(name = "anime_title", length = 500)
    private String animeTitle;
    
    @Column(name = "image_url", length = 1000)
    private String imageUrl;
    
    /**
     * 追番状态，与 Bangumi 收藏类型对齐：
     * wish(想看/1)、watched(看过/2)、watching(在看/3)、on_hold(搁置/4)、dropped(抛弃/5)
     */
    @Column(name = "status", nullable = false, length = 20)
    private String status = "wish";
    
    /**
     * 用户的标签（tag），用于分类管理，如"待看"、"喜欢"等
     */
    @Column(name = "tags", length = 500)
    private String tags;
    
    @Column(name = "follow_at", nullable = false, updatable = false)
    private LocalDateTime followAt;
    
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
    
    @PrePersist
    protected void onCreate() {
        followAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }
    
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
