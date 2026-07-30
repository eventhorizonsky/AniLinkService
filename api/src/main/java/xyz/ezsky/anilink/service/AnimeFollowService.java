package xyz.ezsky.anilink.service;

import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import xyz.ezsky.anilink.model.dto.AnimeFollowDTO;
import xyz.ezsky.anilink.model.entity.AnimeFollow;
import xyz.ezsky.anilink.model.vo.AnimeFollowVO;
import xyz.ezsky.anilink.model.vo.PageVO;
import xyz.ezsky.anilink.repository.AnimeFollowRepository;
import lombok.extern.log4j.Log4j2;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 追番管理服务
 */
@Service
@Log4j2
public class AnimeFollowService {
    
    @Autowired
    private AnimeFollowRepository animeFollowRepository;

    @Autowired
    private BangumiSyncService bangumiSyncService;
    
    /**
     * 添加追番
     */
    @Transactional
    public AnimeFollowVO followAnime(Long userId, AnimeFollowDTO dto) {
        // 检查是否已追番
        Optional<AnimeFollow> existing = animeFollowRepository.findByUserIdAndAnimeId(userId, dto.getAnimeId());
        
        AnimeFollow follow;
        if (existing.isPresent()) {
            // 更新状态
            follow = existing.get();
            follow.setStatus(dto.getStatus() != null ? dto.getStatus() : "watching");
            follow.setTags(dto.getTags());
            follow.setUpdatedAt(LocalDateTime.now());
        } else {
            // 创建新记录
            follow = new AnimeFollow();
            follow.setUserId(userId);
            follow.setAnimeId(dto.getAnimeId());
            follow.setAnimeTitle(dto.getAnimeTitle());
            follow.setImageUrl(dto.getImageUrl());
            follow.setStatus(dto.getStatus() != null ? dto.getStatus() : "watching");
            follow.setTags(dto.getTags());
        }
        
        AnimeFollow saved = animeFollowRepository.save(follow);
        log.info("User {} followed anime {}", userId, dto.getAnimeId());
        // 异步同步到 Bangumi
        bangumiSyncService.syncFollowStatusToBangumi(userId, dto.getAnimeId(),
                dto.getStatus() != null ? dto.getStatus() : "watching");
        return convertToVO(saved);
    }
    
    /**
     * 取消追番
     */
    @Transactional
    public boolean unfollowAnime(Long userId, Long animeId) {
        Optional<AnimeFollow> follow = animeFollowRepository.findByUserIdAndAnimeId(userId, animeId);
        if (follow.isPresent()) {
            animeFollowRepository.delete(follow.get());
            log.info("User {} unfollowed anime {}", userId, animeId);
            return true;
        }
        return false;
    }
    
    /**
     * 获取用户的追番列表（分页）
     */
    public PageVO<AnimeFollowVO> getUserFollows(Long userId, int page, int pageSize) {
        Pageable pageable = PageRequest.of(page - 1, pageSize);
        Page<AnimeFollow> followPage = animeFollowRepository.findByUserIdOrderByUpdatedAtDesc(userId, pageable);
        
        List<AnimeFollowVO> data = followPage.getContent().stream()
                .map(this::convertToVO)
                .collect(Collectors.toList());
        
        return PageVO.<AnimeFollowVO>builder()
                .content(data)
                .totalElements(followPage.getTotalElements())
                .totalPages(followPage.getTotalPages())
                .currentPage(page)
                .pageSize(pageSize)
                .build();
    }
    
    /**
     * 获取用户的追番列表（不分页，按更新时间倒序）
     */
    public List<AnimeFollowVO> getUserFollowsList(Long userId) {
        return animeFollowRepository.findByUserIdOrderByUpdatedAtDesc(userId).stream()
                .map(this::convertToVO)
                .collect(Collectors.toList());
    }
    
    /**
     * 获取用户指定状态的追番列表
     */
    public List<AnimeFollowVO> getUserFollowsByStatus(Long userId, String status) {
        return animeFollowRepository.findByUserIdAndStatusOrderByUpdatedAtDesc(userId, status).stream()
                .map(this::convertToVO)
                .collect(Collectors.toList());
    }
    
    /**
     * 获取追番详情
     */
    public AnimeFollowVO getFollowDetail(Long userId, Long animeId) {
        Optional<AnimeFollow> follow = animeFollowRepository.findByUserIdAndAnimeId(userId, animeId);
        return follow.map(this::convertToVO).orElse(null);
    }
    
    /**
     * 检查用户是否追番过某个番剧
     */
    public boolean isFollowing(Long userId, Long animeId) {
        return animeFollowRepository.existsByUserIdAndAnimeId(userId, animeId);
    }
    
    /**
     * 更新追番状态
     */
    @Transactional
    public AnimeFollowVO updateFollowStatus(Long userId, Long animeId, String status) {
        Optional<AnimeFollow> follow = animeFollowRepository.findByUserIdAndAnimeId(userId, animeId);
        if (follow.isPresent()) {
            AnimeFollow entity = follow.get();
            entity.setStatus(status);
            entity.setUpdatedAt(LocalDateTime.now());
            AnimeFollow saved = animeFollowRepository.save(entity);
            // 异步同步到 Bangumi
            bangumiSyncService.syncFollowStatusToBangumi(userId, animeId, status);
            return convertToVO(saved);
        }
        return null;
    }
    
    /**
     * 获取追了某个番剧的所有用户ID
     */
    public List<Long> getUserIdsByAnimeId(Long animeId) {
        return animeFollowRepository.findByAnimeId(animeId).stream()
                .map(AnimeFollow::getUserId)
                .collect(Collectors.toList());
    }
    
    /**
     * 更新追番记录的时间戳（用于新剧集更新时）
     * 使用REQUIRES_NEW确保在独立事务中执行，不影响主流程
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void updateFollowTimestampAsync(Long userId, Long animeId) {
        Optional<AnimeFollow> follow = animeFollowRepository.findByUserIdAndAnimeId(userId, animeId);
        if (follow.isPresent()) {
            AnimeFollow entity = follow.get();
            entity.setUpdatedAt(LocalDateTime.now());
            animeFollowRepository.save(entity);
            log.debug("Updated timestamp for user {} anime follow record", userId);
        }
    }
    
    /**
     * 将实体转换为VO
     */
    private AnimeFollowVO convertToVO(AnimeFollow follow) {
        AnimeFollowVO vo = new AnimeFollowVO();
        BeanUtils.copyProperties(follow, vo);
        return vo;
    }
}
