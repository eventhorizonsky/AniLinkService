package xyz.ezsky.anilink.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import xyz.ezsky.anilink.model.dto.MediaSubtitleAdminDTO;
import xyz.ezsky.anilink.model.dto.MediaSubtitleDTO;
import xyz.ezsky.anilink.model.entity.MediaFile;
import xyz.ezsky.anilink.model.entity.MediaSubtitle;
import xyz.ezsky.anilink.model.vo.PageVO;
import xyz.ezsky.anilink.repository.MediaSubtitleRepository;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;
import java.util.stream.Collectors;

@Log4j2
@Service
public class MediaSubtitleService {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final Set<String> KNOWN_SUBTITLE_SUFFIXES = Set.of(
            "s", "sign", "signs",
            "sc", "tc", "gb", "big5",
            "zh", "zhs", "zht", "chs", "cht", "chi",
            "jp", "jpn", "ja",
            "en", "eng",
            "ko", "kor"
    );

    @Autowired
    private MediaSubtitleRepository mediaSubtitleRepository;

    @Value("${media.subtitle.output-dir:./data/subtitles}")
    private String subtitleOutputDir;

    public PageVO<MediaSubtitleAdminDTO> getSubtitlePage(int page, int pageSize, String keyword, String sourceType) {
        Pageable pageable = PageRequest.of(page, pageSize, Sort.by(Sort.Direction.DESC, "id"));
        String normalizedKeyword = keyword == null ? "" : keyword.trim();

        String normalizedSourceType = sourceType == null ? null : sourceType.trim();
        if (normalizedSourceType != null && normalizedSourceType.isEmpty()) {
            normalizedSourceType = null;
        }

        Page<MediaSubtitle> result = mediaSubtitleRepository.searchSubtitles(normalizedKeyword, normalizedSourceType, pageable);
        return PageVO.<MediaSubtitleAdminDTO>builder()
                .content(result.getContent().stream().map(this::toAdminDTO).collect(Collectors.toList()))
                .totalElements(result.getTotalElements())
                .totalPages(result.getTotalPages())
                .currentPage(result.getNumber())
                .pageSize(result.getSize())
                .hasNext(result.hasNext())
                .hasPrevious(result.hasPrevious())
                .build();
    }

