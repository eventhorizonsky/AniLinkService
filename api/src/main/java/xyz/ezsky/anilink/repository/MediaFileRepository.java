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

    long countByLibraryIdAndMatchStatus(Long libraryId, MatchStatus matchStatus);

    long countByMatchStatusIn(List<MatchStatus> matchStatuses);

    long countByLibraryIdAndMatchStatusIn(Long libraryId, List<MatchStatus> matchStatuses);

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
