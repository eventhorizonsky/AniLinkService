package xyz.ezsky.anilink.controller;

import cn.dev33.satoken.annotation.SaCheckRole;
import cn.dev33.satoken.stp.StpUtil;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import xyz.ezsky.anilink.model.vo.ApiResponseVO;
import xyz.ezsky.anilink.model.vo.McpIntegrationVO;
import xyz.ezsky.anilink.service.SiteConfigService;
import xyz.ezsky.anilink.service.UserService;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/mcp")
@Tag(name = "MCP 接入", description = "管理员查看与重置 MCP API Key、复制客户端配置")
public class McpAccessController {

    public static final String MCP_PATH = "/api/v1/mcp";

    @Autowired
    private UserService userService;

    @Autowired
    private SiteConfigService siteConfigService;

    @Autowired
    private ObjectMapper objectMapper;

    @GetMapping("/config")
    @SaCheckRole("super-admin")
    @Operation(summary = "获取 MCP 接入配置", description = "返回当前超级管理员账号绑定的 API Key 与可复制的客户端 JSON")
    public ApiResponseVO<McpIntegrationVO> getConfig(HttpServletRequest request) throws JsonProcessingException {
        Long userId = StpUtil.getLoginIdAsLong();
        String apiKey = userService.getOrCreateMcpApiKey(userId);
        String base = resolvePublicBaseUrl(request);
        String mcpUrl = joinUrl(base, MCP_PATH);
        McpIntegrationVO vo = buildVo(apiKey, mcpUrl);
        return ApiResponseVO.success(vo);
    }

    @PostMapping("/config/regenerate")
    @SaCheckRole("super-admin")
    @Operation(summary = "重置 MCP API Key", description = "使旧 Key 立即失效，并返回新配置")
    public ApiResponseVO<McpIntegrationVO> regenerate(HttpServletRequest request) throws JsonProcessingException {
        Long userId = StpUtil.getLoginIdAsLong();
        String apiKey = userService.regenerateMcpApiKey(userId);
        String base = resolvePublicBaseUrl(request);
        String mcpUrl = joinUrl(base, MCP_PATH);
        McpIntegrationVO vo = buildVo(apiKey, mcpUrl);
        return ApiResponseVO.success(vo, "已重置 MCP API Key");
    }

    private McpIntegrationVO buildVo(String apiKey, String mcpUrl) throws JsonProcessingException {
        Map<String, Object> headers = new LinkedHashMap<>();
        headers.put("X-API-KEY", apiKey);
        Map<String, Object> clientConfig = new LinkedHashMap<>();
        clientConfig.put("transport", "streamable_http");
        clientConfig.put("url", mcpUrl);
        clientConfig.put("headers", headers);
        String json = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(clientConfig);
        return McpIntegrationVO.builder()
                .apiKey(apiKey)
                .mcpUrl(mcpUrl)
                .clientConfig(clientConfig)
                .clientConfigJson(json)
                .build();
    }

    private String resolvePublicBaseUrl(HttpServletRequest request) {
        return siteConfigService.getConfiguredSiteUrlOptional()
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(this::trimTrailingSlash)
                .orElseGet(() -> inferBaseUrlFromRequest(request));
    }

    private String inferBaseUrlFromRequest(HttpServletRequest request) {
        String scheme = request.getHeader("X-Forwarded-Proto");
        if (scheme == null || scheme.isBlank()) {
            scheme = request.getScheme();
        }
        String host = request.getHeader("X-Forwarded-Host");
        if (host == null || host.isBlank()) {
            host = request.getServerName();
        }
        int port = request.getServerPort();
        boolean defaultPort = ("http".equalsIgnoreCase(scheme) && port == 80)
                || ("https".equalsIgnoreCase(scheme) && port == 443);
        if (defaultPort || host.contains(":")) {
            return scheme + "://" + host;
        }
        if (("http".equalsIgnoreCase(scheme) && port != 80)
                || ("https".equalsIgnoreCase(scheme) && port != 443)) {
            return scheme + "://" + host + ":" + port;
        }
        return scheme + "://" + host;
    }

    private String trimTrailingSlash(String url) {
        if (url.endsWith("/")) {
            return url.substring(0, url.length() - 1);
        }
        return url;
    }

    private String joinUrl(String base, String path) {
        if (path.startsWith("/")) {
            return base + path;
        }
        return base + "/" + path;
    }
}
