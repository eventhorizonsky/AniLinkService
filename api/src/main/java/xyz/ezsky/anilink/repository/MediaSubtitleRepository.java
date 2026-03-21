package xyz.ezsky.anilink.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;
import xyz.ezsky.anilink.model.entity.MediaSubtitle;
import xyz.ezsky.anilink.repository.base.BaseRepository;

import java.util.List;
import java.util.Optional;

public interface MediaSubtitleRepository extends BaseRepository<MediaSubtitle, Long> {
    List<MediaSubtitle> findByMediaFileIdOrderByStreamIndexAsc(Long mediaFileId);

        @Query(value = """
            SELECT s FROM MediaSubtitle s
            JOIN FETCH s.mediaFile m
            JOIN FETCH m.library l
        WHERE (LOWER(s.fileName) LIKE LOWER(CONCAT('%', :keyword, '%'))
           OR LOWER(s.trackName) LIKE LOWER(CONCAT('%', :keyword, '%'))
           OR LOWER(m.fileName) LIKE LOWER(CONCAT('%', :keyword, '%'))
           OR LOWER(COALESCE(m.animeTitle, '')) LIKE LOWER(CONCAT('%', :keyword, '%')))
          AND (:sourceType IS NULL OR s.sourceType = :sourceType)
            """,
            countQuery = """
            SELECT COUNT(s) FROM MediaSubtitle s
            JOIN s.mediaFile m
            JOIN m.library l
            WHERE (LOWER(s.fileName) LIKE LOWER(CONCAT('%', :keyword, '%'))
               OR LOWER(s.trackName) LIKE LOWER(CONCAT('%', :keyword, '%'))
               OR LOWER(m.fileName) LIKE LOWER(CONCAT('%', :keyword, '%'))
               OR LOWER(COALESCE(m.animeTitle, '')) LIKE LOWER(CONCAT('%', :keyword, '%')))
              AND (:sourceType IS NULL OR s.sourceType = :sourceType)
            """)
    Page<MediaSubtitle> searchSubtitles(
        @Param("keyword") String keyword,
        @Param("sourceType") String sourceType,
        Pageable pageable
    );

    Optional<MediaSubtitle> findByMediaFileIdAndStreamIndex(Long mediaFileId, Integer streamIndex);

    @Transactional
    void deleteByMediaFileId(Long mediaFileId);

    @Transactional
    void deleteByMediaFileLibraryId(Long libraryId);
}
