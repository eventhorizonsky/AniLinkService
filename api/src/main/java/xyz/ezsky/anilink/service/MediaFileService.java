package xyz.ezsky.anilink.service;

import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import xyz.ezsky.anilink.model.dto.MediaFileDTO;
import xyz.ezsky.anilink.model.dto.MatchResult;
import xyz.ezsky.anilink.model.dto.UpdateMediaFileRequest;
import xyz.ezsky.anilink.model.entity.MatchStatus;
import xyz.ezsky.anilink.model.entity.MediaFile;
import xyz.ezsky.anilink.model.vo.LibraryItemVO;
import xyz.ezsky.anilink.model.vo.MatchProgressVO;
import xyz.ezsky.anilink.model.vo.MetadataProgressVO;
import xyz.ezsky.anilink.model.vo.PageVO;
import xyz.ezsky.anilink.repository.MediaFileRepository;
import xyz.ezsky.anilink.repository.MediaLibraryRepository;

import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * 媒体文件业务服务
 * 
 * 负责处理媒体文件的查询、更新、删除等操作
 */
@Log4j2
@Service
public class MediaFileService {

    @Autowired
    private MediaFileRepository mediaFileRepository;
    
    @Autowired
    private xyz.ezsky.anilink.service.notification.EpisodeUpdateNotificationService episodeUpdateNotificationService;

    @Autowired
    private MediaLibraryRepository mediaLibraryRepository;

    @Autowired
    private MediaMetadataQueueManager metadataQueueManager;

    @Autowired
    private MediaSubtitleService mediaSubtitleService;

    @Autowired
    private MediaMatchQueueManager mediaMatchQueueManager;

    @Autowired
    private DandanMatchService dandanMatchService;

    @Autowired
    private MediaHashService mediaHashService;

    @Autowired
    private xyz.ezsky.anilink.repository.PlayHistoryRepository playHistoryRepository;

    /**
     * 分页查询媒体文件
     * 
     * @param libraryId 媒体库ID（可选，为null时查询所有）
     * @param page 页码（从0开始）
     * @param pageSize 每页大小
     * @return 分页结果
     */
    public PageVO<MediaFileDTO> getMediaFiles(Long libraryId, int page, int pageSize, String keyword, Boolean matched) {
        Pageable pageable = PageRequest.of(page, pageSize);

        String normalizedKeyword = keyword == null ? "" : keyword.trim();

        List<MatchStatus> matchStatuses = resolveMatchStatuses(matched);
        Page<MediaFile> result = mediaFileRepository.searchMediaFiles(libraryId, normalizedKeyword, matchStatuses, pageable);

        return PageVO.<MediaFileDTO>builder()
                .content(result.getContent().stream().map(this::toDTO).collect(Collectors.toList()))
                .totalElements(result.getTotalElements())
                .totalPages(result.getTotalPages())
                .currentPage(result.getNumber())
                .pageSize(result.getSize())
                .hasNext(result.hasNext())
                .hasPrevious(result.hasPrevious())
                .build();
    }

    /**
     * 重新搜索并更新单个视频文件的弹幕匹配结果。
     */
    public MediaFileDTO rematchMediaFile(Long fileId) {
        MediaFile mediaFile = mediaFileRepository.findById(fileId)
                .orElseThrow(() -> new IllegalArgumentException("媒体文件不存在"));

        ensureHash(mediaFile);

        Map<String, Object> fileInfo = DandanMatchService.createFileInfo(
                mediaFile.getFileName(),
                mediaFile.getHash(),
                mediaFile.getSize()
        );
        List<MatchResult> matchResults = dandanMatchService.batchMatch(List.of(fileInfo));

        if (matchResults.isEmpty()) {
            mediaFile.setMatchStatus(MatchStatus.NO_MATCH_FOUND);
        } else {
            MatchResult result = matchResults.get(0);
            if (Boolean.TRUE.equals(result.getSuccess())) {
                mediaFile.setMatchStatus(MatchStatus.MATCHED);
                mediaFile.setEpisodeId(result.getEpisodeId());
                mediaFile.setAnimeId(result.getAnimeId());
                mediaFile.setAnimeTitle(result.getAnimeTitle());
                mediaFile.setEpisodeTitle(result.getEpisodeTitle());
            } else {
                mediaFile.setMatchStatus(MatchStatus.NO_MATCH_FOUND);
                mediaFile.setEpisodeId(null);
                mediaFile.setAnimeId(null);
                mediaFile.setAnimeTitle(null);
                mediaFile.setEpisodeTitle(null);
            }
        }

        MediaFile saved = mediaFileRepository.save(mediaFile);
        
        // 如果手动匹配成功，也异步通知追番用户
        if (saved.getMatchStatus() == MatchStatus.MATCHED && saved.getAnimeId() != null) {
            try {
                episodeUpdateNotificationService.notifyFollowingUsersAsync(saved);
            } catch (Exception e) {
                log.warn("Failed to trigger notification for rematch: {}", e.getMessage());
            }
        }
        
        return toDTO(saved);
    }

