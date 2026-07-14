package xyz.ezsky.anilink.model.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.GenericGenerator;

import java.sql.Timestamp;

@Entity
@Data
@SuppressWarnings("deprecation")
@Table(name = "resource_rss_subscription")
public class ResourceRssSubscription {
    @Id
    @GenericGenerator(name = "snowflakeId", strategy = "xyz.ezsky.anilink.util.SnowflakeIdGenerator")
    @GeneratedValue(generator = "snowflakeId")
    private Long id;

    @Column(nullable = false, length = 255)
    private String name;

    @Column(nullable = false, length = 2048)
    private String feedUrl;

    @ManyToOne
    @JoinColumn(name = "library_id", nullable = false)
    private MediaLibrary library;

    @Column(name = "interval_minutes", nullable = false)
    private Integer intervalMinutes;

    @Column(nullable = false)
    private Boolean enabled = true;

    @Column(name = "last_checked_at")
    private Timestamp lastCheckedAt;

    @Column(name = "last_success_at")
    private Timestamp lastSuccessAt;

    @Column(name = "last_error", columnDefinition = "TEXT")
    private String lastError;

    @Column(name = "last_fetched_content", columnDefinition = "TEXT")
    private String lastFetchedContent;

    @Column(name = "include_filter", length = 2048)
    private String includeFilter;

    @Column(name = "exclude_filter", length = 2048)
    private String excludeFilter;

    @Column(name = "created_at", nullable = false, updatable = false, insertable = false, columnDefinition = "TIMESTAMP DEFAULT CURRENT_TIMESTAMP")
    private Timestamp createdAt;

    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false, columnDefinition = "TIMESTAMP DEFAULT CURRENT_TIMESTAMP")
    private Timestamp updatedAt;
}
