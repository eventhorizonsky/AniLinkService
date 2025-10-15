package xyz.ezsky.anilink.model.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.GenericGenerator;

@Entity
@Data
@SuppressWarnings("deprecation")
@Table(name = "media_library")
public class MediaLibrary {
    @Id
    @GenericGenerator(name = "snowflakeId", strategy = "xyz.ezsky.anilink.util.SnowflakeIdGenerator")
    @GeneratedValue(generator = "snowflakeId")
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, length = 1024)
    private String path;
}
