package xyz.ezsky.anilink.model.vo;

/**
 * 系统信息视图对象
 */
public class SystemInfoVO {
    
    /**
     * 数据库类型（MySQL、PostgreSQL、H2等）
     */
    private String dbType;
    
    /**
     * 数据库版本
     */
    private String dbVersion;
    
    /**
     * 服务器IP地址
     */
    private String serverIp;
    
    /**
     * 服务器主机名
     */
    private String hostname;
    
    /**
     * 操作系统名称
     */
    private String osName;
    
    /**
     * 操作系统版本
     */
    private String osVersion;
    
    /**
     * 操作系统架构
     */
    private String osArch;
    
    /**
     * 可用处理器数（CPU核数）
     */
    private Integer availableProcessors;
    
    /**
     * JVM最大内存（MB）
     */
    private Long maxMemoryMB;
    
    /**
     * JVM已分配内存（MB）
     */
    private Long totalMemoryMB;
    
    /**
     * JVM可用内存（MB）
     */
    private Long freeMemoryMB;
    
    /**
     * Java版本
     */
    private String javaVersion;
    
    /**
     * Java供应商
     */
    private String javaVendor;
    
    /**
     * 系统运行时间（秒）
     */
    private Long uptimeSeconds;
    
    /**
     * Liquibase是否已启用
     */
    private Boolean liquibaseEnabled;
    
    /**
     * Liquibase变更集数量
     */
    private Integer liquibaseChangeSets;
    
    /**
     * Liquibase是否成功初始化
     */
    private Boolean liquibaseInitialized;
    
    /**
     * Liquibase最后执行时间
     */
    private String liquibaseLastExecuted;

    public SystemInfoVO() {
    }

    public SystemInfoVO(String dbType, String dbVersion, String serverIp, String hostname, 
                     String osName, String osVersion, String osArch, 
                     Integer availableProcessors, Long maxMemoryMB, 
                     Long totalMemoryMB, Long freeMemoryMB, 
                     String javaVersion, String javaVendor, 
                     Long uptimeSeconds, Boolean liquibaseEnabled, 
                     Integer liquibaseChangeSets, Boolean liquibaseInitialized, 
                     String liquibaseLastExecuted) {
        this.dbType = dbType;
        this.dbVersion = dbVersion;
        this.serverIp = serverIp;
        this.hostname = hostname;
        this.osName = osName;
        this.osVersion = osVersion;
        this.osArch = osArch;
        this.availableProcessors = availableProcessors;
        this.maxMemoryMB = maxMemoryMB;
        this.totalMemoryMB = totalMemoryMB;
        this.freeMemoryMB = freeMemoryMB;
        this.javaVersion = javaVersion;
        this.javaVendor = javaVendor;
        this.uptimeSeconds = uptimeSeconds;
        this.liquibaseEnabled = liquibaseEnabled;
        this.liquibaseChangeSets = liquibaseChangeSets;
        this.liquibaseInitialized = liquibaseInitialized;
        this.liquibaseLastExecuted = liquibaseLastExecuted;
    }

    public String getDbType() {
        return dbType;
    }

    public void setDbType(String dbType) {
        this.dbType = dbType;
    }

    public String getDbVersion() {
        return dbVersion;
    }

    public void setDbVersion(String dbVersion) {
        this.dbVersion = dbVersion;
    }

    public String getServerIp() {
        return serverIp;
    }

    public void setServerIp(String serverIp) {
        this.serverIp = serverIp;
    }

    public String getHostname() {
        return hostname;
    }

    public void setHostname(String hostname) {
        this.hostname = hostname;
    }

    public String getOsName() {
        return osName;
    }

    public void setOsName(String osName) {
        this.osName = osName;
    }

    public String getOsVersion() {
        return osVersion;
    }

    public void setOsVersion(String osVersion) {
        this.osVersion = osVersion;
    }

    public String getOsArch() {
        return osArch;
    }

    public void setOsArch(String osArch) {
        this.osArch = osArch;
    }

    public Integer getAvailableProcessors() {
        return availableProcessors;
    }

    public void setAvailableProcessors(Integer availableProcessors) {
        this.availableProcessors = availableProcessors;
    }

    public Long getMaxMemoryMB() {
        return maxMemoryMB;
    }

    public void setMaxMemoryMB(Long maxMemoryMB) {
        this.maxMemoryMB = maxMemoryMB;
    }

    public Long getTotalMemoryMB() {
        return totalMemoryMB;
    }

    public void setTotalMemoryMB(Long totalMemoryMB) {
        this.totalMemoryMB = totalMemoryMB;
    }

    public Long getFreeMemoryMB() {
        return freeMemoryMB;
    }

    public void setFreeMemoryMB(Long freeMemoryMB) {
        this.freeMemoryMB = freeMemoryMB;
    }

    public String getJavaVersion() {
        return javaVersion;
    }

    public void setJavaVersion(String javaVersion) {
        this.javaVersion = javaVersion;
    }

    public String getJavaVendor() {
        return javaVendor;
    }

    public void setJavaVendor(String javaVendor) {
        this.javaVendor = javaVendor;
    }

    public Long getUptimeSeconds() {
        return uptimeSeconds;
    }

    public void setUptimeSeconds(Long uptimeSeconds) {
        this.uptimeSeconds = uptimeSeconds;
    }

    public Boolean getLiquibaseEnabled() {
        return liquibaseEnabled;
    }

    public void setLiquibaseEnabled(Boolean liquibaseEnabled) {
        this.liquibaseEnabled = liquibaseEnabled;
    }

    public Integer getLiquibaseChangeSets() {
        return liquibaseChangeSets;
    }

    public void setLiquibaseChangeSets(Integer liquibaseChangeSets) {
        this.liquibaseChangeSets = liquibaseChangeSets;
    }

    public Boolean getLiquibaseInitialized() {
        return liquibaseInitialized;
    }

    public void setLiquibaseInitialized(Boolean liquibaseInitialized) {
        this.liquibaseInitialized = liquibaseInitialized;
    }

    public String getLiquibaseLastExecuted() {
        return liquibaseLastExecuted;
    }

    public void setLiquibaseLastExecuted(String liquibaseLastExecuted) {
        this.liquibaseLastExecuted = liquibaseLastExecuted;
    }
}
