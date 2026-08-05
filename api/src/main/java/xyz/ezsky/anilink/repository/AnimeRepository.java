package xyz.ezsky.anilink.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import xyz.ezsky.anilink.model.entity.Anime;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface AnimeRepository extends JpaRepository<Anime, Long> {
    Optional<Anime> findByAnimeId(Long animeId);
    List<Anime> findByAnimeIdIn(Collection<Long> animeIds);
    void deleteByAnimeIdIn(Collection<Long> animeIds);
    
    /**
     * 根据标题模糊查询动漫
     *
     * @param keyword 搜索关键词
     * @param pageable 分页信息
     * @return 匹配结果
     */
    Page<Anime> findByTitleContainingIgnoreCase(String keyword, Pageable pageable);
}
