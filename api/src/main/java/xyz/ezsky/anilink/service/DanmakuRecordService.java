package xyz.ezsky.anilink.service;

import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import xyz.ezsky.anilink.model.entity.Anime;
import xyz.ezsky.anilink.model.entity.DanmakuRecord;
import xyz.ezsky.anilink.model.vo.DanmakuRecordVO;
import xyz.ezsky.anilink.model.vo.PageVO;
import xyz.ezsky.anilink.repository.AnimeRepository;
import xyz.ezsky.anilink.repository.DanmakuRecordRepository;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 弹幕记录查询服务
 */
@Service
@Log4j2
public class DanmakuRecordService {

    @Autowired
    private DanmakuRecordRepository danmakuRecordRepository;

    @Autowired
    private AnimeRepository animeRepository;

    /**
     * 获取用户的弹幕记录（分页）
     */
    public PageVO<DanmakuRecordVO> getUserRecords(Long userId, int page, int pageSize) {
        Pageable pageable = PageRequest.of(page - 1, pageSize);
        Page<DanmakuRecord> pageResult = danmakuRecordRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable);

        List<DanmakuRecordVO> data = pageResult.getContent().stream().map(this::convertToVO).collect(Collectors.toList());
        fillImageUrls(data);

        return PageVO.<DanmakuRecordVO>builder()
                .content(data)
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

        List<DanmakuRecordVO> data = pageResult.getContent().stream().map(this::convertToVO).collect(Collectors.toList());
        fillImageUrls(data);

        return PageVO.<DanmakuRecordVO>builder()
                .content(data)
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

    /**
     * 批量填充封面图（关联 anime 表），避免逐条查询（N+1）
     */
    private void fillImageUrls(List<DanmakuRecordVO> vos) {
        if (vos == null || vos.isEmpty()) {
            return;
        }
        Set<Long> animeIds = vos.stream()
                .map(DanmakuRecordVO::getAnimeId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (animeIds.isEmpty()) {
            return;
        }
        Map<Long, String> imageMap = animeRepository.findByAnimeIdIn(animeIds).stream()
                .collect(Collectors.toMap(Anime::getAnimeId, Anime::getImageUrl, (a, b) -> a));
        vos.forEach(vo -> vo.setImageUrl(imageMap.getOrDefault(vo.getAnimeId(), null)));
    }
}
