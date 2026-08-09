package xyz.ezsky.anilink.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 组合 Tracker 列表 VO
 *
 * <p>用于展示新下载任务实际会附加的最终 Tracker 列表：自定义 Tracker 与订阅 Tracker 合并去重后的结果。</p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "组合 Tracker 列表")
public class CombinedTrackerListVO {

    @Schema(description = "自定义 Tracker")
    private List<String> customTrackers;

    @Schema(description = "订阅 Tracker")
    private List<String> subscribedTrackers;

    @Schema(description = "最终合并去重后的 Tracker 列表")
    private List<String> combinedTrackers;
}