    /**
     * 仅获取单个视频文件的自动匹配候选原始结果，不直接写回数据库。
     */
    public String getRematchCandidatesRaw(Long fileId) {
        MediaFile mediaFile = mediaFileRepository.findById(fileId)
                .orElseThrow(() -> new IllegalArgumentException("媒体文件不存在"));

        ensureHash(mediaFile);
        return dandanMatchService.matchRawByFile(mediaFile.getFileName(), mediaFile.getHash(), mediaFile.getSize());
    }

    /**
     * 获取媒体文件详情
     * 
     * @param fileId 文件ID
     * @return 文件详情DTO
     */
    public MediaFileDTO getMediaFileDetail(Long fileId) {
        return mediaFileRepository.findById(fileId)
                .map(this::toDTO)
                .orElse(null);
    }

    /**
     * 获取媒体文件实体
     * 
     * @param fileId 文件ID
     * @return 媒体文件实体
     */
    public MediaFile getMediaFileById(Long fileId) {
        return mediaFileRepository.findById(fileId).orElse(null);
    }

    /**
     * 批量重新获取指定媒体库的元数据（异步）
     * 
     * @param libraryId 媒体库ID
     * @return CompletableFuture<Void>
     */
    @Async
    public CompletableFuture<Void> reprocessMetadataForLibrary(Long libraryId) {
        List<MediaFile> files = mediaFileRepository.findByLibraryId(libraryId);
        
        for (MediaFile file : files) {
            // 重置元数据标志
            file.setMetadataFetched(false);
            mediaFileRepository.save(file);
        }

        // 触发后台处理器按批次从数据库拉取待处理任务
        metadataQueueManager.triggerProcessing();
        
        log.info("Submitted {} files from library {} for metadata reprocessing", files.size(), libraryId);
        return CompletableFuture.completedFuture(null);
    }

    public MetadataProgressVO getMetadataProgress(Long libraryId) {
        long totalFiles = libraryId == null ? mediaFileRepository.count() : mediaFileRepository.countByLibraryId(libraryId);
        long metadataFetched = libraryId == null
                ? mediaFileRepository.countByMetadataFetchedTrue()
                : mediaFileRepository.countByLibraryIdAndMetadataFetchedTrue(libraryId);
        
        // 库级别的待处理数 = 总文件数 - 已获取元数据的文件数
        long libraryPendingMetadata = Math.max(0, totalFiles - metadataFetched);

        // DB 驱动模型下，UI 展示优先使用数据库快照口径，避免运行时累计计数与当前状态不一致。
        long processedFromDb = metadataFetched;
        long submittedFromDb = metadataFetched + libraryPendingMetadata;
        int queuePending = libraryId == null
                ? metadataQueueManager.getQueueSize()
                : (int) Math.min(Integer.MAX_VALUE, libraryPendingMetadata);

        return MetadataProgressVO.builder()
                .libraryId(libraryId)
                .totalFiles(totalFiles)
                .metadataFetched(metadataFetched)
                .pendingMetadata(libraryPendingMetadata)
            // DB 驱动：queuePending 直接代表数据库中待处理量（库级或全局）
            .queuePending(queuePending)
                .activeThreads(metadataQueueManager.getActiveThreadCount())
                .maxPoolSize(metadataQueueManager.getMaxPoolSize())
                .totalSubmitted(submittedFromDb)
                .totalProcessed(processedFromDb)
                .failedTasks(metadataQueueManager.getTotalFailed())
                .build();
    }

