package xyz.ezsky.anilink.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import xyz.ezsky.anilink.model.entity.AnimeFollow;

import java.util.List;
import java.util.Optional;

@Repository
public interface AnimeFollowRepository extends JpaRepository<AnimeFollow, Long> {
    
    /**
     * 根据用户ID和番剧ID查询追番记录
     */
    Optional<AnimeFollow> findByUserIdAndAnimeId(Long userId, Long animeId);
    
    /**
     * 根据用户ID查询追番列表（分页）
     */
    Page<AnimeFollow> findByUserIdOrderByUpdatedAtDesc(Long userId, Pageable pageable);
    
    /**
     * 根据用户ID查询追番列表（按更新时间倒序）
     */
    List<AnimeFollow> findByUserIdOrderByUpdatedAtDesc(Long userId);
    
    /**
     * 根据用户ID和状态查询追番记录（按更新时间倒序）
     */
    List<AnimeFollow> findByUserIdAndStatusOrderByUpdatedAtDesc(Long userId, String status);
    
    /**
     * 检查用户是否追番过某个番剧
     */
    boolean existsByUserIdAndAnimeId(Long userId, Long animeId);
    
    /**
     * 根据番剧ID查询所有追番记录
     */
    List<AnimeFollow> findByAnimeId(Long animeId);
    
    /**
     * 根据用户ID查询指定多个状态的追番（按更新时间倒序）
     */
    List<AnimeFollow> findByUserIdAndStatusInOrderByUpdatedAtDesc(Long userId, List<String> statuses);

    /**
     * 根据用户ID和标题关键词查询追番列表（分页，按更新时间倒序）
     */
    Page<AnimeFollow> findByUserIdAndAnimeTitleContainingIgnoreCaseOrderByUpdatedAtDesc(
            Long userId, String keyword, Pageable pageable);

    /**
     * 根据用户ID和标题关键词查询追番列表（按更新时间倒序）
     */
    List<AnimeFollow> findByUserIdAndAnimeTitleContainingIgnoreCaseOrderByUpdatedAtDesc(
            Long userId, String keyword);

    /**
     * 根据用户ID、状态和标题关键词查询追番列表（按更新时间倒序）
     */
    List<AnimeFollow> findByUserIdAndStatusAndAnimeTitleContainingIgnoreCaseOrderByUpdatedAtDesc(
            Long userId, String status, String keyword);

    /**
     * 根据用户ID、多个状态和标题关键词查询追番列表（按更新时间倒序）
     */
    List<AnimeFollow> findByUserIdAndStatusInAndAnimeTitleContainingIgnoreCaseOrderByUpdatedAtDesc(
            Long userId, List<String> statuses, String keyword);

    /**
     * 根据用户ID删除所有追番记录
     */
    void deleteByUserId(Long userId);
}
