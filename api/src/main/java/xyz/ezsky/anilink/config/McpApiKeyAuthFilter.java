package xyz.ezsky.anilink.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import xyz.ezsky.anilink.model.entity.User;
import xyz.ezsky.anilink.repository.UserRepository;
import xyz.ezsky.anilink.service.RoleInterfaceImpl;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

/**
 * MCP Streamable HTTP 端点使用 X-API-KEY 识别用户（与登录 Cookie 无关）
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 30)
public class McpApiKeyAuthFilter extends OncePerRequestFilter {

    public static final String ATTR_USER_ID = "mcpUserId";
    public static final String ATTR_ROLE_CODES = "mcpRoleCodes";

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleInterfaceImpl roleInterfaceImpl;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = stripContextPath(request);
        return !path.startsWith("/api/v1/mcp");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String key = request.getHeader("X-API-KEY");
        if (key == null || key.isBlank()) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"error\":\"missing_api_key\",\"message\":\"需要请求头 X-API-KEY\"}");
            return;
        }
        Optional<User> userOpt = userRepository.findByMcpApiKey(key.trim());
        if (userOpt.isEmpty() || !Boolean.TRUE.equals(userOpt.get().getIsActive())) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"error\":\"invalid_api_key\",\"message\":\"无效的 MCP API Key\"}");
            return;
        }
        User user = userOpt.get();
        List<String> roleCodes = roleInterfaceImpl.getRoleList(user.getId(), "login");
        request.setAttribute(ATTR_USER_ID, user.getId());
        request.setAttribute(ATTR_ROLE_CODES, roleCodes);
        filterChain.doFilter(request, response);
    }

    private static String stripContextPath(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String ctx = request.getContextPath();
        if (ctx != null && !ctx.isEmpty() && uri.startsWith(ctx)) {
            return uri.substring(ctx.length());
        }
        return uri;
    }
}
