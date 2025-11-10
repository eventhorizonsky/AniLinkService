package xyz.ezsky.anilink.service;

import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import xyz.ezsky.anilink.model.dto.MediaLibraryDTO;
import xyz.ezsky.anilink.model.entity.MediaLibrary;
import xyz.ezsky.anilink.model.vo.MediaLibraryVO;
import xyz.ezsky.anilink.repository.MediaLibraryRepository;

import java.util.List;
import java.util.stream.Collectors;
import xyz.ezsky.anilink.model.vo.PathVO;

/**
 * 媒体库管理服务。
 *
 * <p>提供媒体库的增删查以及触发扫描的功能。该服务与 {@link MediaScannerService} 协作，
 * 在新增媒体库后触发扫描，或对单个媒体库发起扫描操作。</p>
 */
@Service
public class MediaLibraryService {

    @Autowired
    private MediaLibraryRepository mediaLibraryRepository;

    @Autowired
    private MediaScannerService mediaScannerService;

    /**
     * 添加一个新的媒体库并在后台触发一次扫描以索引该库中的媒体文件。
     *
     * @param mediaLibraryDTO 用于创建媒体库的数据传输对象
     * @return 创建后的媒体库视图对象（包含数据库分配的 ID）
     */
    public MediaLibraryVO addLibrary(MediaLibraryDTO mediaLibraryDTO) {
        MediaLibrary mediaLibrary = new MediaLibrary();
        BeanUtils.copyProperties(mediaLibraryDTO, mediaLibrary);
        MediaLibrary savedLibrary = mediaLibraryRepository.save(mediaLibrary);
        // 添加后立即在后台触发一次扫描以索引新库中的文件
        mediaScannerService.scanLibrary(savedLibrary);
        MediaLibraryVO mediaLibraryVO = new MediaLibraryVO();
        BeanUtils.copyProperties(savedLibrary, mediaLibraryVO);
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
            return mediaLibraryVO;
        }).collect(Collectors.toList());
    }

    /**
     * 删除指定 ID 的媒体库并停止对该库的文件系统监控（如有）。
     *
     * @param id 要删除的媒体库的数据库 ID
     */
    public void deleteLibrary(Long id) {
        mediaScannerService.stopWatching(id);
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
        mediaLibraryRepository.findById(id).ifPresent(mediaScannerService::scanLibrary);
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
}
