package xyz.ezsky.anilink.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Bangumi「猜你喜欢」个性化推荐生成结果（每用户一条，重新生成覆盖）。
 */
@Entity
@Table(name = "bangumi_recommend_record", uniqueConstraints = {
    @UniqueConstraint(name = "uk_bangumi_recommend_user", columnNames = "user_id")
}, indexes = {
    @Index(name = "idx_bangumi_recommend_user", columnList = "user_id")
})
@Data
public class BangumiRecommendRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** 参与计分的维度开关（10 位 0/1 数组 JSON，顺序同 BangumiRecommendService.REASONS） */
    @Column(name = "dimensions", length = 64, nullable = false)
    private String dimensions;

    /** 推荐结果列表 JSON（已按推荐分降序、剔除已收藏条目） */
    @Column(name = "result_json", columnDefinition = "TEXT", nullable = false)
    private String resultJson;

    /** 生成时参与计算的收藏条目数 */
    @Column(name = "collection_count", nullable = false)
    private Integer collectionCount;

    /** 推荐条目数 */
    @Column(name = "result_count", nullable = false)
    private Integer resultCount;

    @Column(name = "generated_at", nullable = false)
    private LocalDateTime generatedAt;

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    private LocalDateTime updatedAt = LocalDateTime.now();
}
