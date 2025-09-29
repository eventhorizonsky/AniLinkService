package xyz.ezsky.anilink.integration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import javax.sql.DataSource;
import java.io.InputStream;
import java.sql.DatabaseMetaData;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class LiquibaseTableCreationTest {

    @Autowired
    private DataSource dataSource;

    /**
     * 自动解析所有changelog yaml，提取所有createTable的表名，并校验这些表都已被Liquibase创建。
     */
    @Test
    void shouldHaveAllTablesDefinedInChangelog() throws Exception {
        Set<String> expectedTables = new HashSet<>();
        // 递归解析所有changelog yaml文件
        List<String> changelogFiles = List.of(
                "db/changelog/db.changelog-master.yaml",
                "db/changelog/common/db.changelog-common-init.yaml",
                "db/changelog/h2/db.changelog-h2-init.yaml",
                "db/changelog/pgsql/db.changelog-pgsql-init.yaml");
        Pattern tablePattern = Pattern.compile("tableName:\\s*([a-zA-Z0-9_\\-]+)");
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        for (String file : changelogFiles) {
            try (InputStream is = cl.getResourceAsStream(file)) {
                if (is == null)
                    continue;
                String content = new String(is.readAllBytes());
                Matcher matcher = tablePattern.matcher(content);
                while (matcher.find()) {
                    expectedTables.add(matcher.group(1).toLowerCase());
                }
            }
        }
        assertThat(expectedTables).isNotEmpty();
        Set<String> actualTables = new HashSet<>();
        try (var conn = dataSource.getConnection()) {
            DatabaseMetaData metaData = conn.getMetaData();
            try (var rs = metaData.getTables(null, null, "%", new String[] { "TABLE" })) {
                while (rs.next()) {
                    String tableName = rs.getString("TABLE_NAME");
                    if (!tableName.toLowerCase().contains("databasechangelog")) {
                        actualTables.add(tableName.toLowerCase());
                    }
                }
            }
        }
        System.out.println("[Liquibase表检查] changelog定义表: " + expectedTables);
        System.out.println("[Liquibase表检查] 实际数据库表: " + actualTables);
        for (String expected : expectedTables) {

            assertThat(actualTables)
                    .as("表[%s] 应该存在于数据库中", expected)
                    .contains(expected);
        }
    }
}
