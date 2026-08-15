package xyz.ezsky.anilink.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import xyz.ezsky.anilink.model.entity.MediaFile;
import xyz.ezsky.anilink.model.vo.ApiResponseVO;
import xyz.ezsky.anilink.model.vo.PlayInfoVO;
import xyz.ezsky.anilink.service.MediaFileService;
import xyz.ezsky.anilink.service.MediaTranscodeService;

import java.nio.file.Path;

/**
 * Web 播放相关 API。
 *
 * <p>play-info 返回文件的编码/容器事实与候选播放地址，前端基于浏览器能力
 * （canPlayType / MediaSource.isTypeSupported）在 direct/remux/transcode 之间做最终选择；
 * 转码/秒转流以 HLS（m3u8 + ts）对外提供，便于拖动 seek。</p>
 */
@Log4j2
@Tag(name = "Web 播放", description = "播放信息查询与服务端转码/秒转 HLS 流")
@RestController
@RequestMapping("/api/media-files")
public class MediaPlayController {

    private static final String API_PREFIX = "/api";
    private static final MediaType M3U8_MEDIA_TYPE = MediaType.parseMediaType("application/vnd.apple.mpegurl");
    private static final MediaType MPEGTS_MEDIA_TYPE = MediaType.parseMediaType("video/mp2t");

    @Autowired
    private MediaFileService mediaFileService;

    @Autowired
    private MediaTranscodeService mediaTranscodeService;

    @Operation(summary = "获取播放信息", description = "返回媒体文件的容器/编码事实、后端推荐模式与 direct/remux/transcode 候选播放地址")
    @GetMapping("/{id}/play-info")
    public ApiResponseVO<PlayInfoVO> getPlayInfo(
            @Parameter(description = "媒体文件ID")
            @PathVariable Long id) {
        MediaFile mediaFile = mediaFileService.getMediaFileById(id);
        if (mediaFile == null) {
            return ApiResponseVO.fail(404, "媒体文件不存在");
        }

        boolean transcodeEnabled = mediaTranscodeService.isTranscodeEnabled() && mediaTranscodeService.isFfmpegAvailable();
        MediaTranscodeService.PlayMode recommended = mediaTranscodeService.recommendMode(mediaFile);

        PlayInfoVO.PlayInfoVOBuilder builder = PlayInfoVO.builder()
                .mediaFileId(mediaFile.getId())
                .fileName(mediaFile.getFileName())
                .containerFormat(mediaFile.getContainerFormat())
                .videoCodec(mediaFile.getVideoCodec())
                .audioCodec(mediaFile.getAudioCodec())
                .colorDepth(mediaFile.getColorDepth())
                .hdrType(mediaFile.getHdrType())
                .duration(mediaFile.getDuration())
                .width(mediaFile.getWidth())
                .height(mediaFile.getHeight())
                .transcodeEnabled(transcodeEnabled)
                .recommendedMode(recommended.name().toLowerCase())
                .streamUrl(API_PREFIX + "/media-files/stream/" + id);

        if (transcodeEnabled) {
            boolean remuxable = mediaTranscodeService.isRemuxable(mediaFile);
            boolean videoCopyable = mediaTranscodeService.canCopyVideoToTs(mediaFile);
            builder.remuxUrl(remuxable ? API_PREFIX + "/media-files/" + id + "/transcode/index.m3u8?mode=remux" : null);
            builder.mixedUrl(videoCopyable ? API_PREFIX + "/media-files/" + id + "/transcode/index.m3u8?mode=mixed" : null);
            builder.transcodeUrl(API_PREFIX + "/media-files/" + id + "/transcode/index.m3u8?mode=transcode");
        }

        return ApiResponseVO.success(builder.build());
    }

    @Operation(summary = "获取转码/秒转 HLS 清单", description = "按需启动 ffmpeg 会话并返回 m3u8 清单；清单为按源时长合成的完整 VOD 清单（含 ENDLIST），浏览器可一次性获得总时长。首次请求会等待首个分片就绪")
    @GetMapping("/{id}/transcode/index.m3u8")
    public ResponseEntity<?> getTranscodeManifest(
            @Parameter(description = "媒体文件ID")
            @PathVariable Long id,
            @Parameter(description = "模式：remux 秒转封装 / transcode 转码")
            @RequestParam(defaultValue = "transcode") String mode) {
        MediaTranscodeService.PlayMode playMode = parseMode(mode);
        if (playMode == MediaTranscodeService.PlayMode.DIRECT) {
            return ResponseEntity.badRequest().body(ApiResponseVO.fail(400, "不支持的转码模式: " + mode));
        }
        try {
            String content = mediaTranscodeService.getManifestContent(id, playMode);
            return ResponseEntity.ok()
                    .contentType(M3U8_MEDIA_TYPE)
                    .header(HttpHeaders.CACHE_CONTROL, "no-cache")
                    .body(content);
        } catch (IllegalStateException e) {
            log.warn("转码清单请求失败：id={} mode={} msg={}", id, mode, e.getMessage());
            return ResponseEntity.status(503).body(ApiResponseVO.fail(503, e.getMessage()));
        }
    }

    @Operation(summary = "获取转码/秒转 HLS 分片", description = "返回 m3u8 引用的 ts 分片文件（m3u8 中的 base_url 已指向 /transcode/{mode}/segments/）")
    @GetMapping("/{id}/transcode/{mode}/segments/{segmentName:.+}")
    public ResponseEntity<Resource> getTranscodeSegment(
            @Parameter(description = "媒体文件ID")
            @PathVariable Long id,
            @Parameter(description = "模式：remux 秒转封装 / transcode 转码")
            @PathVariable String mode,
            @Parameter(description = "分片文件名，如 seg_00001.ts")
            @PathVariable String segmentName) {
        MediaTranscodeService.PlayMode playMode = parseMode(mode);
        if (playMode == MediaTranscodeService.PlayMode.DIRECT) {
            return ResponseEntity.badRequest().build();
        }
        Path segment = mediaTranscodeService.resolveSegment(id, playMode, segmentName);
        if (segment == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok()
                .contentType(MPEGTS_MEDIA_TYPE)
                .header(HttpHeaders.CACHE_CONTROL, "public, max-age=86400")
                .body(new FileSystemResource(segment.toFile()));
    }

    private MediaTranscodeService.PlayMode parseMode(String mode) {
        try {
            return MediaTranscodeService.PlayMode.valueOf((mode == null ? "" : mode).trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return MediaTranscodeService.PlayMode.DIRECT;
        }
    }
}
