package xyz.ezsky.anilink.model.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 用户实体类
 */
@Entity
@Table(name = "users")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class User {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(unique = true, nullable = false, length = 50)
    private String username;
    
    @Column(nullable = false)
    private String passwordHash;
    
    @Column(unique = true, length = 100)
    private String email;

    @Column(name = "remote_access_token", unique = true, length = 128)
    private String remoteAccessToken;

    @Column(name = "mcp_api_key", unique = true, length = 128)
    private String mcpApiKey;

    @Column(name = "bangumi_access_token", length = 255)
    private String bangumiAccessToken;

    @Column(name = "bangumi_username", length = 100)
    private String bangumiUsername;

    @Column(name = "bangumi_nickname", length = 100)
    private String bangumiNickname;

    @Column(name = "bangumi_user_id")
    private Long bangumiUserId;
    
    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;
    
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
    
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }
    
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
