package xyz.ezsky.anilink.model.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.GenericGenerator;

import java.sql.Timestamp;

@Entity
@Data
@SuppressWarnings("deprecation")
@Table(name = "resource_download_task")
public class ResourceDownloadTask {
    @Id
    @GenericGenerator(name = "snowflakeId", strategy = "xyz.ezsky.anilink.util.SnowflakeIdGenerator")
    @GeneratedValue(generator = "snowflakeId")
    private Long id;

    @Column(nullable = false, length = 600)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String magnet;

    @Column(name = "page_url", length = 1200)
    private String pageUrl;

    @Column(name = "file_size", length = 120)
    private String fileSize;

    @Column(name = "publish_date", length = 64)
    private String publishDate;

    @Column(name = "subgroup_name", length = 255)
    private String subgroupName;

    @Column(name = "type_name", length = 255)
    private String typeName;

    @ManyToOne
    @JoinColumn(name = "library_id", nullable = false)
    private MediaLibrary library;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private DownloadStatus status = DownloadStatus.PENDING;

    @Column(name = "progress_percent")
    private Integer progressPercent;

    @Column(name = "downloaded_bytes")
    private Long downloadedBytes;

    @Column(name = "total_bytes")
    private Long totalBytes;

    @Column(name = "speed_text", length = 100)
    private String speedText;

    @Column(name = "output_message", columnDefinition = "TEXT")
    private String outputMessage;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "temp_dir", length = 2048)
    private String tempDir;

    @Column(name = "final_path", length = 2048)
    private String finalPath;

    @Column(name = "media_file_id")
    private Long mediaFileId;

    @Column(name = "started_at")
    private Timestamp startedAt;

    @Column(name = "finished_at")
    private Timestamp finishedAt;

    @Column(name = "created_at", nullable = false, updatable = false, insertable = false, columnDefinition = "TIMESTAMP DEFAULT CURRENT_TIMESTAMP")
    private Timestamp createdAt;

    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false, columnDefinition = "TIMESTAMP DEFAULT CURRENT_TIMESTAMP")
    private Timestamp updatedAt;

    public enum DownloadStatus {
        PENDING,
        RUNNING,
        SEEDING,
        MOVING,
        SCANNING,
        COMPLETED,
        CANCELLED,
        FAILED
    }
}
