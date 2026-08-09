package xyz.ezsky.anilink.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

/**
 * Tracker 列表订阅状态 VO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Tracker 列表订阅状态")
public class TrackerListStatusVO {

    @Schema(description = "订阅地址（每行一个）", example = "https://raw.githubusercontent.com/ngosang/trackerslist/master/trackers_all.txt")
    private String url;

    @Schema(description = "是否已配置并启用", example = "true")
    private Boolean enabled;

    @Schema(description = "是否正在刷新", example = "false")
    private Boolean refreshing;

    @Schema(description = "最近一次拉取成功时间")
    private Instant lastFetchedAt;

    @Schema(description = "最近一次错误信息")
    private String lastError;

    @Schema(description = "已订阅 Tracker 数量", example = "3100")
    private Integer trackerCount;

    @Schema(description = "已订阅 Tracker 列表")
    private List<String> trackers;
}
