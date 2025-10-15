package xyz.ezsky.anilink.model.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.GenericGenerator;

import java.sql.Timestamp;

@Entity
@Data
@SuppressWarnings("deprecation")
@Table(name = "media_file", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"library_id", "file_path"})
})
public class MediaFile {
    @Id
    @GenericGenerator(name = "snowflakeId", strategy = "xyz.ezsky.anilink.util.SnowflakeIdGenerator")
    @GeneratedValue(generator = "snowflakeId")
    private Long id;

    @ManyToOne
    @JoinColumn(name = "library_id", nullable = false)
    private MediaLibrary library;

    @Column(name = "file_path", nullable = false, length = 2048)
    private String filePath;

    @Column(name = "file_name", nullable = false)
    private String fileName;

    @Column(name = "last_modified", nullable = false)
    private Long lastModified;

    @Column(nullable = false)
    private Long size;

    @Column(name = "created_at", nullable = false, updatable = false, insertable = false, columnDefinition = "TIMESTAMP DEFAULT CURRENT_TIMESTAMP")
    private Timestamp createdAt;

    @Column(name = "updated_at", nullable = false, insertable = false, columnDefinition = "TIMESTAMP DEFAULT CURRENT_TIMESTAMP")
    private Timestamp updatedAt;
}
