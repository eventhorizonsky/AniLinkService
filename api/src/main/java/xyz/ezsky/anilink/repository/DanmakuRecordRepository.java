package xyz.ezsky.anilink.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import xyz.ezsky.anilink.model.entity.DanmakuRecord;

/**
 * 弹幕发送记录 Repository
 */
public interface DanmakuRecordRepository extends JpaRepository<DanmakuRecord, Long> {

    Page<DanmakuRecord> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    @Query("""
        SELECT d FROM DanmakuRecord d
        WHERE (:userId IS NULL OR d.userId = :userId)
          AND (:episodeId IS NULL OR d.episodeId = :episodeId)
          AND (:animeId IS NULL OR d.animeId = :animeId)
          AND (:keyword IS NULL OR LOWER(d.comment) LIKE LOWER(CONCAT('%', :keyword, '%'))
               OR LOWER(d.username) LIKE LOWER(CONCAT('%', :keyword, '%'))
               OR LOWER(d.animeTitle) LIKE LOWER(CONCAT('%', :keyword, '%'))
               OR LOWER(d.episodeTitle) LIKE LOWER(CONCAT('%', :keyword, '%')))
        ORDER BY d.createdAt DESC
        """)
    Page<DanmakuRecord> searchDanmakuRecords(
            @Param("userId") Long userId,
            @Param("episodeId") Long episodeId,
            @Param("animeId") Long animeId,
            @Param("keyword") String keyword,
            Pageable pageable);
}
