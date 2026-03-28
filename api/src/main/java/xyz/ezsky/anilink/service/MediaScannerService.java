package xyz.ezsky.anilink.service;

import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import xyz.ezsky.anilink.model.entity.MediaFile;
import xyz.ezsky.anilink.model.entity.MediaLibrary;
import xyz.ezsky.anilink.model.entity.MatchStatus;
import xyz.ezsky.anilink.repository.MediaFileRepository;
import xyz.ezsky.anilink.repository.MediaLibraryRepository;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * 媒体库扫描与监控服务。
 *
 * <p>该服务负责：</p>
 * <ul>
 *   <li>扫描数据库中已注册的媒体库并索引视频文件（如 mp4/mkv/avi/mov）。</li>
 *   <li>在首次扫描后启动对媒体库目录的文件系统监听，处理新增、修改和删除事件。</li>
 *   <li>对文件修改采用延迟合并策略以避免短时间内的重复处理。</li>
 * </ul>
 *
 * 注意：实现依赖 Spring 管理的 {@code MediaLibraryRepository} 和 {@code MediaFileRepository}。
 */
@Log4j2
@Service
public class MediaScannerService {

    @Autowired
    private MediaLibraryRepository mediaLibraryRepository;

    @Autowired
    private MediaFileRepository mediaFileRepository;

    @Autowired
    private MediaMetadataQueueManager metadataQueueManager;

    @Autowired
    private MediaSubtitleService mediaSubtitleService;

    @Autowired
    private MediaMatchQueueManager mediaMatchQueueManager;

    private final ExecutorService executorService = Executors.newFixedThreadPool(4);
    private final ScheduledExecutorService scheduledExecutor = Executors.newSingleThreadScheduledExecutor();
    private final Map<Path, ScheduledFuture<?>> scheduledTasks = new ConcurrentHashMap<>();
    private final Map<Long, WatchService> watchServices = new ConcurrentHashMap<>();
    private final Map<Long, Map<WatchKey, Path>> watchKeyPaths = new ConcurrentHashMap<>();

    /**
     * 同一媒体库的扫描/增量处理必须串行，否则并行 scanLibrary（例如多个下载同时 triggerScan）
     * 会各自得到“尚未包含新文件”的 existingFilesMap 并重复 insert，触发 (library_id, file_path) 唯一约束。
     */
    private final ConcurrentHashMap<Long, Object> libraryScanLocks = new ConcurrentHashMap<>();

    private Object lockForLibrary(Long libraryId) {
        return libraryScanLocks.computeIfAbsent(libraryId, id -> new Object());
    }

    private String normalizePathKey(Path path) {
        return path.toAbsolutePath().normalize().toString();
    }

    private String normalizePathKey(String storedPath) {
        if (storedPath == null || storedPath.isBlank()) {
            return storedPath;
        }
        return Paths.get(storedPath).normalize().toAbsolutePath().toString();
    }

    /**
     * 扫描所有已注册的媒体库。
     *
     * <p>该方法会并发提交每个媒体库的扫描任务到内部线程池。</p>
     */
    public void scanAllLibraries() {
        List<MediaLibrary> libraries = mediaLibraryRepository.findAll();
        for (MediaLibrary library : libraries) {
            scanLibraryAsync(library);
        }
    }

    /**
     * 异步扫描指定媒体库。
     */
    public void scanLibraryAsync(MediaLibrary library) {
        executorService.submit(() -> scanLibrary(library));
    }

