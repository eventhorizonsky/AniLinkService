package xyz.ezsky.anilink.service;

import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import xyz.ezsky.anilink.model.entity.DanmakuRecord;
import xyz.ezsky.anilink.model.vo.DanmakuRecordVO;
import xyz.ezsky.anilink.model.vo.PageVO;
import xyz.ezsky.anilink.repository.DanmakuRecordRepository;

import java.util.stream.Collectors;

/**
 * 弹幕记录查询服务
 */
@Service
@Log4j2
public class DanmakuRecordService {

    @Autowired
    private DanmakuRecordRepository danmakuRecordRepository;

    /**
     * 获取用户的弹幕记录（分页）
     */
    public PageVO<DanmakuRecordVO> getUserRecords(Long userId, int page, int pageSize) {
        Pageable pageable = PageRequest.of(page - 1, pageSize);
        Page<DanmakuRecord> pageResult = danmakuRecordRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable);

        return PageVO.<DanmakuRecordVO>builder()
                .content(pageResult.getContent().stream().map(this::convertToVO).collect(Collectors.toList()))
                .totalElements(pageResult.getTotalElements())
                .totalPages(pageResult.getTotalPages())
                .currentPage(page)
                .pageSize(pageSize)
                .hasNext(pageResult.hasNext())
                .hasPrevious(pageResult.hasPrevious())
                .build();
    }

    /**
     * 管理员查询弹幕记录（支持筛选）
     */
    public PageVO<DanmakuRecordVO> searchAdmin(Long userId, Long episodeId, Long animeId,
                                               String keyword, int page, int pageSize) {
        Pageable pageable = PageRequest.of(page - 1, pageSize);
        Page<DanmakuRecord> pageResult = danmakuRecordRepository.searchDanmakuRecords(
                userId, episodeId, animeId,
                (keyword != null && !keyword.isBlank()) ? keyword.trim() : null,
                pageable);

        return PageVO.<DanmakuRecordVO>builder()
                .content(pageResult.getContent().stream().map(this::convertToVO).collect(Collectors.toList()))
                .totalElements(pageResult.getTotalElements())
                .totalPages(pageResult.getTotalPages())
                .currentPage(page)
                .pageSize(pageSize)
                .hasNext(pageResult.hasNext())
                .hasPrevious(pageResult.hasPrevious())
                .build();
    }

    private DanmakuRecordVO convertToVO(DanmakuRecord record) {
        return DanmakuRecordVO.builder()
                .id(record.getId())
                .userId(record.getUserId())
                .username(record.getUsername())
                .episodeId(record.getEpisodeId())
                .animeId(record.getAnimeId())
                .animeTitle(record.getAnimeTitle())
                .videoId(record.getVideoId())
                .episodeTitle(record.getEpisodeTitle())
                .time(record.getTime())
                .mode(record.getMode())
                .color(record.getColor())
                .comment(record.getComment())
                .cid(record.getCid())
                .createdAt(record.getCreatedAt())
                .build();
    }
}
