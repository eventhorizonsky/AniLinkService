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

    @Schema(description = "Dandan 应用 ID", example = "your_app_id")
    private String dandanAppId;

    @Schema(description = "Dandan 应用密钥", example = "your_app_secret")
    private String dandanAppSecret;

    @Schema(description = "Dandan API 地址", example = "https://api.dandanplay.net")
    private String dandanBaseUrl;

    @Schema(description = "是否开放注册", example = "false")
    private Boolean authRegisterEnabled;

    @Schema(description = "是否开启远程访问（API v1）", example = "true")
    private Boolean remoteAccessEnabled;

    @Schema(description = "远程访问是否需要授权", example = "true")
    private Boolean remoteAccessTokenRequired;

    @Schema(description = "远程访问授权所需角色代码", example = "user")
    private String remoteAccessRequiredRole;

    @Schema(description = "SMTP 主机", example = "smtp.qq.com")
    private String smtpHost;

    @Schema(description = "SMTP 端口", example = "465")
    private Integer smtpPort;

    @Schema(description = "SMTP 用户名", example = "noreply@example.com")
    private String smtpUsername;

    @Schema(description = "SMTP 密码", example = "smtp_auth_code")
    private String smtpPassword;

    @Schema(description = "SMTP 发件邮箱", example = "noreply@example.com")
    private String smtpFromEmail;

    @Schema(description = "SMTP 发件人", example = "AniLink")
    private String smtpFromName;

    @Schema(description = "是否启用 SMTP SSL", example = "true")
    private Boolean smtpSslEnabled;

    @Schema(description = "是否启用 STARTTLS", example = "false")
    private Boolean smtpStarttlsEnabled;

    public String getDandanBaseUrl() {
        return dandanBaseUrl;
    }

    public void setDandanBaseUrl(String dandanBaseUrl) {
        this.dandanBaseUrl = dandanBaseUrl;
    }
}