    /**
     * 扫描指定的媒体库目录，将发现的视频文件记录到数据库，并移除数据库中已删除的文件记录。
     *
     * @param library 要扫描的媒体库实体（包含路径信息）
     */
    public void scanLibrary(MediaLibrary library) {
        log.info("Scanning library: {}", library.getName());
        try {
            Path libraryPath = Paths.get(library.getPath());
            if (!Files.exists(libraryPath) || !Files.isDirectory(libraryPath)) {
                log.error("Library path does not exist or is not a directory: {}", library.getPath());
                // 标记媒体库为错误状态并保存（容错，继续扫描其他库）
                try {
                    library.setStatus(MediaLibrary.Status.ERROR);
                    mediaLibraryRepository.save(library);
                    log.info("Marked library as ERROR: {}", library.getName());
                } catch (Exception ex) {
                    log.error("Failed to mark library status ERROR for {}", library.getName(), ex);
                }
                return;
            }

            // 路径存在：确保状态为 OK
            try {
                if (library.getStatus() != MediaLibrary.Status.OK) {
                    library.setStatus(MediaLibrary.Status.OK);
                    mediaLibraryRepository.save(library);
                    log.info("Marked library as OK: {}", library.getName());
                }
            } catch (Exception ex) {
                log.warn("Failed to update library status to OK for {}", library.getName(), ex);
            }

            synchronized (lockForLibrary(library.getId())) {
                List<MediaFile> existingFiles = mediaFileRepository.findByLibraryId(library.getId());
                Map<String, MediaFile> existingFilesMap = new HashMap<>();
                for (MediaFile f : existingFiles) {
                    existingFilesMap.put(normalizePathKey(f.getFilePath()), f);
                }

                Files.walkFileTree(libraryPath, new SimpleFileVisitor<Path>() {
                    @Override
                    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                        if (isVideoFile(file)) {
                            processFile(library, file, attrs, existingFilesMap);
                        }
                        return FileVisitResult.CONTINUE;
                    }
                });

                // 删除数据库中已不存在的文件记录
                for (MediaFile mediaFile : existingFiles) {
                    if (!Files.exists(Paths.get(mediaFile.getFilePath()))) {
                        mediaSubtitleService.cleanupByMediaFileId(mediaFile.getId());
                        mediaFileRepository.delete(mediaFile);
                        log.info("Removed deleted file: {}", mediaFile.getFilePath());
                    }
                }

                // 扫描完成后启动对该库的监听
                startWatching(library);
            }

        } catch (IOException e) {
            log.error("Error scanning library: " + library.getName(), e);
        }
    }

    /**
     * 处理单个文件：如果已存在则比较并更新元数据，否则新增记录。
     * 
     * 基础信息（文件路径、大小、修改时间）直接保存。
     * 完整的技术元数据（分辨率、编码等）通过异步队列在后台处理。
     *
     * @param library          所属的媒体库实体
     * @param file             要处理的文件路径
     * @param attrs            文件属性（如大小、最后修改时间）
     * @param existingFilesMap 当前媒体库中已存在文件的映射（filePath -> MediaFile）
     */
    private void processFile(MediaLibrary library, Path file, BasicFileAttributes attrs, Map<String, MediaFile> existingFilesMap) {
        String filePath = normalizePathKey(file);
        MediaFile existingFile = existingFilesMap.get(filePath);

        if (existingFile != null) {
            if (existingFile.getLastModified() != attrs.lastModifiedTime().toMillis() || existingFile.getSize() != attrs.size()) {
                existingFile.setLastModified(attrs.lastModifiedTime().toMillis());
                existingFile.setSize(attrs.size());
                // 重置元数据提取标志，以便重新提取
                existingFile.setMetadataFetched(false);
                // 文件内容变化后，清空旧 hash/匹配结果，避免后续匹配误用历史数据。
                existingFile.setHash(null);
                existingFile.setMatchStatus(MatchStatus.UNMATCHED);
                existingFile.setEpisodeId(null);
                existingFile.setAnimeId(null);
                existingFile.setAnimeTitle(null);
                existingFile.setEpisodeTitle(null);
                mediaFileRepository.save(existingFile);
                log.info("Updated file: {}", filePath);

                // 文件记录更新后立即入匹配队列，避免等待 FFprobe 链路。
                mediaMatchQueueManager.addToQueue(existingFile.getId());
                
                // 仅触发后台处理器；具体任务由处理器按库内待处理文件分批拉取。
                metadataQueueManager.triggerProcessing();
            }
        } else {
            MediaFile newMediaFile = new MediaFile();
            newMediaFile.setLibrary(library);
            newMediaFile.setFilePath(filePath);
            newMediaFile.setFileName(file.getFileName().toString());
            newMediaFile.setLastModified(attrs.lastModifiedTime().toMillis());
            newMediaFile.setSize(attrs.size());
            newMediaFile.setMetadataFetched(false);  // 标记待提取元数据
            MediaFile savedFile = mediaFileRepository.save(newMediaFile);
            log.info("Added new file: {}", filePath);
            existingFilesMap.put(filePath, savedFile);

            // 新文件落库后立即入匹配队列，匹配所需字段由匹配队列自行补齐（hash）。
            mediaMatchQueueManager.addToQueue(savedFile.getId());
            
            // 仅触发后台处理器；具体任务由处理器按库内待处理文件分批拉取。
            metadataQueueManager.triggerProcessing();
        }
    }

    /**
     * 为指定的媒体库目录启动文件系统监听（CREATE/DELETE/MODIFY）。
     *
     * <p>监听到事件后会根据事件类型决定是立即删除数据库记录还是对文件进行延迟合并处理。</p>
     *
     * @param library 需要监控的媒体库实体
     */
    private void startWatching(MediaLibrary library) {
        try {
            // 避免重复监听造成资源泄漏
            stopWatching(library.getId());

            WatchService watchService = FileSystems.getDefault().newWatchService();
            Path path = Paths.get(library.getPath());
            Map<WatchKey, Path> keyMap = new ConcurrentHashMap<>();
            registerAllDirectories(path, watchService, keyMap);

            watchServices.put(library.getId(), watchService);
            watchKeyPaths.put(library.getId(), keyMap);

            executorService.submit(() -> {
                try {
                    WatchKey key;
                    while ((key = watchService.take()) != null) {
                        Path watchedPath = keyMap.get(key);
                        if (watchedPath == null) {
                            key.reset();
                            continue;
                        }

                        for (WatchEvent<?> event : key.pollEvents()) {
                            if (event.kind() == StandardWatchEventKinds.OVERFLOW) {
                                continue;
                            }

                            Path changedFile = (Path) event.context();
                            Path fullPath = watchedPath.resolve(changedFile);

                            if (event.kind() == StandardWatchEventKinds.ENTRY_CREATE && Files.isDirectory(fullPath)) {
                                registerAllDirectories(fullPath, watchService, keyMap);
                                continue;
                            }

                            if (event.kind() == StandardWatchEventKinds.ENTRY_DELETE) {
                                deleteFile(fullPath.toAbsolutePath().toString());
                            } else {
                                if (!Files.isDirectory(fullPath)) {
                                    scheduleFileProcessing(library, fullPath);
                                }
                            }
                        }

                        if (!key.reset()) {
                            keyMap.remove(key);
                            if (keyMap.isEmpty()) {
                                break;
                            }
                        }
                    }
                } catch (InterruptedException e) {
                    log.info("Watch service interrupted for library: {}", library.getName());
                } catch (ClosedWatchServiceException ignored) {
                    log.info("Watch service closed for library: {}", library.getName());
                } catch (IOException e) {
                    log.error("Error registering watched directory for library: {}", library.getName(), e);
                }
            });
            log.info("Started watching library: {}", library.getName());
        } catch (IOException e) {
            log.error("Could not start watch service for library: " + library.getName(), e);
        }
    }

    /**
     * 停止对指定媒体库的文件系统监听并释放资源。
     *
     * @param libraryId 媒体库在数据库中的 ID
     */
    public void stopWatching(Long libraryId) {
        WatchService watchService = watchServices.get(libraryId);
        if (watchService != null) {
            try {
                watchService.close();
                watchServices.remove(libraryId);
                watchKeyPaths.remove(libraryId);
                log.info("Stopped watching library with id: {}", libraryId);
            } catch (IOException e) {
                log.error("Error stopping watch service for library id: " + libraryId, e);
            }
        }
    }

    private void registerAllDirectories(Path root, WatchService watchService, Map<WatchKey, Path> keyMap) throws IOException {
        if (!Files.exists(root) || !Files.isDirectory(root)) {
            return;
        }

        Files.walkFileTree(root, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                WatchKey key = dir.register(
                        watchService,
                        StandardWatchEventKinds.ENTRY_CREATE,
                        StandardWatchEventKinds.ENTRY_DELETE,
                        StandardWatchEventKinds.ENTRY_MODIFY
                );
                keyMap.put(key, dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    /**
     * 简单的文件类型判断，仅识别常见的视频扩展名（不区分大小写）。
     *
     * @param file 待判断的文件路径
     * @return 如果是支持的视频文件返回 true，否则返回 false
     */
    private boolean isVideoFile(Path file) {
        String fileName = file.toString().toLowerCase();
        return fileName.endsWith(".mp4") || fileName.endsWith(".mkv") || fileName.endsWith(".avi") || fileName.endsWith(".mov");
    }

    /**
     * 对文件变更进行延迟处理：如果在延迟期间出现新的变更事件，会取消之前的计划并重新计时，
     * 以实现去抖（debounce）效果，避免重复处理短时间内的多个文件事件。
     * 
     * 延迟处理完成后，提交异步元数据提取任务。
     *
     * @param library 所属媒体库
     * @param file    要处理的文件路径
     */
    private void scheduleFileProcessing(MediaLibrary library, Path file) {
        if (scheduledTasks.containsKey(file)) {
            scheduledTasks.get(file).cancel(false);
        }

        ScheduledFuture<?> future = scheduledExecutor.schedule(() -> {
            try {
                if (Files.exists(file)) {
                    BasicFileAttributes attrs = Files.readAttributes(file, BasicFileAttributes.class);
                    if (isVideoFile(file)) {
                        synchronized (lockForLibrary(library.getId())) {
                            List<MediaFile> existingFiles = mediaFileRepository.findByLibraryId(library.getId());
                            Map<String, MediaFile> existingFilesMap = new HashMap<>();
                            for (MediaFile f : existingFiles) {
                                existingFilesMap.put(normalizePathKey(f.getFilePath()), f);
                            }
                            // 注意：无论是新文件还是修改的文件，匹配队列将在元数据提取完成后自动添加
                            processFile(library, file, attrs, existingFilesMap);
                        }
                    }
                }
            } catch (IOException e) {
                log.error("Error processing file: " + file, e);
            }
            scheduledTasks.remove(file);
        }, 5, TimeUnit.SECONDS); // 5秒延迟

        scheduledTasks.put(file, future);
    }

    /**
     * 删除数据库中指定路径的文件记录（若存在）。
     *
     * @param filePath 要删除的文件的绝对路径字符串
     */
    private void deleteFile(String filePath) {
        String normalized = normalizePathKey(filePath);
        Optional<MediaFile> found = mediaFileRepository.findByFilePath(filePath);
        if (found.isEmpty() && !normalized.equals(filePath)) {
            found = mediaFileRepository.findByFilePath(normalized);
        }
        found.ifPresent(mediaFile -> {
            Long libId = mediaFile.getLibrary().getId();
            synchronized (lockForLibrary(libId)) {
                mediaFileRepository.findById(mediaFile.getId()).ifPresent(fresh -> {
                    mediaSubtitleService.cleanupByMediaFileId(fresh.getId());
                    mediaFileRepository.delete(fresh);
                    log.info("Removed deleted file: {}", fresh.getFilePath());
                });
            }
        });
    }
}
