package xyz.ezsky.anilink.integration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import javax.sql.DataSource;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class DataSourceIntegrationTest {

    @Autowired
    private DataSource dataSource;

    /**
     * 验证数据源配置是否可用，能否获取有效连接。
     */
    @Test
    void dataSourceShouldProvideValidConnection() throws Exception {
        assertThat(dataSource).isNotNull();
        try (java.sql.Connection conn = dataSource.getConnection()) {
            assertThat(conn.isValid(2)).isTrue();
        }
    }
}