    public List<MediaSubtitleDTO> listByMediaFileId(Long mediaFileId) {
        return mediaSubtitleRepository.findByMediaFileIdOrderByStreamIndexAsc(mediaFileId)
                .stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    public void extractSubtitlesIfMkv(MediaFile mediaFile) {
        if (mediaFile == null || mediaFile.getId() == null || mediaFile.getFilePath() == null) {
            return;
        }

        Path sourcePath = Paths.get(mediaFile.getFilePath());
        if (!Files.exists(sourcePath)) {
            log.warn("Skip subtitle extraction because source file does not exist: {}", sourcePath);
            cleanupByMediaFileId(mediaFile.getId());
            return;
        }

        String lowerName = sourcePath.getFileName().toString().toLowerCase(Locale.ROOT);
        if (!lowerName.endsWith(".mkv")) {
            cleanupByMediaFileId(mediaFile.getId());
            return;
        }

        cleanupByMediaFileId(mediaFile.getId());

        List<SubtitleStreamInfo> subtitleStreams = probeSubtitleStreams(sourcePath);
        if (subtitleStreams.isEmpty()) {
            log.debug("No subtitle stream found in MKV file: {}", sourcePath);
            return;
        }

        Path outputDir = Paths.get(subtitleOutputDir);
        try {
            Files.createDirectories(outputDir);
        } catch (IOException e) {
            log.error("Failed to create subtitle output directory: {}", outputDir, e);
            return;
        }

        for (SubtitleStreamInfo streamInfo : subtitleStreams) {
            String safeTrackName = sanitizeTrackName(streamInfo.trackName());
            String fileExt = resolveSubtitleExtension(streamInfo.codecName());
            String fileName = mediaFile.getId() + "_" + safeTrackName + "_" + streamInfo.streamIndex() + "." + fileExt;
            Path outputPath = outputDir.resolve(fileName);

            if (!extractSubtitleStream(sourcePath, streamInfo.streamIndex(), outputPath)) {
                continue;
            }

            try {
                MediaSubtitle subtitle = mediaSubtitleRepository
                        .findByMediaFileIdAndStreamIndex(mediaFile.getId(), streamInfo.streamIndex())
                        .orElseGet(MediaSubtitle::new);

                subtitle.setMediaFile(mediaFile);
                subtitle.setStreamIndex(streamInfo.streamIndex());
                subtitle.setTrackName(streamInfo.trackName());
                subtitle.setLanguage(streamInfo.language());
                subtitle.setCodecName(streamInfo.codecName());
                subtitle.setSubtitleFormat(fileExt);
                subtitle.setFileName(fileName);
                subtitle.setFilePath(outputPath.toAbsolutePath().toString());
                subtitle.setFileSize(Files.size(outputPath));
                mediaSubtitleRepository.save(subtitle);
            } catch (DataIntegrityViolationException e) {
                // 并发场景下可能已有同 streamIndex 记录被其他线程写入，回退为更新确保幂等。
                mediaSubtitleRepository.findByMediaFileIdAndStreamIndex(mediaFile.getId(), streamInfo.streamIndex())
                        .ifPresent(existing -> {
                            existing.setTrackName(streamInfo.trackName());
                            existing.setLanguage(streamInfo.language());
                            existing.setCodecName(streamInfo.codecName());
                            existing.setSubtitleFormat(fileExt);
                            existing.setFileName(fileName);
                            existing.setFilePath(outputPath.toAbsolutePath().toString());
                            try {
                                existing.setFileSize(Files.size(outputPath));
                            } catch (IOException ioException) {
                                log.warn("Failed to read subtitle file size for {}", outputPath, ioException);
                            }
                            mediaSubtitleRepository.save(existing);
                        });
            } catch (Exception e) {
                log.error("Failed to persist subtitle metadata for stream {} in file {}",
                        streamInfo.streamIndex(), sourcePath, e);
            }
        }
    }

    public void cleanupByMediaFileId(Long mediaFileId) {
        List<MediaSubtitle> subtitles = mediaSubtitleRepository.findByMediaFileIdOrderByStreamIndexAsc(mediaFileId);
        for (MediaSubtitle subtitle : subtitles) {
            if (subtitle.getFilePath() == null || subtitle.getFilePath().isEmpty()) {
                continue;
            }
            // 只删除内嵌字幕提取出来的文件，外部字幕不删除
            if (subtitle.getIsExternal() != null && !subtitle.getIsExternal()) {
                try {
                    Files.deleteIfExists(Paths.get(subtitle.getFilePath()));
                } catch (Exception e) {
                    log.warn("Failed to delete subtitle file: {}", subtitle.getFilePath(), e);
                }
            }
        }
        mediaSubtitleRepository.deleteByMediaFileId(mediaFileId);
    }

    /**
     * 扫描视频文件同目录下的外部字幕文件
     */
    public void scanExternalSubtitles(MediaFile mediaFile) {
        if (mediaFile == null || mediaFile.getFilePath() == null) {
            return;
        }

        Path videoPath = Paths.get(mediaFile.getFilePath());
        if (!Files.exists(videoPath)) {
            log.warn("Video file does not exist: {}", videoPath);
            return;
        }

        Path parentDir = videoPath.getParent();
        if (parentDir == null) {
            return;
        }

        String videoNameWithoutExt = getFileNameWithoutExtension(videoPath.getFileName().toString());

        try (Stream<Path> fileStream = Files.list(parentDir)) {
            List<Path> subtitleFiles = fileStream
                    .filter(Files::isRegularFile)
                    .filter(path -> isSupportedSubtitleFile(path.getFileName().toString()))
                    .filter(path -> isMatchingSubtitleFile(videoNameWithoutExt, path.getFileName().toString()))
                    .collect(Collectors.toList());

            for (Path subtitleFile : subtitleFiles) {
                addExternalSubtitle(mediaFile, subtitleFile);
            }
        } catch (IOException e) {
            log.error("Failed to scan subtitles in {}", parentDir, e);
        }
    }

    /**
     * 添加外部字幕文件到数据库
     */
    private void addExternalSubtitle(MediaFile mediaFile, Path subtitlePath) {
        try {
            String absolutePath = subtitlePath.toAbsolutePath().toString();
            
            // 检查是否已经存在
            List<MediaSubtitle> existing = mediaSubtitleRepository.findByMediaFileIdOrderByStreamIndexAsc(mediaFile.getId());
            for (MediaSubtitle sub : existing) {
                if (absolutePath.equals(sub.getFilePath())) {
                    // 已存在，更新文件大小等信息
                    sub.setFileSize(Files.size(subtitlePath));
                    mediaSubtitleRepository.save(sub);
                    return;
                }
            }

            // 提取语言信息和轨道名称
            String fileName = subtitlePath.getFileName().toString();
            String nameWithoutExt = getFileNameWithoutExtension(fileName);
            String videoName = getFileNameWithoutExtension(Paths.get(mediaFile.getFilePath()).getFileName().toString());
            
            String language = extractLanguageFromFileName(nameWithoutExt, videoName);
            String trackName = language != null ? language : "External";
            String format = getFileExtension(fileName);

            MediaSubtitle subtitle = new MediaSubtitle();
            subtitle.setMediaFile(mediaFile);
            subtitle.setStreamIndex(null); // 外部字幕没有stream index
            subtitle.setTrackName(trackName);
            subtitle.setLanguage(language);
            subtitle.setCodecName(null); // 外部字幕没有codec name
            subtitle.setSubtitleFormat(format);
            subtitle.setFileName(fileName);
            subtitle.setFilePath(absolutePath);
            subtitle.setFileSize(Files.size(subtitlePath));
            subtitle.setIsExternal(true);
            subtitle.setSourceType("EXTERNAL");
            subtitle.setTimeOffset(0L);

            mediaSubtitleRepository.save(subtitle);
            log.info("Added external subtitle: {} for media file: {}", fileName, mediaFile.getFileName());
        } catch (Exception e) {
            log.error("Failed to add external subtitle: {}", subtitlePath, e);
        }
    }

    /**
     * 从文件名中提取语言代码
     */
    private String extractLanguageFromFileName(String fileNameWithoutExt, String videoNameWithoutExt) {
        // 移除视频文件名部分
        String remaining = fileNameWithoutExt.toLowerCase()
                .replaceFirst("^" + videoNameWithoutExt.toLowerCase(), "")
                .replaceFirst("^[\\s._-]+", "");
        
        if (remaining.isEmpty()) {
            return null;
        }

        // 常见语言代码映射
        String[] parts = remaining.split("[\\s._-]+");
        if (parts.length > 0) {
            String lang = parts[0];
            return switch (lang) {
                case "zh", "chs", "chi", "sc", "简", "简中" -> "zh_CN";
                case "cht", "tc", "繁", "繁中" -> "zh_TW";
                case "en", "eng" -> "en";
                case "ja", "jp", "jpn" -> "ja";
                case "ko", "kor" -> "ko";
                case "s", "sign", "signs" -> null;
                default -> lang;
            };
        }
        
        return null;
    }

    private boolean isSupportedSubtitleFile(String fileName) {
        String extension = getFileExtension(fileName);
        return switch (extension) {
            case "srt", "ass", "ssa", "vtt", "sub", "sbv" -> true;
            default -> false;
        };
    }

    private boolean isMatchingSubtitleFile(String videoNameWithoutExt, String subtitleFileName) {
        String videoBase = videoNameWithoutExt.toLowerCase(Locale.ROOT);
        String subtitleBase = getFileNameWithoutExtension(subtitleFileName).toLowerCase(Locale.ROOT);

        if (subtitleBase.equals(videoBase)) {
            return true;
        }
        if (!subtitleBase.startsWith(videoBase)) {
            return false;
        }

        String suffix = subtitleBase.substring(videoBase.length());
        if (suffix.isEmpty()) {
            return true;
        }
        if (suffix.startsWith(".") || suffix.startsWith("_") || suffix.startsWith("-")
                || suffix.startsWith(" ") || suffix.startsWith("[") || suffix.startsWith("(")) {
            return true;
        }

        String normalizedSuffix = suffix.replaceFirst("^[\\s._-]+", "");
        return KNOWN_SUBTITLE_SUFFIXES.contains(normalizedSuffix);
    }

    private String getFileNameWithoutExtension(String fileName) {
        int lastDotIndex = fileName.lastIndexOf('.');
        return lastDotIndex > 0 ? fileName.substring(0, lastDotIndex) : fileName;
    }

    private String getFileExtension(String fileName) {
        int lastDotIndex = fileName.lastIndexOf('.');
        return lastDotIndex > 0 ? fileName.substring(lastDotIndex + 1).toLowerCase() : "";
    }

    private List<SubtitleStreamInfo> probeSubtitleStreams(Path sourcePath) {
        ProcessBuilder pb = new ProcessBuilder(
                "ffprobe",
                "-v", "quiet",
                "-print_format", "json",
                "-show_streams",
                sourcePath.toAbsolutePath().toString()
        );
        pb.redirectErrorStream(true);

        try {
            Process process = pb.start();
            String output = readProcessOutput(process);
            int exitCode = process.waitFor();
            if (exitCode != 0 || output.isEmpty()) {
                log.warn("FFprobe failed to read subtitle streams from file: {} (exitCode={})", sourcePath, exitCode);
                return List.of();
            }

            JsonNode root = OBJECT_MAPPER.readTree(output);
            JsonNode streamsNode = root.get("streams");
            if (streamsNode == null || !streamsNode.isArray()) {
                return List.of();
            }

            List<SubtitleStreamInfo> result = new ArrayList<>();
            for (JsonNode stream : streamsNode) {
                String codecType = stream.path("codec_type").asText("");
                if (!Objects.equals(codecType, "subtitle")) {
                    continue;
                }

                int streamIndex = stream.path("index").asInt(-1);
                if (streamIndex < 0) {
                    continue;
                }

                String codecName = stream.path("codec_name").asText("subrip");
                JsonNode tagsNode = stream.path("tags");
                String language = tagsNode.path("language").asText("");
                if (language.isBlank()) {
                    language = null;
                }

                String title = tagsNode.path("title").asText("");
                String trackName = title.isBlank() ? null : title;
                if (trackName == null) {
                    trackName = language != null ? language : "track";
                }

                result.add(new SubtitleStreamInfo(streamIndex, trackName, language, codecName));
            }
            return result;
        } catch (Exception e) {
            log.error("Failed to probe subtitle streams for file: {}", sourcePath, e);
            return List.of();
        }
    }

    private boolean extractSubtitleStream(Path sourcePath, int streamIndex, Path outputPath) {
        ProcessBuilder pb = new ProcessBuilder(
                "ffmpeg",
                "-y",
                "-i", sourcePath.toAbsolutePath().toString(),
                "-map", "0:" + streamIndex,
                "-c:s", "copy",
                outputPath.toAbsolutePath().toString()
        );
        pb.redirectErrorStream(true);

        try {
            Process process = pb.start();
            String output = readProcessOutput(process);
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                log.warn("FFmpeg failed to extract subtitle stream {} from {} (exitCode={}): {}",
                        streamIndex, sourcePath, exitCode, output);
                return false;
            }
            return true;
        } catch (Exception e) {
            log.error("Failed to extract subtitle stream {} from {}", streamIndex, sourcePath, e);
            return false;
        }
    }

