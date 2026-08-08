package xyz.ezsky.anilink.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import xyz.ezsky.anilink.model.entity.Anime;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface AnimeRepository extends JpaRepository<Anime, Long> {
    Optional<Anime> findByAnimeId(Long animeId);
    List<Anime> findByAnimeIdIn(Collection<Long> animeIds);

    /**
     * 按 animeId 批量删除 Anime 记录。
     * 使用 bulk delete 并自带事务，保证在无外部事务的调用场景（如定时任务）下也能可靠执行。
     */
    @Transactional
    @Modifying
    @Query("DELETE FROM Anime a WHERE a.animeId IN :animeIds")
    void deleteByAnimeIdIn(@Param("animeIds") Collection<Long> animeIds);
    
    /**
     * 根据标题模糊查询动漫
     *
     * @param keyword 搜索关键词
     * @param pageable 分页信息
     * @return 匹配结果
     */
    Page<Anime> findByTitleContainingIgnoreCase(String keyword, Pageable pageable);

    /**
     * 查询媒体库中没有剧集的 Anime animeId（去重）。
     * 用于定时任务清理孤儿 Anime 记录。
     */
    @Query("SELECT a.animeId FROM Anime a WHERE a.animeId IS NOT NULL " +
            "AND NOT EXISTS (SELECT 1 FROM MediaFile m WHERE m.animeId = a.animeId)")
    List<Long> findAnimeIdsWithoutMediaFiles();
}
