package xyz.ezsky.anilink.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 设置站点配置请求DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "设置站点配置请求")
public class SetSiteConfigRequest {
    
    @Schema(description = "站点名称", example = "AniLink Service")
    private String siteName;
    
    @Schema(description = "站点描述", example = "番剧识别与弹幕播放服务")
    private String siteDescription;
    
    @Schema(description = "站点URL", example = "http://localhost:8081")
    private String siteUrl;
    
    @Schema(description = "管理员账号", example = "admin")
    private String adminUsername;
    
    @Schema(description = "管理员密码", example = "123456")
    private String adminPassword;
}
