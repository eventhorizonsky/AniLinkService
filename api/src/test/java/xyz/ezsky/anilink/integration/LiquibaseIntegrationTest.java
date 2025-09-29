package xyz.ezsky.anilink.integration;

import liquibase.integration.spring.SpringLiquibase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class LiquibaseIntegrationTest {

    @Autowired(required = false)
    private SpringLiquibase liquibase;

    /**
     * 验证Liquibase配置是否注入且changelog路径有效。
     */
    @Test
    void liquibaseShouldBeConfigured() {
        assertThat(liquibase).isNotNull();
        assertThat(liquibase.getChangeLog()).isNotEmpty();
    }
}
