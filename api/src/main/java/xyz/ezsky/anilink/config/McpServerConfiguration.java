package xyz.ezsky.anilink.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.common.McpTransportContext;
import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.json.jackson2.JacksonMcpJsonMapper;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.server.McpTransportContextExtractor;
import io.modelcontextprotocol.server.transport.HttpServletStreamableServerTransportProvider;
import io.modelcontextprotocol.spec.McpSchema;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import xyz.ezsky.anilink.controller.McpAccessController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Configuration
public class McpServerConfiguration {

    @Bean
    public McpJsonMapper mcpJsonMapper(ObjectMapper objectMapper) {
        return new JacksonMcpJsonMapper(objectMapper);
    }

    @Bean
    public HttpServletStreamableServerTransportProvider mcpStreamableTransportProvider(McpJsonMapper mcpJsonMapper) {
        McpTransportContextExtractor<HttpServletRequest> extractor = request -> {
            Object uid = request.getAttribute(McpApiKeyAuthFilter.ATTR_USER_ID);
            if (uid == null) {
                return McpTransportContext.EMPTY;
            }
            Map<String, Object> meta = new HashMap<>();
            meta.put("userId", uid);
            Object roles = request.getAttribute(McpApiKeyAuthFilter.ATTR_ROLE_CODES);
            meta.put("roleCodes", roles != null ? roles : List.of());
            return McpTransportContext.create(meta);
        };
        return HttpServletStreamableServerTransportProvider.builder()
                .jsonMapper(mcpJsonMapper)
                .mcpEndpoint(McpAccessController.MCP_PATH)
                .contextExtractor(extractor)
                .build();
    }

    @Bean(destroyMethod = "closeGracefully")
    public McpSyncServer mcpSyncServer(
            HttpServletStreamableServerTransportProvider mcpStreamableTransportProvider,
            McpJsonMapper mcpJsonMapper,
            AniLinkMcpToolsFactory aniLinkMcpToolsFactory) {
        return McpServer.sync(mcpStreamableTransportProvider)
                .serverInfo(new McpSchema.Implementation("anilink-service", "1.0.0"))
                .capabilities(McpSchema.ServerCapabilities.builder()
                        .tools(false)
                        .build())
                .instructions("""
                        AniLinkService：本地追番 NAS。请按「场景」选工具，勿混淆「本地已入库内容」与「资源站搜索下载」。

                        【权限】描述中含「需超级管理员」的工具仅当该 MCP API Key 对应账号为 super-admin 可用。其余工具以 Key 绑定用户为准（追番、播放记录、消息、Bangumi 等）。

                        【本地库 / 已扫描媒体】用户问「我硬盘/NAS 里有哪些番、某部有几集」→ anilink_list_animes、anilink_get_anime、anilink_list_episodes。当季时间表 → anilink_get_shin_schedule。

                        【弹弹：匹剧集 / 弹幕】要给视频或标题对齐弹弹剧集、拉弹幕 JSON → 先 anilink_dandan_search_episodes（anime/episode/tmdbId 至少其一），再用结果里的信息调用 anilink_dandan_get_comment(episodeId)。不要用资源搜索工具做弹幕匹配。

                        【Bangumi】条目短评/吐槽箱 → anilink_bangumi_subject_comments(subjectId)。查询或写入个人收藏/评分 → 须用户已在网页绑定 Bangumi：anilink_bangumi_get_collection、anilink_bangumi_save_collection。

                        【追番 / 续播 / 站内信】追番列表与状态 → anilink_follows_*、anilink_follow_add/remove/update_status。续播与写进度 → anilink_play_resume、anilink_play_progress_update。通知 → anilink_messages_*。

                        【资源搜索 + 磁链下载 — 必须按顺序】要找动漫资源并下载到服务器媒体库：
                        1）可选：anilink_resource_subgroups、anilink_resource_types 看筛选枚举；
                        2）必做：anilink_resource_search 用 keyword（及可选 subgroup/type）拿到候选与 magnet；
                        3）必做：anilink_resource_download_start 使用上一步返回的 magnet、标题等字段，并指定 libraryId（目标媒体库）；
                        4）跟进：anilink_resource_download_tasks 查状态；失败可 anilink_resource_download_retry，取消用 cancel，清理记录用 delete。
                        不要跳过 search 直接编造 magnet；libraryId 可先 anilink_media_libraries 列出。

                        【RSS 自动收种】管理订阅 anilink_rss_list/create/delete；手动拉取 anilink_rss_trigger；排查抓取 anilink_rss_last_content。

                        【媒体库运维】扫盘 anilink_media_scan / scan_all；整库重匹 anilink_media_rematch_library；查文件 anilink_media_files；单文件重匹 anilink_media_file_rematch；队列与进度 anilink_queue_metadata_status、anilink_match_progress、anilink_metadata_progress。
                        """.stripIndent())
                .jsonMapper(mcpJsonMapper)
                .tools(aniLinkMcpToolsFactory.buildToolSpecifications())
                .build();
    }

    @Bean
    @DependsOn("mcpSyncServer")
    public ServletRegistrationBean<HttpServletStreamableServerTransportProvider> mcpServletRegistration(
            HttpServletStreamableServerTransportProvider mcpStreamableTransportProvider) {
        ServletRegistrationBean<HttpServletStreamableServerTransportProvider> reg =
                new ServletRegistrationBean<>(mcpStreamableTransportProvider, McpAccessController.MCP_PATH);
        reg.setLoadOnStartup(1);
        reg.setAsyncSupported(true);
        reg.setName("mcpStreamableHttp");
        return reg;
    }
}
