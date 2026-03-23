package xyz.ezsky.anilink.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import xyz.ezsky.anilink.model.vo.SiteConfigVO;
import xyz.ezsky.anilink.model.dto.SetSiteConfigRequest;
import xyz.ezsky.anilink.model.dto.UpdateSiteConfigRequest;
import xyz.ezsky.anilink.model.entity.SiteConfig;
import xyz.ezsky.anilink.model.entity.User;
import xyz.ezsky.anilink.repository.SiteConfigRepository;
import xyz.ezsky.anilink.repository.UserRepository;

import java.util.Optional;

/**
 * 站点配置服务
 */
@Service
public class SiteConfigService {
    
    @Autowired
    private SiteConfigRepository siteConfigRepository;
    
    @Autowired
    private UserRepository userRepository;
    
    @Autowired
    private UserService userService;
    
    // 配置键常量
    private static final String SITE_NAME = "site_name";
    private static final String SITE_DESCRIPTION = "site_description";
    private static final String SITE_URL = "site_url";
    private static final String INSTALLED = "installed";
    private static final String DANDAN_APP_ID = "dandan_app_id";
    private static final String DANDAN_APP_SECRET = "dandan_app_secret";
    private static final String DANDAN_BASE_URL = "dandan_base_url";
    private static final String RESOURCE_NODE_BASE_URL = "resource_node_base_url";
    private static final String RESOURCE_DOWNLOAD_TEMP_DIR = "resource_download_temp_dir";
    private static final String RESOURCE_DOWNLOAD_MAX_CONCURRENCY = "resource_download_max_concurrency";
    private static final String RESOURCE_DOWNLOAD_LIMIT_KBPS = "resource_download_limit_kbps";
    private static final String RESOURCE_UPLOAD_LIMIT_KBPS = "resource_upload_limit_kbps";
    private static final String RESOURCE_SEED_TIME_SECONDS = "resource_seed_time_seconds";
    private static final String RESOURCE_CUSTOM_TRACKERS = "resource_custom_trackers";
    private static final String RESOURCE_NODE_PROXY_HOST = "resource_node_proxy_host";
    private static final String RESOURCE_NODE_PROXY_PORT = "resource_node_proxy_port";
    private static final String RSS_PROXY_HOST = "rss_proxy_host";
    private static final String RSS_PROXY_PORT = "rss_proxy_port";
    private static final String AUTH_REGISTER_ENABLED = "auth_register_enabled";
    private static final String REMOTE_ACCESS_ENABLED = "remote_access_enabled";
    private static final String REMOTE_ACCESS_TOKEN_REQUIRED = "remote_access_token_required";
    private static final String REMOTE_ACCESS_REQUIRED_ROLE = "remote_access_required_role";
    private static final String SMTP_HOST = "smtp_host";
    private static final String SMTP_PORT = "smtp_port";
    private static final String SMTP_USERNAME = "smtp_username";
    private static final String SMTP_PASSWORD = "smtp_password";
    private static final String SMTP_FROM_EMAIL = "smtp_from_email";
    private static final String SMTP_FROM_NAME = "smtp_from_name";
    private static final String SMTP_SSL_ENABLED = "smtp_ssl_enabled";
    private static final String SMTP_STARTTLS_ENABLED = "smtp_starttls_enabled";
    private static final String MAIL_CODE_EXPIRE_SECONDS = "mail_code_expire_seconds";
    private static final String MAIL_SEND_COOLDOWN_SECONDS = "mail_send_cooldown_seconds";
    private static final String MAIL_SEND_DAILY_LIMIT_PER_IP = "mail_send_daily_limit_per_ip";
    private static final String MAIL_SEND_DAILY_LIMIT_PER_EMAIL = "mail_send_daily_limit_per_email";
    private static final String REGISTER_DAILY_LIMIT_PER_IP = "register_daily_limit_per_ip";
    private static final String DEFAULT_DANDAN_BASE_URL = "https://api.dandanplay.net";

