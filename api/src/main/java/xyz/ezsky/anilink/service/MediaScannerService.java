package xyz.ezsky.anilink.service;

import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import xyz.ezsky.anilink.model.entity.MediaFile;
import xyz.ezsky.anilink.model.entity.MediaLibrary;
import xyz.ezsky.anilink.repository.MediaFileRepository;
import xyz.ezsky.anilink.repository.MediaLibraryRepository;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;

@Log4j2
@Service
public class MediaScannerService {

    @Autowired
    private MediaLibraryRepository mediaLibraryRepository;

    @Autowired
    private MediaFileRepository mediaFileRepository;

    private final ExecutorService executorService = Executors.newFixedThreadPool(4);
    private final ScheduledExecutorService scheduledExecutor = Executors.newSingleThreadScheduledExecutor();
    private final Map<Path, ScheduledFuture<?>> scheduledTasks = new ConcurrentHashMap<>();
    private final Map<Long, WatchService> watchServices = new ConcurrentHashMap<>();

    public void scanAllLibraries() {
        List<MediaLibrary> libraries = mediaLibraryRepository.findAll();
        for (MediaLibrary library : libraries) {
            executorService.submit(() -> scanLibrary(library));
        }
    }

    public void scanLibrary(MediaLibrary library) {
        log.info("Scanning library: {}", library.getName());
        try {
            Path libraryPath = Paths.get(library.getPath());
            if (!Files.exists(libraryPath) || !Files.isDirectory(libraryPath)) {
                log.error("Library path does not exist or is not a directory: {}", library.getPath());
                return;
            }

            List<MediaFile> existingFiles = mediaFileRepository.findByLibraryId(library.getId());
            Map<String, MediaFile> existingFilesMap = existingFiles.stream()
                    .collect(Collectors.toMap(MediaFile::getFilePath, Function.identity()));

            Files.walkFileTree(libraryPath, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    if (isVideoFile(file)) {
                        processFile(library, file, attrs, existingFilesMap);
                    }
                    return FileVisitResult.CONTINUE;
                }
            });

            // Remove files that no longer exist
            for (MediaFile mediaFile : existingFiles) {
                if (!Files.exists(Paths.get(mediaFile.getFilePath()))) {
                    mediaFileRepository.delete(mediaFile);
                    log.info("Removed deleted file: {}", mediaFile.getFilePath());
                }
            }

            startWatching(library);
        } catch (IOException e) {
            log.error("Error scanning library: " + library.getName(), e);
        }
    }

    private void processFile(MediaLibrary library, Path file, BasicFileAttributes attrs, Map<String, MediaFile> existingFilesMap) {
        String filePath = file.toAbsolutePath().toString();
        MediaFile existingFile = existingFilesMap.get(filePath);

        if (existingFile != null) {
            if (existingFile.getLastModified() != attrs.lastModifiedTime().toMillis() || existingFile.getSize() != attrs.size()) {
                existingFile.setLastModified(attrs.lastModifiedTime().toMillis());
                existingFile.setSize(attrs.size());
                mediaFileRepository.save(existingFile);
                log.info("Updated file: {}", filePath);
            }
        } else {
            MediaFile newMediaFile = new MediaFile();
            newMediaFile.setLibrary(library);
            newMediaFile.setFilePath(filePath);
            newMediaFile.setFileName(file.getFileName().toString());
            newMediaFile.setLastModified(attrs.lastModifiedTime().toMillis());
            newMediaFile.setSize(attrs.size());
            mediaFileRepository.save(newMediaFile);
            log.info("Added new file: {}", filePath);
        }
    }

    private void startWatching(MediaLibrary library) {
        try {
            WatchService watchService = FileSystems.getDefault().newWatchService();
            Path path = Paths.get(library.getPath());
            path.register(watchService, StandardWatchEventKinds.ENTRY_CREATE, StandardWatchEventKinds.ENTRY_DELETE, StandardWatchEventKinds.ENTRY_MODIFY);
            watchServices.put(library.getId(), watchService);

            executorService.submit(() -> {
                try {
                    WatchKey key;
                    while ((key = watchService.take()) != null) {
                        for (WatchEvent<?> event : key.pollEvents()) {
                            Path changedFile = (Path) event.context();
                            Path fullPath = path.resolve(changedFile);
                            if (event.kind() == StandardWatchEventKinds.ENTRY_DELETE) {
                                deleteFile(fullPath.toAbsolutePath().toString());
                            } else {
                                scheduleFileProcessing(library, fullPath);
                            }
                        }
                        key.reset();
                    }
                } catch (InterruptedException e) {
                    log.info("Watch service interrupted for library: {}", library.getName());
                }
            });
            log.info("Started watching library: {}", library.getName());
        } catch (IOException e) {
            log.error("Could not start watch service for library: " + library.getName(), e);
        }
    }

    public void stopWatching(Long libraryId) {
        WatchService watchService = watchServices.get(libraryId);
        if (watchService != null) {
            try {
                watchService.close();
                watchServices.remove(libraryId);
                log.info("Stopped watching library with id: {}", libraryId);
            } catch (IOException e) {
                log.error("Error stopping watch service for library id: " + libraryId, e);
            }
        }
    }

    private boolean isVideoFile(Path file) {
        String fileName = file.toString().toLowerCase();
        return fileName.endsWith(".mp4") || fileName.endsWith(".mkv") || fileName.endsWith(".avi") || fileName.endsWith(".mov");
    }

    private void scheduleFileProcessing(MediaLibrary library, Path file) {
        if (scheduledTasks.containsKey(file)) {
            scheduledTasks.get(file).cancel(false);
        }

        ScheduledFuture<?> future = scheduledExecutor.schedule(() -> {
            try {
                if (Files.exists(file)) {
                    BasicFileAttributes attrs = Files.readAttributes(file, BasicFileAttributes.class);
                    if (isVideoFile(file)) {
                        List<MediaFile> existingFiles = mediaFileRepository.findByLibraryId(library.getId());
                        Map<String, MediaFile> existingFilesMap = existingFiles.stream()
                                .collect(Collectors.toMap(MediaFile::getFilePath, Function.identity()));
                        processFile(library, file, attrs, existingFilesMap);
                    }
                }
            } catch (IOException e) {
                log.error("Error processing file: " + file, e);
            }
            scheduledTasks.remove(file);
        }, 5, TimeUnit.SECONDS); // 5秒延迟

        scheduledTasks.put(file, future);
    }

    private void deleteFile(String filePath) {
        mediaFileRepository.findByFilePath(filePath).ifPresent(mediaFile -> {
            mediaFileRepository.delete(mediaFile);
            log.info("Removed deleted file: {}", filePath);
        });
    }
}
