package xyz.ezsky.anilink.model.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Entity
@Table(name = "anime", indexes = {
    @Index(name = "idx_anime_animeid", columnList = "anime_id")
})
@Data
public class Anime {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "anime_id", unique = true)
    private Long animeId;

    @Column(name = "title", length = 500)
    private String title;

    @Column(name = "type", length = 100)
    private String type;

    @Column(name = "image_url", length = 1000)
    private String imageUrl;

    @Column(name = "bangumi_subject_id")
    private Long bangumiSubjectId;

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    private LocalDateTime updatedAt = LocalDateTime.now();
}