    // 简单内存缓存，避免每次请求都访问数据库
    private volatile String cachedDandanAppId;
    private volatile String cachedDandanAppSecret;
    
    /**
     * 获取站点配置
     */
    public SiteConfigVO getSiteConfig() {
        SiteConfigVO vo = new SiteConfigVO();
        
        // 获取站点名称
        siteConfigRepository.findByConfigKey(SITE_NAME)
            .ifPresent(config -> vo.setSiteName(config.getConfigValue()));
        
        // 获取站点描述
        siteConfigRepository.findByConfigKey(SITE_DESCRIPTION)
            .ifPresent(config -> vo.setSiteDescription(config.getConfigValue()));
        
        // 获取站点URL
        siteConfigRepository.findByConfigKey(SITE_URL)
            .ifPresent(config -> vo.setSiteUrl(config.getConfigValue()));
        
        // 检查是否已安装
        Optional<SiteConfig> installedConfig = siteConfigRepository.findByConfigKey(INSTALLED);
        vo.setInstalled(installedConfig.isPresent() && "true".equals(installedConfig.get().getConfigValue()));

        // Dandan 配置
        siteConfigRepository.findByConfigKey(DANDAN_APP_ID)
            .ifPresent(config -> vo.setDandanAppId(config.getConfigValue()));
        String dandanAppSecret = getConfigValue(DANDAN_APP_SECRET);
        vo.setDandanAppSecretConfigured(dandanAppSecret != null && !dandanAppSecret.isBlank());
        vo.setDandanBaseUrl(getDandanBaseUrl());
        siteConfigRepository.findByConfigKey(RESOURCE_NODE_BASE_URL)
            .ifPresent(config -> vo.setResourceNodeBaseUrl(config.getConfigValue()));
        siteConfigRepository.findByConfigKey(RESOURCE_DOWNLOAD_TEMP_DIR)
            .ifPresent(config -> vo.setResourceDownloadTempDir(config.getConfigValue()));
        vo.setResourceDownloadMaxConcurrency(getResourceDownloadMaxConcurrency());
        vo.setResourceDownloadLimitKbps(getResourceDownloadLimitKbps());
        vo.setResourceUploadLimitKbps(getResourceUploadLimitKbps());
        vo.setResourceSeedTimeSeconds(getResourceSeedTimeSeconds());
        vo.setResourceCustomTrackers(getResourceCustomTrackers());
        vo.setResourceNodeProxyHost(getResourceNodeProxyHost());
        vo.setResourceNodeProxyPort(getResourceNodeProxyPort());
        vo.setRssProxyHost(getRssProxyHost());
        vo.setRssProxyPort(getRssProxyPort());

        vo.setAuthRegisterEnabled(isRegisterEnabled());
        vo.setRemoteAccessEnabled(isRemoteAccessEnabled());
        vo.setRemoteAccessTokenRequired(isRemoteAccessTokenRequired());
        vo.setRemoteAccessRequiredRole(getRemoteAccessRequiredRole());
        vo.setSmtpHost(getConfigValue(SMTP_HOST));
        vo.setSmtpPort(parseInt(getConfigValue(SMTP_PORT), null));
        vo.setSmtpUsername(getConfigValue(SMTP_USERNAME));
        vo.setSmtpFromEmail(getConfigValue(SMTP_FROM_EMAIL));
        vo.setSmtpFromName(getConfigValue(SMTP_FROM_NAME));
        vo.setSmtpSslEnabled(getBooleanConfig(SMTP_SSL_ENABLED, true));
        vo.setSmtpStarttlsEnabled(getBooleanConfig(SMTP_STARTTLS_ENABLED, false));
        String smtpPassword = getConfigValue(SMTP_PASSWORD);
        vo.setSmtpPasswordConfigured(smtpPassword != null && !smtpPassword.isBlank());
        
        return vo;
    }
    
