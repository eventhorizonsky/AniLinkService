package xyz.ezsky.anilink.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 获取站点配置响应VO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "站点配置信息")
public class SiteConfigVO {
    
    @Schema(description = "站点名称", example = "AniLink Service")
    private String siteName;
    
    @Schema(description = "站点描述", example = "番剧识别与弹幕播放服务")
    private String siteDescription;
    
    @Schema(description = "站点URL", example = "http://localhost:8081")
    private String siteUrl;
    
    @Schema(description = "是否已安装", example = "true")
    private boolean installed;
}
