package xyz.ezsky.anilink.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;
import xyz.ezsky.anilink.model.vo.SystemInfoVO;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.OperatingSystemMXBean;
import java.net.InetAddress;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * 系统信息服务
 */
@Service
public class SystemInfoService {
    
    private final javax.sql.DataSource dataSource;
    private final Environment environment;
    
    @Autowired
    public SystemInfoService(javax.sql.DataSource dataSource, Environment environment) {
        this.dataSource = dataSource;
        this.environment = environment;
    }
    
    /**
     * 获取系统信息
     */
    public SystemInfoVO getSystemInfo() {
        // 获取操作系统信息
        OperatingSystemMXBean osBean = ManagementFactory.getOperatingSystemMXBean();
        String osName = System.getProperty("os.name");
        String osVersion = System.getProperty("os.version");
        String osArch = System.getProperty("os.arch");
        Integer availableProcessors = osBean.getAvailableProcessors();
        
        // 获取JVM内存信息
        Runtime runtime = Runtime.getRuntime();
        long maxMemory = runtime.maxMemory() / (1024 * 1024);
        long totalMemory = runtime.totalMemory() / (1024 * 1024);
        long freeMemory = runtime.freeMemory() / (1024 * 1024);
        
        // 获取Java信息
        String javaVersion = System.getProperty("java.version");
        String javaVendor = System.getProperty("java.vendor");
        
        // 获取系统运行时间
        long uptimeSeconds = ManagementFactory.getRuntimeMXBean().getUptime() / 1000;
        
        // 获取服务器IP和主机名
        String serverIp = getServerIp();
        String hostname = getHostname();
        
        // 获取数据库信息
        String[] dbInfo = getDatabaseInfo();
        String dbType = dbInfo[0];
        String dbVersion = dbInfo[1];
        
        // 获取Liquibase信息
        Boolean liquibaseEnabled = environment.getProperty("spring.liquibase.enabled", Boolean.class, true);
        Integer liquibaseChangeSets = 0;
        Boolean liquibaseInitialized = false;
        String liquibaseLastExecuted = null;
        
        if (liquibaseEnabled && dataSource != null) {
            try (Connection connection = dataSource.getConnection()) {
                // 检查databasechangelog表是否存在
                if (isTableExists(connection, "databasechangelog")) {
                    // 获取变更集数量
                    liquibaseChangeSets = getChangeSetCount(connection);
                    // 获取最后执行时间
                    liquibaseLastExecuted = getLastExecutedTime(connection);
                    // 检查是否有执行记录
                    liquibaseInitialized = liquibaseChangeSets > 0;
                } else {
                    liquibaseEnabled = false;
                }
            } catch (Exception e) {
                liquibaseEnabled = false;
            }
        } else {
            liquibaseEnabled = false;
        }
        
        return new SystemInfoVO(
            dbType, dbVersion, serverIp, hostname,
            osName, osVersion, osArch,
            availableProcessors, maxMemory, totalMemory, freeMemory,
            javaVersion, javaVendor, uptimeSeconds,
            liquibaseEnabled, liquibaseChangeSets, liquibaseInitialized, liquibaseLastExecuted
        );
    }
    
    /**
     * 检查表是否存在
     */
    private boolean isTableExists(Connection connection, String tableName) throws Exception {
        java.sql.DatabaseMetaData metaData = connection.getMetaData();
        try (ResultSet rs = metaData.getTables(null, null, tableName.toUpperCase(), null)) {
            return rs.next();
        }
    }
    
    /**
     * 获取变更集数量
     */
    private int getChangeSetCount(Connection connection) throws Exception {
        String sql = "SELECT COUNT(*) FROM databasechangelog";
        try (PreparedStatement stmt = connection.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            if (rs.next()) {
                return rs.getInt(1);
            }
        }
        return 0;
    }
    
    /**
     * 获取最后执行时间
     */
    private String getLastExecutedTime(Connection connection) throws Exception {
        String sql = "SELECT MAX(dateexecuted) FROM databasechangelog";
        try (PreparedStatement stmt = connection.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            if (rs.next()) {
                java.sql.Timestamp timestamp = rs.getTimestamp(1);
                if (timestamp != null) {
                    SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
                    return sdf.format(new Date(timestamp.getTime()));
                }
            }
        }
        return null;
    }
    
    /**
     * 获取服务器IP地址
     */
    private String getServerIp() {
        try {
            return InetAddress.getLocalHost().getHostAddress();
        } catch (Exception e) {
            return "未知";
        }
    }
    
    /**
     * 获取主机名
     */
    private String getHostname() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (Exception e) {
            return "未知";
        }
    }
    
    /**
     * 获取数据库信息
     */
    private String[] getDatabaseInfo() {
        try {
            if (dataSource == null) {
                return new String[]{"未知", "未知"};
            }
            
            String className = dataSource.getClass().getName();
            String dbType = "未知";
            String dbVersion = "未知";
            
            try (Connection connection = dataSource.getConnection()) {
                String url = connection.getMetaData().getURL();
                
                if (url != null) {
                    if (url.contains("mysql")) {
                        dbType = "MySQL";
                        try {
                            java.sql.DatabaseMetaData metaData = connection.getMetaData();
                            dbVersion = metaData.getDatabaseProductVersion();
                        } catch (Exception ignored) {}
                    } else if (url.contains("postgresql")) {
                        dbType = "PostgreSQL";
                        try {
                            java.sql.DatabaseMetaData metaData = connection.getMetaData();
                            dbVersion = metaData.getDatabaseProductVersion();
                        } catch (Exception ignored) {}
                    } else if (url.contains("h2")) {
                        dbType = "H2";
                        try {
                            java.sql.DatabaseMetaData metaData = connection.getMetaData();
                            dbVersion = metaData.getDatabaseProductVersion();
                        } catch (Exception ignored) {}
                    } else if (url.contains("oracle")) {
                        dbType = "Oracle";
                        try {
                            java.sql.DatabaseMetaData metaData = connection.getMetaData();
                            dbVersion = metaData.getDatabaseProductVersion();
                        } catch (Exception ignored) {}
                    } else if (url.contains("sqlserver")) {
                        dbType = "SQL Server";
                        try {
                            java.sql.DatabaseMetaData metaData = connection.getMetaData();
                            dbVersion = metaData.getDatabaseProductVersion();
                        } catch (Exception ignored) {}
                    } else if (url.contains("sqlite")) {
                        dbType = "SQLite";
                        try {
                            java.sql.DatabaseMetaData metaData = connection.getMetaData();
                            dbVersion = metaData.getDatabaseProductVersion();
                        } catch (Exception ignored) {}
                    }
                }
            }
            
            return new String[]{dbType, dbVersion};
        } catch (Exception e) {
            return new String[]{"获取失败", "获取失败"};
        }
    }
}