    /**
     * 设置站点配置和管理员账号（初始化用）
     */
    public void setSiteConfig(SetSiteConfigRequest request) {
        // 设置站点名称
        saveOrUpdateConfig(SITE_NAME, request.getSiteName(), "站点名称");
        
        // 设置站点描述
        saveOrUpdateConfig(SITE_DESCRIPTION, request.getSiteDescription(), "站点描述");
        
        // 设置站点URL
        saveOrUpdateConfig(SITE_URL, request.getSiteUrl(), "站点URL");
        
        // 设置已安装标记
        saveOrUpdateConfig(INSTALLED, "true", "安装标记");
        
        // 创建管理员账号
        userService.createAdminUser(request.getAdminUsername(), request.getAdminPassword(), "example@example.com");

        // Dandan 配置（可选）
        if (request.getDandanAppId() != null) {
            saveOrUpdateConfig(DANDAN_APP_ID, request.getDandanAppId(), "Dandan 应用 ID");
            cachedDandanAppId = request.getDandanAppId();
        }
        if (request.getDandanAppSecret() != null && !request.getDandanAppSecret().isBlank()) {
            saveOrUpdateConfig(DANDAN_APP_SECRET, request.getDandanAppSecret(), "Dandan 应用密钥");
            cachedDandanAppSecret = request.getDandanAppSecret();
        }
        saveOrUpdateConfig(DANDAN_BASE_URL, normalizeDandanBaseUrl(request.getDandanBaseUrl()), "Dandan API 地址");

        saveOrUpdateConfig(
            AUTH_REGISTER_ENABLED,
            String.valueOf(Boolean.TRUE.equals(request.getAuthRegisterEnabled())),
            "是否开放注册"
        );

        saveOrUpdateConfig(
            REMOTE_ACCESS_ENABLED,
            String.valueOf(Boolean.TRUE.equals(request.getRemoteAccessEnabled())),
            "是否开启 API v1 远程访问"
        );
        saveOrUpdateConfig(
            REMOTE_ACCESS_TOKEN_REQUIRED,
            String.valueOf(Boolean.TRUE.equals(request.getRemoteAccessTokenRequired())),
            "API v1 远程访问是否需要授权"
        );
        saveOrUpdateConfig(
            REMOTE_ACCESS_REQUIRED_ROLE,
            normalizeRemoteAccessRole(request.getRemoteAccessRequiredRole()),
            "API v1 授权所需角色代码"
        );

        if (request.getSmtpHost() != null) {
            saveOrUpdateConfig(SMTP_HOST, request.getSmtpHost(), "SMTP 主机");
        }
        if (request.getSmtpPort() != null) {
            saveOrUpdateConfig(SMTP_PORT, String.valueOf(request.getSmtpPort()), "SMTP 端口");
        }
        if (request.getSmtpUsername() != null) {
            saveOrUpdateConfig(SMTP_USERNAME, request.getSmtpUsername(), "SMTP 用户名");
        }
        if (request.getSmtpPassword() != null && !request.getSmtpPassword().isBlank()) {
            saveOrUpdateConfig(SMTP_PASSWORD, request.getSmtpPassword(), "SMTP 密码");
        }
        if (request.getSmtpFromEmail() != null) {
            saveOrUpdateConfig(SMTP_FROM_EMAIL, request.getSmtpFromEmail(), "SMTP 发件邮箱");
        }
        if (request.getSmtpFromName() != null) {
            saveOrUpdateConfig(SMTP_FROM_NAME, request.getSmtpFromName(), "SMTP 发件人");
        }
        if (request.getSmtpSslEnabled() != null) {
            saveOrUpdateConfig(SMTP_SSL_ENABLED, String.valueOf(request.getSmtpSslEnabled()), "SMTP SSL 开关");
        }
        if (request.getSmtpStarttlsEnabled() != null) {
            saveOrUpdateConfig(SMTP_STARTTLS_ENABLED, String.valueOf(request.getSmtpStarttlsEnabled()), "SMTP STARTTLS 开关");
        }

        saveOrUpdateConfig(MAIL_CODE_EXPIRE_SECONDS, "300", "邮箱验证码过期秒数");
        saveOrUpdateConfig(MAIL_SEND_COOLDOWN_SECONDS, "60", "邮箱验证码发送冷却秒数");
        saveOrUpdateConfig(MAIL_SEND_DAILY_LIMIT_PER_IP, "30", "单 IP 每日邮箱验证码发送上限");
        saveOrUpdateConfig(MAIL_SEND_DAILY_LIMIT_PER_EMAIL, "10", "单邮箱每日验证码发送上限");
        saveOrUpdateConfig(REGISTER_DAILY_LIMIT_PER_IP, "20", "单 IP 每日注册上限");
    }
    