    public MatchProgressVO getMatchProgress(Long libraryId) {
        long totalFiles = libraryId == null ? mediaFileRepository.count() : mediaFileRepository.countByLibraryId(libraryId);
        long matched = libraryId == null
                ? mediaFileRepository.countByMatchStatus(MatchStatus.MATCHED)
                : mediaFileRepository.countByLibraryIdAndMatchStatus(libraryId, MatchStatus.MATCHED);
        long noMatch = libraryId == null
                ? mediaFileRepository.countByMatchStatus(MatchStatus.NO_MATCH_FOUND)
                : mediaFileRepository.countByLibraryIdAndMatchStatus(libraryId, MatchStatus.NO_MATCH_FOUND);
        
        // 库级别的待处理匹配数 = 总文件数 - 已匹配的 - 无匹配的
        long libraryPendingMatch = Math.max(0, totalFiles - matched - noMatch);

        return MatchProgressVO.builder()
                .libraryId(libraryId)
                .totalFiles(totalFiles)
                .matched(matched)
                .noMatch(noMatch)
                .pendingMatch(libraryPendingMatch)
            // queuePending 表示真实队列长度（当前为全局匹配队列）
            .queuePending(mediaMatchQueueManager.getQueueSize())
                .activeBatches(mediaMatchQueueManager.getActiveBatches())
                .batchSize(mediaMatchQueueManager.getBatchSize())
                .queueIntervalSeconds(mediaMatchQueueManager.getQueueIntervalSeconds())
                .totalEnqueued(mediaMatchQueueManager.getTotalEnqueued())
                .totalProcessed(mediaMatchQueueManager.getTotalProcessed())
                .totalMatched(mediaMatchQueueManager.getTotalMatched())
                .totalNoMatch(mediaMatchQueueManager.getTotalNoMatch())
                .failedTasks(mediaMatchQueueManager.getTotalFailed())
                .build();
    }

    /**
     * 删除媒体文件记录
     * 
     * @param fileId 文件ID
     * @param deletePhysicalFile 是否同时删除硬盘上的文件
     */
    public void deleteMediaFile(Long fileId, boolean deletePhysicalFile) {
        mediaFileRepository.findById(fileId).ifPresentOrElse(
                mediaFile -> {
                    String filePath = mediaFile.getFilePath();

                    // 删除抽取的字幕文件和字幕记录
                    mediaSubtitleService.cleanupByMediaFileId(mediaFile.getId());
                    
                    // 删除数据库记录
                    mediaFileRepository.delete(mediaFile);
                    log.info("Deleted media file record: {}", filePath);
                    
                    // 可选：删除硬盘上的文件
                    if (deletePhysicalFile) {
                        try {
                            File file = new File(filePath);
                            if (file.exists()) {
                                if (file.delete()) {
                                    log.info("Deleted physical file: {}", filePath);
                                } else {
                                    log.warn("Failed to delete physical file: {}", filePath);
                                }
                            }
                        } catch (Exception e) {
                            log.error("Error deleting physical file: {}", filePath, e);
                        }
                    }
                },
                () -> log.warn("Media file not found with id: {}", fileId)
        );
    }

    /**
     * 更新媒体文件信息
     * 
     * @param fileId 文件ID
     * @param request 更新请求DTO
     */
    public void updateMediaFile(Long fileId, UpdateMediaFileRequest request) {
        mediaFileRepository.findById(fileId).ifPresentOrElse(
                mediaFile -> {
                    Long originalAnimeId = mediaFile.getAnimeId();
                    String originalEpisodeId = mediaFile.getEpisodeId();

                    if (request.getEpisodeId() != null) {
                        mediaFile.setEpisodeId(request.getEpisodeId());
                    }
                    if (request.getAnimeId() != null) {
                        mediaFile.setAnimeId(request.getAnimeId());
                    }
                    if (request.getAnimeTitle() != null) {
                        mediaFile.setAnimeTitle(request.getAnimeTitle());
                    }
                    if (request.getEpisodeTitle() != null) {
                        mediaFile.setEpisodeTitle(request.getEpisodeTitle());
                    }

                    // 手工更新后同步匹配状态，确保“弹幕匹配”展示与绑定关系一致。
                    boolean hasMatchedBinding = mediaFile.getAnimeId() != null
                            && mediaFile.getEpisodeId() != null
                            && !mediaFile.getEpisodeId().isBlank();
                    mediaFile.setMatchStatus(hasMatchedBinding ? MatchStatus.MATCHED : MatchStatus.UNMATCHED);

                    MediaFile saved = mediaFileRepository.save(mediaFile);

                    // 手工更新将文件绑定到新剧集时，也触发一次追番通知。
                    if (shouldNotifyAfterManualUpdate(saved, originalAnimeId, originalEpisodeId)) {
                        try {
                            episodeUpdateNotificationService.notifyFollowingUsersAsync(saved);
                        } catch (Exception e) {
                            log.warn("Failed to trigger notification for manual update fileId={}: {}", fileId, e.getMessage());
                        }
                    }

                    log.info("Updated media file: {}", fileId);
                },
                () -> log.warn("Media file not found with id: {}", fileId)
        );
    }

    private boolean shouldNotifyAfterManualUpdate(MediaFile saved, Long originalAnimeId, String originalEpisodeId) {
        if (saved == null || saved.getAnimeId() == null) {
            return false;
        }

        String currentEpisodeId = saved.getEpisodeId();
        if (currentEpisodeId == null || currentEpisodeId.isBlank()) {
            return false;
        }

        return !Objects.equals(saved.getAnimeId(), originalAnimeId)
                || !Objects.equals(currentEpisodeId, originalEpisodeId);
    }

