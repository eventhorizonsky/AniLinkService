package xyz.ezsky.anilink.service;

import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import xyz.ezsky.anilink.model.dto.AnimeFollowDTO;
import xyz.ezsky.anilink.model.entity.Anime;
import xyz.ezsky.anilink.model.entity.AnimeFollow;
import xyz.ezsky.anilink.model.vo.AnimeFollowVO;
import xyz.ezsky.anilink.model.vo.PageVO;
import xyz.ezsky.anilink.repository.AnimeFollowRepository;
import lombok.extern.log4j.Log4j2;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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

    @Autowired
    private AnimeService animeService;
    
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
            follow.setStatus(dto.getStatus() != null ? dto.getStatus() : "wish");
            follow.setTags(dto.getTags());
            follow.setUpdatedAt(LocalDateTime.now());
        } else {
            // 创建新记录
            follow = new AnimeFollow();
            follow.setUserId(userId);
            follow.setAnimeId(dto.getAnimeId());
            follow.setAnimeTitle(dto.getAnimeTitle());
            follow.setImageUrl(dto.getImageUrl());
            follow.setStatus(dto.getStatus() != null ? dto.getStatus() : "wish");
            follow.setTags(dto.getTags());
        }

        AnimeFollow saved = animeFollowRepository.save(follow);
        log.info("User {} followed anime {}", userId, dto.getAnimeId());
        // 异步同步到 Bangumi
        bangumiSyncService.syncFollowStatusToBangumi(userId, dto.getAnimeId(),
                dto.getStatus() != null ? dto.getStatus() : "wish");
        return convertToVO(saved);
    }
    
    /**
     * 取消追番
     */
    @Transactional
    public boolean unfollowAnime(Long userId, Long animeId) {
        Optional<AnimeFollow> follow = animeFollowRepository.findByUserIdAndAnimeId(userId, animeId);
        if (follow.isPresent()) {
            // 删除前取出 Bangumi subjectId，供删除后同步（记录删除后无法再查询）
            Long bangumiSubjectId = follow.get().getBangumiSubjectId();
            animeFollowRepository.delete(follow.get());
            log.info("User {} unfollowed anime {}", userId, animeId);
            // 异步同步到 Bangumi：官方接口无删除收藏操作，降级为标记"抛弃"
            bangumiSyncService.syncUnfollowToBangumi(userId, animeId, bangumiSubjectId);
            return true;
        }
        return false;
    }
    
    /**
     * 获取用户的追番列表（分页），支持按标题关键词搜索
     */
    public PageVO<AnimeFollowVO> getUserFollows(Long userId, int page, int pageSize, String keyword) {
        selfHealDuplicateFollows(userId);
        Pageable pageable = PageRequest.of(page - 1, pageSize);
        Page<AnimeFollow> followPage = StringUtils.hasText(keyword)
                ? animeFollowRepository.findByUserIdAndAnimeTitleContainingIgnoreCaseOrderByUpdatedAtDesc(
                        userId, keyword.trim(), pageable)
                : animeFollowRepository.findByUserIdOrderByUpdatedAtDesc(userId, pageable);
        
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
     * 获取用户的追番列表（不分页，按更新时间倒序），支持按标题关键词搜索
     */
    public List<AnimeFollowVO> getUserFollowsList(Long userId, String keyword) {
        selfHealDuplicateFollows(userId);
        List<AnimeFollow> follows = StringUtils.hasText(keyword)
                ? animeFollowRepository.findByUserIdAndAnimeTitleContainingIgnoreCaseOrderByUpdatedAtDesc(
                        userId, keyword.trim())
                : animeFollowRepository.findByUserIdOrderByUpdatedAtDesc(userId);
        return follows.stream()
                .map(this::convertToVO)
                .collect(Collectors.toList());
    }
    
    /**
     * 获取用户指定状态的追番列表，支持按标题关键词搜索
     */
    public List<AnimeFollowVO> getUserFollowsByStatus(Long userId, String status, String keyword) {
        selfHealDuplicateFollows(userId);
        List<AnimeFollow> follows = StringUtils.hasText(keyword)
                ? animeFollowRepository.findByUserIdAndStatusAndAnimeTitleContainingIgnoreCaseOrderByUpdatedAtDesc(
                        userId, status, keyword.trim())
                : animeFollowRepository.findByUserIdAndStatusOrderByUpdatedAtDesc(userId, status);
        return follows.stream()
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
     * 获取用户活跃追番（想看 + 在看），支持按标题关键词搜索。
     */
    public List<AnimeFollowVO> getActiveFollows(Long userId, String keyword) {
        selfHealDuplicateFollows(userId);
        List<String> activeStatuses = List.of("wish", "watching");
        List<AnimeFollow> follows = StringUtils.hasText(keyword)
                ? animeFollowRepository.findByUserIdAndStatusInAndAnimeTitleContainingIgnoreCaseOrderByUpdatedAtDesc(
                        userId, activeStatuses, keyword.trim())
                : animeFollowRepository.findByUserIdAndStatusInOrderByUpdatedAtDesc(userId, activeStatuses);
        return follows.stream()
                .map(this::convertToVO)
                .collect(Collectors.toList());
    }

    /**
     * 查询前自愈：合并当前用户重复的追番记录（同 animeId / 同 subjectId）。
     * 清理失败不影响查询主流程。
     */
    private void selfHealDuplicateFollows(Long userId) {
        try {
            bangumiSyncService.mergeDuplicateFollows(userId);
        } catch (Exception e) {
            log.warn("Failed to self-heal duplicate follows for userId={}: {}", userId, e.getMessage());
        }
    }

    /**
     * 绑定未匹配的追番记录到本地番剧。
     */
    @Transactional
    public AnimeFollowVO bindFollow(Long userId, Long followId, Long animeId, String animeTitle, String imageUrl) {
        Optional<AnimeFollow> opt = animeFollowRepository.findById(followId);
        if (opt.isEmpty()) return null;
        AnimeFollow follow = opt.get();
        if (!follow.getUserId().equals(userId)) return null;
        follow.setAnimeId(animeId);
        if (animeTitle != null) follow.setAnimeTitle(animeTitle);
        // 封面优先保留已有的 Bangumi 图片，仅缺失时用弹弹的补充
        if (imageUrl != null && (follow.getImageUrl() == null || follow.getImageUrl().isBlank())) {
            follow.setImageUrl(imageUrl);
        }
        // 双向关联：把追番上的 Bangumi subjectId 回写到本地番剧记录，便于后续同步匹配
        animeService.attachBangumiSubjectId(animeId, follow.getBangumiSubjectId());
        follow.setUpdatedAt(LocalDateTime.now());
        return convertToVO(animeFollowRepository.save(follow));
    }

    /**
     * 自动匹配未绑定追番：通过 Bangumi subjectId 查询弹弹并绑定本地番剧。
     * 查不到时返回 matched=false，由前端弹出手动绑定。
     *
     * @return null 表示追番记录不存在或不属于当前用户；
     *         否则返回包含 matched / follow / animeId / animeTitle 的结果 Map
     */
    @Transactional
    public Map<String, Object> matchAndBindFollow(Long userId, Long followId) {
        Optional<AnimeFollow> opt = animeFollowRepository.findById(followId);
        if (opt.isEmpty()) return null;
        AnimeFollow follow = opt.get();
        if (!follow.getUserId().equals(userId)) return null;

        Map<String, Object> result = new LinkedHashMap<>();

        // 已绑定则直接返回
        if (follow.getAnimeId() != null) {
            result.put("matched", true);
            result.put("alreadyBound", true);
            result.put("follow", convertToVO(follow));
            return result;
        }

        Long subjectId = follow.getBangumiSubjectId();
        if (subjectId == null) {
            result.put("matched", false);
            result.put("message", "该追番缺少 Bangumi subjectId，无法自动匹配");
            result.put("follow", convertToVO(follow));
            return result;
        }

        Anime anime = animeService.matchAnimeByBangumiSubjectId(subjectId);
        if (anime == null || anime.getAnimeId() == null) {
            result.put("matched", false);
            result.put("message", "弹弹中未找到对应番剧");
            result.put("follow", convertToVO(follow));
            return result;
        }

        follow.setAnimeId(anime.getAnimeId());
        if (anime.getTitle() != null && !anime.getTitle().isBlank()) {
            follow.setAnimeTitle(anime.getTitle());
        }
        // 封面保持 Bangumi 图片，匹配时不再改成弹弹的
        follow.setUpdatedAt(LocalDateTime.now());
        AnimeFollow saved = animeFollowRepository.save(follow);

        result.put("matched", true);
        result.put("animeId", anime.getAnimeId());
        result.put("animeTitle", anime.getTitle());
        result.put("follow", convertToVO(saved));
        return result;
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
