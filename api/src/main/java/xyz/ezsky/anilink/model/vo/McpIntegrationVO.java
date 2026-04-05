package xyz.ezsky.anilink.model.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * 后台「MCP 接入」页展示：API Key 与客户端一键配置 JSON
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class McpIntegrationVO {

    private String apiKey;

    /** 完整 MCP HTTP 地址，如 https://example.com/api/v1/mcp */
    private String mcpUrl;

    /**
     * 与 Cursor 等客户端兼容的结构：transport + url + headers
     */
    private Map<String, Object> clientConfig;

    /** clientConfig 的格式化 JSON 字符串，便于整段复制 */
    private String clientConfigJson;
}