    /**
     * 更新站点配置
     */
    public void updateSiteConfig(UpdateSiteConfigRequest request) {
        if (request.getSiteName() != null) {
            saveOrUpdateConfig(SITE_NAME, request.getSiteName(), "站点名称");
        }
        if (request.getSiteDescription() != null) {
            saveOrUpdateConfig(SITE_DESCRIPTION, request.getSiteDescription(), "站点描述");
        }
        if (request.getSiteUrl() != null) {
            saveOrUpdateConfig(SITE_URL, request.getSiteUrl(), "站点URL");
        }
        if (request.getDandanAppId() != null) {
            saveOrUpdateConfig(DANDAN_APP_ID, request.getDandanAppId(), "Dandan 应用 ID");
            cachedDandanAppId = request.getDandanAppId();
        }
        if (request.getDandanAppSecret() != null && !request.getDandanAppSecret().isBlank()) {
            saveOrUpdateConfig(DANDAN_APP_SECRET, request.getDandanAppSecret(), "Dandan 应用密钥");
            cachedDandanAppSecret = request.getDandanAppSecret();
        }
        if (request.getDandanBaseUrl() != null) {
            saveOrUpdateConfig(DANDAN_BASE_URL, normalizeDandanBaseUrl(request.getDandanBaseUrl()), "Dandan API 地址");
        }
        if (request.getResourceNodeBaseUrl() != null) {
            saveOrUpdateConfig(RESOURCE_NODE_BASE_URL, request.getResourceNodeBaseUrl(), "资源搜索节点地址");
        }
        if (request.getResourceDownloadTempDir() != null) {
            saveOrUpdateConfig(RESOURCE_DOWNLOAD_TEMP_DIR, request.getResourceDownloadTempDir(), "下载暂存目录");
        }
        if (request.getResourceDownloadMaxConcurrency() != null) {
            saveOrUpdateConfig(
                RESOURCE_DOWNLOAD_MAX_CONCURRENCY,
                String.valueOf(Math.max(1, request.getResourceDownloadMaxConcurrency())),
                "下载并发上限"
            );
        }
        if (request.getResourceDownloadLimitKbps() != null) {
            saveOrUpdateConfig(
                RESOURCE_DOWNLOAD_LIMIT_KBPS,
                String.valueOf(Math.max(0, request.getResourceDownloadLimitKbps())),
                "下载限速 KB/s"
            );
        }
        if (request.getResourceUploadLimitKbps() != null) {
            saveOrUpdateConfig(
                RESOURCE_UPLOAD_LIMIT_KBPS,
                String.valueOf(Math.max(0, request.getResourceUploadLimitKbps())),
                "上传限速 KB/s"
            );
        }
        if (request.getResourceSeedTimeSeconds() != null) {
            saveOrUpdateConfig(
                RESOURCE_SEED_TIME_SECONDS,
                String.valueOf(Math.max(0, request.getResourceSeedTimeSeconds())),
                "下载完成后做种时长（秒）"
            );
        }
        if (request.getResourceCustomTrackers() != null) {
            saveOrUpdateConfig(RESOURCE_CUSTOM_TRACKERS, request.getResourceCustomTrackers(), "新任务附加 Tracker");
        }
        if (request.getResourceNodeProxyHost() != null) {
            saveOrUpdateConfig(RESOURCE_NODE_PROXY_HOST, request.getResourceNodeProxyHost(), "资源节点请求代理主机");
        }
        if (request.getResourceNodeProxyPort() != null) {
            saveOrUpdateConfig(RESOURCE_NODE_PROXY_PORT, String.valueOf(Math.max(0, request.getResourceNodeProxyPort())), "资源节点请求代理端口");
        }
        if (request.getRssProxyHost() != null) {
            saveOrUpdateConfig(RSS_PROXY_HOST, request.getRssProxyHost(), "RSS 请求代理主机");
        }
        if (request.getRssProxyPort() != null) {
            saveOrUpdateConfig(RSS_PROXY_PORT, String.valueOf(Math.max(0, request.getRssProxyPort())), "RSS 请求代理端口");
        }
        if (request.getAuthRegisterEnabled() != null) {
            saveOrUpdateConfig(AUTH_REGISTER_ENABLED, String.valueOf(request.getAuthRegisterEnabled()), "是否开放注册");
        }
        if (request.getRemoteAccessEnabled() != null) {
            saveOrUpdateConfig(REMOTE_ACCESS_ENABLED, String.valueOf(request.getRemoteAccessEnabled()), "是否开启 API v1 远程访问");
        }
        if (request.getRemoteAccessTokenRequired() != null) {
            saveOrUpdateConfig(REMOTE_ACCESS_TOKEN_REQUIRED, String.valueOf(request.getRemoteAccessTokenRequired()), "API v1 远程访问是否需要授权");
        }
        if (request.getRemoteAccessRequiredRole() != null) {
            saveOrUpdateConfig(REMOTE_ACCESS_REQUIRED_ROLE, normalizeRemoteAccessRole(request.getRemoteAccessRequiredRole()), "API v1 授权所需角色代码");
        }
        if (request.getSmtpHost() != null) {
            saveOrUpdateConfig(SMTP_HOST, request.getSmtpHost(), "SMTP 主机");
        }
        if (request.getSmtpPort() != null) {
            saveOrUpdateConfig(SMTP_PORT, String.valueOf(request.getSmtpPort()), "SMTP 端口");
        }
        if (request.getSmtpUsername() != null) {
            saveOrUpdateConfig(SMTP_USERNAME, request.getSmtpUsername(), "SMTP 用户名");
        }
        if (request.getSmtpPassword() != null && !request.getSmtpPassword().isBlank()) {
            saveOrUpdateConfig(SMTP_PASSWORD, request.getSmtpPassword(), "SMTP 密码");
        }
        if (request.getSmtpFromEmail() != null) {
            saveOrUpdateConfig(SMTP_FROM_EMAIL, request.getSmtpFromEmail(), "SMTP 发件邮箱");
        }
        if (request.getSmtpFromName() != null) {
            saveOrUpdateConfig(SMTP_FROM_NAME, request.getSmtpFromName(), "SMTP 发件人");
        }
        if (request.getSmtpSslEnabled() != null) {
            saveOrUpdateConfig(SMTP_SSL_ENABLED, String.valueOf(request.getSmtpSslEnabled()), "SMTP SSL 开关");
        }
        if (request.getSmtpStarttlsEnabled() != null) {
            saveOrUpdateConfig(SMTP_STARTTLS_ENABLED, String.valueOf(request.getSmtpStarttlsEnabled()), "SMTP STARTTLS 开关");
        }
    }

