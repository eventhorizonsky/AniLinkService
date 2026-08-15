package xyz.ezsky.anilink.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 播放信息VO
 *
 * <p>提供给前端用于决定播放方式：直接播放、秒转封装（remux）或转码（transcode）。
 * 前端根据本机浏览器能力检测（canPlayType / MSE）在这些候选之间做最终选择。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "播放信息（供前端能力检测后决策播放方式）")
public class PlayInfoVO {

    @Schema(description = "媒体文件ID")
    private Long mediaFileId;

    @Schema(description = "文件名")
    private String fileName;

    @Schema(description = "容器格式，如 matroska/mp4/webm")
    private String containerFormat;

    @Schema(description = "视频编码，如 h264/hevc/av1")
    private String videoCodec;

    @Schema(description = "音频编码，如 aac/flac/ac3/dts")
    private String audioCodec;

    @Schema(description = "色彩深度，如 8-bit/10-bit")
    private String colorDepth;

    @Schema(description = "HDR 类型，如 HDR10/Dolby Vision，无则为 null")
    private String hdrType;

    @Schema(description = "时长（毫秒）")
    private Long duration;

    @Schema(description = "分辨率宽")
    private Integer width;

    @Schema(description = "分辨率高")
    private Integer height;

    @Schema(description = "是否启用转码/秒转（站点开关）")
    private Boolean transcodeEnabled;

    @Schema(description = "后端推荐模式：direct/remux/transcode")
    private String recommendedMode;

    @Schema(description = "直接播放流地址（原始文件 Range 流）")
    private String streamUrl;

    @Schema(description = "秒转封装 HLS 播放地址（remux，视频+音频均原样封装，仅编码均兼容时可用）")
    private String remuxUrl;

    @Schema(description = "混合 HLS 播放地址（视频直通 -c:v copy + 音频转码 AAC，仅视频编码兼容时可用）")
    private String mixedUrl;

    @Schema(description = "转码 HLS 播放地址（transcode）")
    private String transcodeUrl;
}
