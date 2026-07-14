package xyz.ezsky.anilink.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "RSS 正则过滤预览请求")
public class RssFilterPreviewRequest {
    @Schema(description = "RSS 地址")
    private String feedUrl;

    @Schema(description = "正向过滤正则（匹配标题，符合则下载）")
    private String includeFilter;

    @Schema(description = "排除过滤正则（匹配标题，符合则跳过）")
    private String excludeFilter;
}
