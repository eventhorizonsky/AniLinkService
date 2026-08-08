package xyz.ezsky.anilink.repository;

import xyz.ezsky.anilink.model.entity.MediaFile;
import xyz.ezsky.anilink.model.entity.MatchStatus;
import xyz.ezsky.anilink.repository.base.BaseRepository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

public interface MediaFileRepository extends BaseRepository<MediaFile, Long> {
    List<MediaFile> findByLibraryId(Long libraryId);
    Optional<MediaFile> findByFilePath(String filePath);

    long countByLibraryId(Long libraryId);

    long countByMetadataFetchedTrue();

    long countByMetadataFetchedFalse();

    long countByLibraryIdAndMetadataFetchedTrue(Long libraryId);

    long countByMatchStatus(MatchStatus matchStatus);

    long countByAnimeId(Long animeId);

    /** 查找某个动漫在媒体库中的第一条记录，用于兜底重建 Anime 信息 */
    Optional<MediaFile> findFirstByAnimeId(Long animeId);

    /** Returns true when the media library contains at least one episode file for the anime. */
    boolean existsByAnimeId(Long animeId);

    /**
     * 查询媒体库中有剧集、但在 anime 表中尚未入库的 animeId（去重）。
     * 用于定时任务调用弹弹接口补录 Anime 记录。
     */
    @Query("SELECT DISTINCT m.animeId FROM MediaFile m WHERE m.animeId IS NOT NULL " +
            "AND NOT EXISTS (SELECT 1 FROM Anime a WHERE a.animeId = m.animeId)")
    List<Long> findAnimeIdsMissingInAnimeTable();

    long countByLibraryIdAndMatchStatus(Long libraryId, MatchStatus matchStatus);

    long countByMatchStatusIn(List<MatchStatus> matchStatuses);

    long countByLibraryIdAndMatchStatusIn(Long libraryId, List<MatchStatus> matchStatuses);

    /** 计算所有媒体文件的总大小（字节） */
    @Query("SELECT COALESCE(SUM(m.size), 0) FROM MediaFile m")
    long sumAllFileSize();

    // 新的分页查询，按数据库ID排序由调用方的 Pageable 决定
    Page<MediaFile> findByAnimeId(Long animeId, Pageable pageable);

    Page<MediaFile> findByMetadataFetchedFalse(Pageable pageable);

    Page<MediaFile> findByLibraryIdAndMetadataFetchedFalse(Long libraryId, Pageable pageable);

    Page<MediaFile> findByMatchStatusIn(List<MatchStatus> matchStatuses, Pageable pageable);

    Page<MediaFile> findByLibraryIdAndMatchStatusIn(Long libraryId, List<MatchStatus> matchStatuses, Pageable pageable);

        @Query("""
            SELECT m FROM MediaFile m
            WHERE (:libraryId IS NULL OR m.library.id = :libraryId)
              AND LOWER(m.fileName) LIKE LOWER(CONCAT('%', :keyword, '%'))
              AND (:matchStatuses IS NULL OR m.matchStatus IN :matchStatuses)
            """)
        Page<MediaFile> searchMediaFiles(
            @Param("libraryId") Long libraryId,
            @Param("keyword") String keyword,
            @Param("matchStatuses") List<MatchStatus> matchStatuses,
            Pageable pageable
        );

    /**
     * 查询库中指定匹配状态的文件
     * 
     * @param libraryId 媒体库 ID
     * @param matchStatuses 匹配状态数组
     * @return 符合条件的文件列表
     */
    List<MediaFile> findByLibraryIdAndMatchStatusIn(Long libraryId, List<MatchStatus> matchStatuses);

    /**
     * 查询库中指定匹配状态的文件（重载方法）
     */
    default List<MediaFile> findByLibraryIdAndMatchStatus(Long libraryId, MatchStatus[] matchStatuses) {
        return findByLibraryIdAndMatchStatusIn(libraryId, java.util.Arrays.asList(matchStatuses));
    }

    @Transactional
    void deleteByLibraryId(Long libraryId);
}
