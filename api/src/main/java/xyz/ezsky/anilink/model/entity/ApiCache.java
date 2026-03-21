package xyz.ezsky.anilink.model.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Entity
@Table(name = "api_cache", indexes = {
    @Index(name = "idx_api_cache_expire_time", columnList = "expire_time")
})
@Data
public class ApiCache {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "cache_key", length = 255, nullable = false, unique = true)
    private String cacheKey;

    @Column(name = "cache_value", columnDefinition = "TEXT", nullable = false)
    private String cacheValue;

    @Column(name = "expire_time", nullable = false)
    private LocalDateTime expireTime;

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    private LocalDateTime updatedAt = LocalDateTime.now();
}
