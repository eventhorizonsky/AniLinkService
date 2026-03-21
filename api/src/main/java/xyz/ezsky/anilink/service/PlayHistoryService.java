package xyz.ezsky.anilink.service;

import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import xyz.ezsky.anilink.model.dto.PlayHistoryDTO;
import xyz.ezsky.anilink.model.entity.PlayHistory;
import xyz.ezsky.anilink.model.vo.PageVO;
import xyz.ezsky.anilink.model.vo.PlayHistoryVO;
import xyz.ezsky.anilink.repository.PlayHistoryRepository;
import lombok.extern.log4j.Log4j2;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 播放历史记录服务
 */
@Service
@Log4j2
public class PlayHistoryService {
    
    @Autowired
    private PlayHistoryRepository playHistoryRepository;
    
    /**
     * 更新播放进度
     */
    @Transactional
    public PlayHistoryVO updatePlayProgress(Long userId, PlayHistoryDTO dto) {
        if (dto.getAnimeId() == null) {
            throw new IllegalArgumentException("animeId不能为空");
        }

        // 一个用户对同一部番剧只保留一条记录，重复播放时覆盖到最新播放视频与进度。
        Optional<PlayHistory> existing = playHistoryRepository.findByUserIdAndAnimeId(userId, dto.getAnimeId());
        
        PlayHistory history;
        if (existing.isPresent()) {
            history = existing.get();
            history.setAnimeTitle(dto.getAnimeTitle());
            history.setVideoId(dto.getVideoId());
            history.setVideoName(dto.getVideoName());
            history.setEpisodeId(dto.getEpisodeId());
            history.setDurationSeconds(dto.getDurationSeconds());
            history.setProgressSeconds(dto.getProgressSeconds());
            history.setProgressPercentage(calculatePercentage(dto.getProgressSeconds(), dto.getDurationSeconds()));
            history.setIsCompleted(dto.getIsCompleted() != null && dto.getIsCompleted());
        } else {
            // 创建新的播放记录
            history = new PlayHistory();
            history.setUserId(userId);
            history.setVideoId(dto.getVideoId());
            history.setVideoName(dto.getVideoName());
            history.setAnimeId(dto.getAnimeId());
            history.setAnimeTitle(dto.getAnimeTitle());
            history.setEpisodeId(dto.getEpisodeId());
            history.setDurationSeconds(dto.getDurationSeconds());
            history.setProgressSeconds(dto.getProgressSeconds());
            history.setProgressPercentage(calculatePercentage(dto.getProgressSeconds(), dto.getDurationSeconds()));
            history.setIsCompleted(dto.getIsCompleted() != null && dto.getIsCompleted());
        }
        
        PlayHistory saved = playHistoryRepository.save(history);
        log.info("User {} updated play progress for anime {}", userId, dto.getAnimeId());
        return convertToVO(saved);
    }
    
    /**
     * 获取用户的播放历史（分页）
     */
    public PageVO<PlayHistoryVO> getUserPlayHistory(Long userId, int page, int pageSize) {
        Pageable pageable = PageRequest.of(page - 1, pageSize);
        Page<PlayHistory> historyPage = playHistoryRepository.findByUserIdOrderByLastPlayTimeDesc(userId, pageable);
        
        List<PlayHistoryVO> data = historyPage.getContent().stream()
                .map(this::convertToVO)
                .collect(Collectors.toList());
        
        return PageVO.<PlayHistoryVO>builder()
                .content(data)
                .totalElements(historyPage.getTotalElements())
                .totalPages(historyPage.getTotalPages())
                .currentPage(page)
                .pageSize(pageSize)
                .build();
    }
    
    /**
     * 获取指定番剧的播放进度
     */
    public PlayHistoryVO getAnimePlayProgress(Long userId, Long animeId) {
        Optional<PlayHistory> history = playHistoryRepository.findByUserIdAndAnimeId(userId, animeId);
        return history.map(this::convertToVO).orElse(null);
    }
    
    /**
     * 清空用户的播放历史
     */
    @Transactional
    public void clearUserPlayHistory(Long userId) {
        playHistoryRepository.deleteByUserId(userId);
        log.info("User {}'s play history cleared", userId);
    }
    
    /**
     * 删除指定的播放记录
     */
    @Transactional
    public boolean deletePlayHistory(Long userId, Long historyId) {
        Optional<PlayHistory> history = playHistoryRepository.findById(historyId);
        if (history.isPresent() && history.get().getUserId().equals(userId)) {
            playHistoryRepository.delete(history.get());
            return true;
        }
        return false;
    }
    
    /**
     * 计算播放百分比
     */
    private Integer calculatePercentage(Long progressSeconds, Long durationSeconds) {
        if (durationSeconds == null || durationSeconds == 0) {
            return 0;
        }
        int percentage = (int) ((progressSeconds * 100) / durationSeconds);
        return Math.min(percentage, 100);
    }
    
    /**
     * 将实体转换为VO
     */
    private PlayHistoryVO convertToVO(PlayHistory history) {
        PlayHistoryVO vo = new PlayHistoryVO();
        BeanUtils.copyProperties(history, vo);
        return vo;
    }
}
