package xyz.ezsky.anilink.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import xyz.ezsky.anilink.model.entity.BangumiRecommendRecord;

import java.util.Optional;

/**
 * Bangumi「猜你喜欢」推荐结果 Repository
 */
public interface BangumiRecommendRecordRepository extends JpaRepository<BangumiRecommendRecord, Long> {

    Optional<BangumiRecommendRecord> findByUserId(Long userId);
}