    private List<MatchStatus> resolveMatchStatuses(Boolean matched) {
        if (matched == null) {
            return null;
        }
        if (matched) {
            return List.of(MatchStatus.MATCHED);
        }
        return Arrays.asList(MatchStatus.UNMATCHED, MatchStatus.NO_MATCH_FOUND);
    }

    private void ensureHash(MediaFile mediaFile) {
        if (mediaFile.getHash() != null && !mediaFile.getHash().isEmpty()) {
            return;
        }

        try {
            String hash = mediaHashService.calculateHash(Paths.get(mediaFile.getFilePath()));
            if (hash != null && !hash.isEmpty()) {
                mediaFile.setHash(hash);
            }
        } catch (Exception e) {
            log.warn("Failed to calculate hash for fileId={}, path={}", mediaFile.getId(), mediaFile.getFilePath(), e);
        }
    }

    /**
     * Entity to DTO 转换
     */
    private MediaFileDTO toDTO(MediaFile entity) {
        return MediaFileDTO.builder()
                .id(entity.getId())
                .libraryId(entity.getLibrary() != null ? entity.getLibrary().getId() : null)
                .fileName(entity.getFileName())
                .filePath(entity.getFilePath())
                .size(entity.getSize())
                .lastModified(entity.getLastModified())
                .episodeId(entity.getEpisodeId())
                .animeId(entity.getAnimeId())
                .animeTitle(entity.getAnimeTitle())
                .episodeTitle(entity.getEpisodeTitle())
                .duration(entity.getDuration())
                .hash(entity.getHash())
                .width(entity.getWidth())
                .height(entity.getHeight())
                .aspectRatio(entity.getAspectRatio())
                .colorDepth(entity.getColorDepth())
                .hdrType(entity.getHdrType())
                .colorSpace(entity.getColorSpace())
                .colorPrimaries(entity.getColorPrimaries())
                .videoBitrate(entity.getVideoBitrate())
                .fps(entity.getFps())
                .containerFormat(entity.getContainerFormat())
                .videoCodec(entity.getVideoCodec())
                .audioCodec(entity.getAudioCodec())
                .metadataFetched(entity.getMetadataFetched())
                .matchStatus(entity.getMatchStatus())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    /**
     * MediaFile 转 LibraryItemVO（用于 API v1）
     */
    public List<LibraryItemVO> getAllMediaLibraryItems() {
        List<MediaFile> files = mediaFileRepository.findAll();
        return files.stream()
                .map(this::toLibraryItemVO)
                .collect(Collectors.toList());
    }

    /**
     * 单个 MediaFile 转 LibraryItemVO
     */
    private LibraryItemVO toLibraryItemVO(MediaFile entity) {
        // 根据 animeId 和其他信息查询最后播放时间
        LocalDateTime lastPlayTime = null;
        if (entity.getAnimeId() != null) {
            try {
                // 这里我们查询与这个视频相关的最新播放记录
                // 由于 PlayHistory 关联的是用户和视频，这里暂时没有关联，所以为空
                // 实际上应该按视频ID关联，但现在的设计中没有这样的关联
                // 所以保留为空或者从数据库中按 animeId 查询最新的
                lastPlayTime = null;
            } catch (Exception e) {
                log.warn("Failed to get last play time: {}", e.getMessage());
            }
        }

        // 转换 episodeId 为 Long（从字符串）
        Long episodeId = null;
        if (entity.getEpisodeId() != null) {
            try {
                episodeId = Long.parseLong(entity.getEpisodeId());
            } catch (NumberFormatException e) {
                log.warn("Invalid episode ID: {}", entity.getEpisodeId());
            }
        }

        return LibraryItemVO.builder()
                // 直接转换 id 为字符串形式
                .id(entity.getId() != null ? entity.getId().toString() : null)
                .animeId(entity.getAnimeId())
                .episodeId(episodeId)
                .animeTitle(entity.getAnimeTitle())
                .episodeTitle(entity.getEpisodeTitle())
                .hash(entity.getHash())
                .name(entity.getFileName())
                .path(entity.getFilePath())
                .size(entity.getSize())
                .rate(0)  // 默认为 0
                .isStandalone(false)  // 默认为 false
                .created(entity.getCreatedAt() != null ? entity.getCreatedAt().toLocalDateTime() : null)
                .lastMatch(null)  // 匹配时间暂时设为 null
                .lastPlay(lastPlayTime)
                .lastThumbnail(null)  // 暂时设为 null
                .duration(entity.getDuration())
                .build();
    }
}

