package xyz.ezsky.anilink.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 更新站点配置请求DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "更新站点配置请求")
public class UpdateSiteConfigRequest {
    
    @Schema(description = "站点名称", example = "AniLink Service")
    private String siteName;
    
    @Schema(description = "站点描述", example = "番剧识别与弹幕播放服务")
    private String siteDescription;
    
    @Schema(description = "站点URL", example = "http://localhost:8081")
    private String siteUrl;

    @Schema(description = "Dandan 应用 ID", example = "your_app_id")
    private String dandanAppId;

    @Schema(description = "Dandan 应用密钥", example = "your_app_secret")
    private String dandanAppSecret;

    @Schema(description = "Dandan API 地址", example = "https://api.dandanplay.net")
    private String dandanBaseUrl;

    @Schema(description = "资源搜索节点地址", example = "http://127.0.0.1:9000")
    private String resourceNodeBaseUrl;

    @Schema(description = "下载暂存目录", example = "D:/AniLinkDownloads/tmp")
    private String resourceDownloadTempDir;

    @Schema(description = "下载并发上限", example = "2")
    private Integer resourceDownloadMaxConcurrency;

    @Schema(description = "下载限速 KB/s，0 表示不限速", example = "0")
    private Integer resourceDownloadLimitKbps;

    @Schema(description = "上传限速 KB/s，0 表示不限速", example = "0")
    private Integer resourceUploadLimitKbps;

    @Schema(description = "下载完成后的做种时长（秒）", example = "0")
    private Integer resourceSeedTimeSeconds;

    @Schema(description = "新任务附加 Tracker（每行一个或逗号分隔）", example = "udp://tracker.opentrackr.org:1337/announce")
    private String resourceCustomTrackers;

    @Schema(description = "资源节点请求代理主机", example = "127.0.0.1")
    private String resourceNodeProxyHost;

    @Schema(description = "资源节点请求代理端口", example = "7890")
    private Integer resourceNodeProxyPort;

    @Schema(description = "RSS 请求代理主机", example = "127.0.0.1")
    private String rssProxyHost;

    @Schema(description = "RSS 请求代理端口", example = "7890")
    private Integer rssProxyPort;

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
