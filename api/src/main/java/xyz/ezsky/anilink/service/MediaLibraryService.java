package xyz.ezsky.anilink.service;

import lombok.extern.log4j.Log4j2;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import xyz.ezsky.anilink.model.dto.MediaLibraryDTO;
import xyz.ezsky.anilink.model.entity.MediaFile;
import xyz.ezsky.anilink.model.entity.MediaLibrary;
import xyz.ezsky.anilink.model.vo.MediaLibraryVO;
import xyz.ezsky.anilink.repository.AnimeRepository;
import xyz.ezsky.anilink.repository.MediaFileRepository;
import xyz.ezsky.anilink.repository.MediaLibraryRepository;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import xyz.ezsky.anilink.model.vo.PathVO;

/**
 * 媒体库管理服务。
 *
 * <p>提供媒体库的增删查以及触发扫描的功能。该服务与 {@link MediaScannerService} 协作，
 * 在新增媒体库后触发扫描，或对单个媒体库发起扫描操作。</p>
 */
@Log4j2
@Service
public class MediaLibraryService {

    @Autowired
    private MediaLibraryRepository mediaLibraryRepository;

    @Autowired
    private MediaScannerService mediaScannerService;

    @Autowired
    private MediaSubtitleService mediaSubtitleService;

    @Autowired
    private MediaFileRepository mediaFileRepository;

    @Autowired
    private AnimeRepository animeRepository;

    @Autowired
    private MediaMatchQueueManager mediaMatchQueueManager;

    /**
     * 添加一个新的媒体库并在后台触发一次扫描以索引该库中的媒体文件。
     *
     * @param mediaLibraryDTO 用于创建媒体库的数据传输对象
     * @return 创建后的媒体库视图对象（包含数据库分配的 ID）
     */
    public MediaLibraryVO addLibrary(MediaLibraryDTO mediaLibraryDTO) {
        return addLibrary(mediaLibraryDTO, true);
    }

    /**
     * 添加一个新的媒体库，并根据参数决定是否在后台触发一次扫描。
     *
     * @param mediaLibraryDTO 用于创建媒体库的数据传输对象
     * @param autoScan 是否在添加后自动触发扫描
     * @return 创建后的媒体库视图对象（包含数据库分配的 ID）
     */
    public MediaLibraryVO addLibrary(MediaLibraryDTO mediaLibraryDTO, boolean autoScan) {
        MediaLibrary mediaLibrary = new MediaLibrary();
        BeanUtils.copyProperties(mediaLibraryDTO, mediaLibrary);
        MediaLibrary savedLibrary = mediaLibraryRepository.save(mediaLibrary);
        // 只在自动扫描标记为 true 时才触发扫描
        if (autoScan) {
            mediaScannerService.scanLibraryAsync(savedLibrary);
        }
        MediaLibraryVO mediaLibraryVO = new MediaLibraryVO();
        BeanUtils.copyProperties(savedLibrary, mediaLibraryVO);
        // 将 Long ID 转换为 String
        mediaLibraryVO.setId(savedLibrary.getId().toString());
        return mediaLibraryVO;
    }

    /**
     * 查询并返回系统中所有已配置的媒体库。
     *
     * @return 媒体库视图对象列表
     */
    public List<MediaLibraryVO> findAll() {
        return mediaLibraryRepository.findAll().stream().map(mediaLibrary -> {
            MediaLibraryVO mediaLibraryVO = new MediaLibraryVO();
            BeanUtils.copyProperties(mediaLibrary, mediaLibraryVO);
            // 将 Long ID 转换为 String
            mediaLibraryVO.setId(mediaLibrary.getId().toString());
            return mediaLibraryVO;
        }).collect(Collectors.toList());
    }

    /**
     * 删除指定 ID 的媒体库并停止对该库的文件系统监控（如有）。
     *
     * @param id 要删除的媒体库的数据库 ID
     */
    @Transactional
    public void deleteLibrary(Long id) {
        mediaScannerService.stopWatching(id);

        // 先记录该媒体库关联的 animeId，后续用于清理无剧集的 anime 记录
        List<MediaFile> libraryFiles = mediaFileRepository.findByLibraryId(id);
        Set<Long> affectedAnimeIds = libraryFiles.stream()
                .map(MediaFile::getAnimeId)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toCollection(HashSet::new));

        // 允许删除字幕物理文件，但不删除视频物理文件
        for (MediaFile mediaFile : libraryFiles) {
            mediaSubtitleService.cleanupByMediaFileId(mediaFile.getId());
        }