    private String readProcessOutput(Process process) throws IOException {
        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append('\n');
            }
        }
        return output.toString();
    }

    private String sanitizeTrackName(String trackName) {
        String normalized = (trackName == null || trackName.isBlank()) ? "track" : trackName;
        String sanitized = normalized.replaceAll("[\\\\/:*?\"<>|]", "_")
                .replaceAll("\\s+", "_")
                .replaceAll("_+", "_")
                .trim();

        if (sanitized.isBlank()) {
            return "track";
        }
        if (sanitized.length() > 80) {
            return sanitized.substring(0, 80);
        }
        return sanitized;
    }

    private String resolveSubtitleExtension(String codecName) {
        String codec = codecName == null ? "" : codecName.toLowerCase(Locale.ROOT);
        return switch (codec) {
            case "subrip" -> "srt";
            case "ass", "ssa" -> "ass";
            case "webvtt" -> "vtt";
            case "hdmv_pgs_subtitle" -> "sup";
            default -> "sub";
        };
    }

    private MediaSubtitleDTO toDTO(MediaSubtitle entity) {
        return MediaSubtitleDTO.builder()
                .id(entity.getId())
                .mediaFileId(entity.getMediaFile() != null ? entity.getMediaFile().getId() : null)
                .streamIndex(entity.getStreamIndex())
                .trackName(entity.getTrackName())
                .language(entity.getLanguage())
                .codecName(entity.getCodecName())
                .subtitleFormat(entity.getSubtitleFormat())
                .fileName(entity.getFileName())
                .filePath(entity.getFilePath())
                .fileSize(entity.getFileSize())
                .timeOffset(entity.getTimeOffset())
                .isExternal(entity.getIsExternal())
                .sourceType(entity.getSourceType())
                .build();
    }

    private MediaSubtitleAdminDTO toAdminDTO(MediaSubtitle entity) {
        MediaFile mediaFile = entity.getMediaFile();
        return MediaSubtitleAdminDTO.builder()
                .id(entity.getId())
                .mediaFileId(mediaFile != null ? mediaFile.getId() : null)
                .trackName(entity.getTrackName())
                .language(entity.getLanguage())
                .subtitleFormat(entity.getSubtitleFormat())
                .fileName(entity.getFileName())
                .fileSize(entity.getFileSize())
                .timeOffset(entity.getTimeOffset())
                .isExternal(entity.getIsExternal())
                .sourceType(entity.getSourceType())
                .videoFileName(mediaFile != null ? mediaFile.getFileName() : null)
                .videoFilePath(mediaFile != null ? mediaFile.getFilePath() : null)
                .animeId(mediaFile != null ? mediaFile.getAnimeId() : null)
                .animeTitle(mediaFile != null ? mediaFile.getAnimeTitle() : null)
                .episodeTitle(mediaFile != null ? mediaFile.getEpisodeTitle() : null)
                .libraryId(mediaFile != null && mediaFile.getLibrary() != null ? mediaFile.getLibrary().getId() : null)
                .libraryName(mediaFile != null && mediaFile.getLibrary() != null ? mediaFile.getLibrary().getName() : null)
                .build();
    }

    private record SubtitleStreamInfo(int streamIndex, String trackName, String language, String codecName) {
    }
}
