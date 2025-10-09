package xyz.ezsky.anilink;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(classes = AniLinkServiceApplication.class)
/**
 * 基础集成测试：仅验证Spring上下文能否正常加载。
 */
class AniLinkServiceApplicationTests {
    @Test
    void contextLoads() {
        // Spring Boot 应用上下文能否正常加载
        System.out.println("Context loads successfully");
    }
}
