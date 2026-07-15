package xyz.ezsky.anilink.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 发送弹幕请求 DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "发送弹幕请求")
public class SendDanmakuRequest {

    @Schema(description = "弹幕出现时间，单位为秒", example = "12.5")
    private Double time;

    @Schema(description = "弹幕模式：1-普通弹幕，4-顶部弹幕，5-底部弹幕", example = "1")
    private Integer mode;

    @Schema(description = "弹幕颜色，计算方式为 R*255*255+G*255+B", example = "16777215")
    private Integer color;

    @Schema(description = "弹幕内容，不能长于100个字符", example = "前方高能")
    private String comment;

    @Schema(description = "番剧 ID（用于记录上下文）")
    private Long animeId;

    @Schema(description = "番剧标题（用于记录上下文）")
    private String animeTitle;

    @Schema(description = "视频文件 ID（用于记录上下文）")
    private Long videoId;

    @Schema(description = "分集标题（用于记录上下文）")
    private String episodeTitle;
}