    public boolean isRegisterEnabled() {
        return getBooleanConfig(AUTH_REGISTER_ENABLED, false);
    }

    public boolean isRemoteAccessEnabled() {
        return getBooleanConfig(REMOTE_ACCESS_ENABLED, false);
    }

    public boolean isRemoteAccessTokenRequired() {
        return getBooleanConfig(REMOTE_ACCESS_TOKEN_REQUIRED, false);
    }

    public String getRemoteAccessRequiredRole() {
        return normalizeRemoteAccessRole(getConfigValue(REMOTE_ACCESS_REQUIRED_ROLE));
    }

    public int getMailCodeExpireSeconds() {
        return getIntConfig(MAIL_CODE_EXPIRE_SECONDS, 300);
    }

    public int getMailSendCooldownSeconds() {
        return getIntConfig(MAIL_SEND_COOLDOWN_SECONDS, 60);
    }

    public int getMailSendDailyLimitPerIp() {
        return getIntConfig(MAIL_SEND_DAILY_LIMIT_PER_IP, 30);
    }

    public int getMailSendDailyLimitPerEmail() {
        return getIntConfig(MAIL_SEND_DAILY_LIMIT_PER_EMAIL, 10);
    }

    public int getRegisterDailyLimitPerIp() {
        return getIntConfig(REGISTER_DAILY_LIMIT_PER_IP, 20);
    }