        // 删除媒体文件数据库记录
        mediaFileRepository.deleteByLibraryId(id);

        // 删除已无任何剧集文件关联的 anime 记录
        Set<Long> orphanAnimeIds = affectedAnimeIds.stream()
                .filter(animeId -> mediaFileRepository.countByAnimeId(animeId) == 0)
                .collect(Collectors.toSet());
        if (!orphanAnimeIds.isEmpty()) {
            animeRepository.deleteByAnimeIdIn(orphanAnimeIds);
        }

        mediaLibraryRepository.deleteById(id);
    }

    /**
     * 对指定 ID 的媒体库触发一次扫描操作。
     *
     * <p>如果指定 ID 的媒体库存在，则将扫描任务委托给 {@link MediaScannerService}。
     * 该调用为异步（由 {@code MediaScannerService} 在后台线程执行），不会阻塞调用者。</p>
     *
     * @param id 媒体库的数据库 ID
     */
    public void scanLibrary(Long id) {
        mediaLibraryRepository.findById(id).ifPresent(mediaScannerService::scanLibraryAsync);
    }

    /**
     * 手动触发一次对所有媒体库的扫描操作。
     *
     * <p>扫描任务为异步提交，方法返回后不会等待扫描完成。</p>
     */
    public void scanAllLibraries() {
        mediaScannerService.scanAllLibraries();
    }


    /**
     * 查询指定目录下的所有文件和文件夹路径，带类型。
     *
     * @param rootPath 根目录路径
     * @return 路径及类型列表
     */
    public List<PathVO> listServerPaths(String rootPath) {
        java.io.File root = new java.io.File(rootPath);
        if (!root.exists() || !root.isDirectory()) {
            return java.util.Collections.emptyList();
        }
        java.io.File[] files = root.listFiles();
        if (files == null) {
            return java.util.Collections.emptyList();
        }
        return java.util.Arrays.stream(files)
                .map(f -> new PathVO(f.getName(), f.getAbsolutePath(), f.isDirectory() ? "directory" : "file"))
                .collect(java.util.stream.Collectors.toList());
    }

    /**
     * 查询指定目录下的文件或文件夹路径，支持仅返回文件夹或按文件类型过滤。
     *
     * @param rootPath 根目录路径
     * @param onlyDir  如果为 true，仅返回目录；否则同时返回文件和目录
     * @param fileType 如果不为 null，则按扩展名过滤（如 "mp4"），不区分大小写
     * @return 符合条件的路径及类型列表
     */
    public List<PathVO> listServerPaths(String rootPath, boolean onlyDir, String fileType) {
        java.io.File root = new java.io.File(rootPath);
        if (!root.exists() || !root.isDirectory()) {
            return java.util.Collections.emptyList();
        }
        java.io.File[] files = root.listFiles();
        if (files == null) {
            return java.util.Collections.emptyList();
        }
        return java.util.Arrays.stream(files)
                .filter(f -> {
                    if (onlyDir) return f.isDirectory();
                    if (fileType != null && !fileType.isEmpty()) {
                        return f.isDirectory() || (f.isFile() && f.getName().toLowerCase().endsWith("." + fileType.toLowerCase()));
                    }
                    return true;
                })
                .map(f -> new PathVO(f.getName(), f.getAbsolutePath(), f.isDirectory() ? "directory" : "file"))
                .collect(java.util.stream.Collectors.toList());
    }

    // ==================== 定时重新匹配 ====================

    /**
     * 每4小时自动触发所有媒体库的重新匹配，将可匹配文件加入弹弹匹配队列。
     * 在整点过 3 分触发，避免与其他整点任务碰撞。
     */
    @Scheduled(cron = "0 3 */4 * * *")
    public void scheduledRematchAllLibraries() {
        try {
            List<MediaLibrary> libraries = mediaLibraryRepository.findAll();
            log.info("定时重新匹配任务开始，共 {} 个媒体库", libraries.size());
            int totalEnqueued = 0;
            for (MediaLibrary library : libraries) {
                int enqueued = mediaMatchQueueManager.enqueueLibraryForRematch(library.getId());
                if (enqueued > 0) {
                    log.info("媒体库 [{}] 重新匹配: {} 个文件加入队列", library.getName(), enqueued);
                }
                totalEnqueued += enqueued;
            }
            log.info("定时重新匹配任务完成，共加入队列 {} 个文件", totalEnqueued);
        } catch (Exception e) {
            log.error("定时重新匹配任务失败", e);
        }
    }
}