    public String getResourceNodeBaseUrl() {
        return getConfigValue(RESOURCE_NODE_BASE_URL);
    }

    public String getResourceDownloadTempDir() {
        return getConfigValue(RESOURCE_DOWNLOAD_TEMP_DIR);
    }

    public int getResourceDownloadMaxConcurrency() {
        return getIntConfig(RESOURCE_DOWNLOAD_MAX_CONCURRENCY, 2);
    }

    public int getResourceDownloadLimitKbps() {
        return getIntConfig(RESOURCE_DOWNLOAD_LIMIT_KBPS, 0);
    }

    public int getResourceUploadLimitKbps() {
        return getIntConfig(RESOURCE_UPLOAD_LIMIT_KBPS, 0);
    }

    public int getResourceSeedTimeSeconds() {
        return getIntConfig(RESOURCE_SEED_TIME_SECONDS, 0);
    }

    public String getResourceCustomTrackers() {
        return getConfigValue(RESOURCE_CUSTOM_TRACKERS);
    }

    public String getResourceNodeProxyHost() {
        return getConfigValue(RESOURCE_NODE_PROXY_HOST);
    }

    public Integer getResourceNodeProxyPort() {
        return parseInt(getConfigValue(RESOURCE_NODE_PROXY_PORT), 0);
    }

    public String getRssProxyHost() {
        return getConfigValue(RSS_PROXY_HOST);
    }

    public Integer getRssProxyPort() {
        return parseInt(getConfigValue(RSS_PROXY_PORT), 0);
    }

    public SmtpSettings getSmtpSettings() {
        String host = getConfigValue(SMTP_HOST);
        Integer port = parseInt(getConfigValue(SMTP_PORT), null);
        String username = getConfigValue(SMTP_USERNAME);
        String password = getConfigValue(SMTP_PASSWORD);
        String fromEmail = getConfigValue(SMTP_FROM_EMAIL);
        String fromName = getConfigValue(SMTP_FROM_NAME);
        boolean sslEnabled = getBooleanConfig(SMTP_SSL_ENABLED, true);
        boolean starttlsEnabled = getBooleanConfig(SMTP_STARTTLS_ENABLED, false);
        return new SmtpSettings(host, port, username, password, fromEmail, fromName, sslEnabled, starttlsEnabled);
    }

    private boolean getBooleanConfig(String key, boolean defaultValue) {
        String value = getConfigValue(key);
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        return Boolean.parseBoolean(value);
    }

    private int getIntConfig(String key, int defaultValue) {
        return parseInt(getConfigValue(key), defaultValue);
    }

    private Integer parseInt(String value, Integer defaultValue) {
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private String getConfigValue(String key) {
        return siteConfigRepository.findByConfigKey(key)
            .map(SiteConfig::getConfigValue)
            .orElse(null);
    }

    private String normalizeRemoteAccessRole(String roleCode) {
        if (roleCode == null || roleCode.isBlank()) {
            return "user";
        }
        return roleCode.trim();
    }

    private String normalizeDandanBaseUrl(String baseUrl) {
        if (baseUrl == null || baseUrl.isBlank()) {
            return DEFAULT_DANDAN_BASE_URL;
        }
        return baseUrl.trim();
    }
    
    /**
     * 保存或更新配置
     */
    private void saveOrUpdateConfig(String key, String value, String description) {
        Optional<SiteConfig> configOpt = siteConfigRepository.findByConfigKey(key);
        
        if (configOpt.isPresent()) {
            // 更新现有配置
            SiteConfig config = configOpt.get();
            config.setConfigValue(value);
            siteConfigRepository.save(config);
        } else {
            // 创建新配置
            SiteConfig config = new SiteConfig();
            config.setConfigKey(key);
            config.setConfigValue(value);
            config.setDescription(description);
            siteConfigRepository.save(config);
        }
    }

    /**
     * 获取缓存的 Dandan AppId，如果缓存为空则从数据库加载
     */
    public String getDandanAppId() {
        if (cachedDandanAppId == null) {
            synchronized (this) {
                if (cachedDandanAppId == null) {
                    cachedDandanAppId = siteConfigRepository.findByConfigKey(DANDAN_APP_ID)
                        .map(SiteConfig::getConfigValue)
                        .orElse(null);
                }
            }
        }
        return cachedDandanAppId;
    }

    /**
     * 获取缓存的 Dandan AppSecret，如果缓存为空则从数据库加载
     */
    public String getDandanAppSecret() {
        if (cachedDandanAppSecret == null) {
            synchronized (this) {
                if (cachedDandanAppSecret == null) {
                    cachedDandanAppSecret = siteConfigRepository.findByConfigKey(DANDAN_APP_SECRET)
                        .map(SiteConfig::getConfigValue)
                        .orElse(null);
                }
            }
        }
        return cachedDandanAppSecret;
    }

    public String getDandanBaseUrl() {
        return normalizeDandanBaseUrl(getConfigValue(DANDAN_BASE_URL));
    }
    
    /**
     * 检查是否已安装
     */
    public boolean isInstalled() {
        Optional<SiteConfig> config = siteConfigRepository.findByConfigKey(INSTALLED);
        return config.isPresent() && "true".equals(config.get().getConfigValue());
    }

    public static class SmtpSettings {
        private final String host;
        private final Integer port;
        private final String username;
        private final String password;
        private final String fromEmail;
        private final String fromName;
        private final Boolean sslEnabled;
        private final Boolean starttlsEnabled;

        public SmtpSettings(
                String host,
                Integer port,
                String username,
                String password,
                String fromEmail,
                String fromName,
                Boolean sslEnabled,
                Boolean starttlsEnabled
        ) {
            this.host = host;
            this.port = port;
            this.username = username;
            this.password = password;
            this.fromEmail = fromEmail;
            this.fromName = fromName;
            this.sslEnabled = sslEnabled;
            this.starttlsEnabled = starttlsEnabled;
        }

        public String getHost() {
            return host;
        }

        public Integer getPort() {
            return port;
        }

        public String getUsername() {
            return username;
        }

        public String getPassword() {
            return password;
        }

        public String getFromEmail() {
            return fromEmail;
        }

        public String getFromName() {
            return fromName;
        }

        public Boolean getSslEnabled() {
            return sslEnabled;
        }

        public Boolean getStarttlsEnabled() {
            return starttlsEnabled;
        }

        public boolean isConfigured() {
            return host != null && !host.isBlank()
                    && port != null
                    && username != null && !username.isBlank()
                    && password != null && !password.isBlank()
                    && fromEmail != null && !fromEmail.isBlank();
        }
    }
}
