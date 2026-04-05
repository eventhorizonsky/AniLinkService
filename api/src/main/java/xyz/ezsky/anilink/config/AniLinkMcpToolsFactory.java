package xyz.ezsky.anilink.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.server.McpSyncServerExchange;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import xyz.ezsky.anilink.model.dto.AnimeFollowDTO;
import xyz.ezsky.anilink.model.dto.PlayHistoryDTO;
import xyz.ezsky.anilink.model.dto.ResourceRssSubscriptionRequest;
import xyz.ezsky.anilink.model.dto.ResourceSearchDownloadRequest;
import xyz.ezsky.anilink.model.dto.UpdateBangumiCollectionRequest;
import xyz.ezsky.anilink.model.entity.User;
import xyz.ezsky.anilink.model.vo.QueueStatusVO;
import xyz.ezsky.anilink.service.AnimeFollowService;
import xyz.ezsky.anilink.service.AnimeService;
import xyz.ezsky.anilink.service.BangumiApiService;
import xyz.ezsky.anilink.service.DanmakuService;
import xyz.ezsky.anilink.service.MediaFileService;
import xyz.ezsky.anilink.service.MediaLibraryService;
import xyz.ezsky.anilink.service.MediaMatchQueueManager;
import xyz.ezsky.anilink.service.MediaMetadataQueueManager;
import xyz.ezsky.anilink.service.MessageService;
import xyz.ezsky.anilink.service.PlayHistoryService;
import xyz.ezsky.anilink.service.ResourceDownloadService;
import xyz.ezsky.anilink.service.ResourceRssSubscriptionService;
import xyz.ezsky.anilink.service.ResourceSearchProxyService;
import xyz.ezsky.anilink.service.UserService;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * MCP Tools：对应能力集 1（库/弹弹）、3（Bangumi）、5（追番/进度/消息）、6（资源/RSS）、7（媒体库运维）
 */
@Component
public class AniLinkMcpToolsFactory {

    private static final String SCHEMA_EMPTY_OBJECT = """
            {"type":"object","properties":{}}
            """;

    @Autowired
    private McpJsonMapper mcpJsonMapper;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AnimeService animeService;

    @Autowired
    private DanmakuService danmakuService;

    @Autowired
    private BangumiApiService bangumiApiService;

    @Autowired
    private UserService userService;

    @Autowired
    private PlayHistoryService playHistoryService;

    @Autowired
    private AnimeFollowService animeFollowService;

    @Autowired
    private MessageService messageService;

    @Autowired
    private ResourceSearchProxyService resourceSearchProxyService;

    @Autowired
    private ResourceDownloadService resourceDownloadService;

    @Autowired
    private ResourceRssSubscriptionService resourceRssSubscriptionService;

    @Autowired
    private MediaLibraryService mediaLibraryService;

    @Autowired
    private MediaMatchQueueManager mediaMatchQueueManager;

    @Autowired
    private MediaFileService mediaFileService;

    @Autowired
    private MediaMetadataQueueManager mediaMetadataQueueManager;

    public List<McpServerFeatures.SyncToolSpecification> buildToolSpecifications() {
        List<McpServerFeatures.SyncToolSpecification> specs = new ArrayList<>();
        specs.addAll(libraryAndDandanTools());
        specs.addAll(bangumiTools());
        specs.addAll(userStateTools());
        specs.addAll(resourceTools());
        specs.addAll(mediaAdminTools());
        return specs;
    }

    private List<McpServerFeatures.SyncToolSpecification> libraryAndDandanTools() {
        List<McpServerFeatures.SyncToolSpecification> list = new ArrayList<>();
        list.add(McpServerFeatures.SyncToolSpecification.builder()
                .tool(tool("anilink_list_animes", """
                        【场景】查 NAS/媒体库里「已经扫描并入库」的番剧目录，不是资源站下载列表。【下一步】点进某部用 anilink_get_anime；列剧集文件用 anilink_list_episodes。
                        【参数】keyword 按标题模糊搜；分页 page 从 1 开始。
                        """.stripIndent().trim(),
                        """
                        {"type":"object","properties":{
                        "page":{"type":"integer","description":"页码，从1开始，默认1"},
                        "pageSize":{"type":"integer","description":"每页条数，默认20"},
                        "keyword":{"type":"string","description":"标题模糊搜索，可选"}
                        }}
                        """))
                .callHandler((ex, req) -> {
                    Map<String, Object> a = req.arguments();
                    int page = intArg(a, "page", 1);
                    int pageSize = intArg(a, "pageSize", 20);
                    String keyword = strArg(a, "keyword");
                    return jsonOk(animeService.getAnimesPage(page, pageSize, keyword));
                })
                .build());

        list.add(McpServerFeatures.SyncToolSpecification.builder()
                .tool(tool("anilink_get_anime", """
                        【场景】已知弹幕库 animeId，查该番的元数据/海报等。【前置】animeId 通常来自 anilink_list_animes 或站内详情。【下一步】列本地已入库剧集 anilink_list_episodes。
                        """.stripIndent().trim(),
                        """
                        {"type":"object","required":["animeId"],"properties":{
                        "animeId":{"type":"integer","description":"弹幕库动漫 ID"}
                        }}
                        """))
                .callHandler((ex, req) -> {
                    Long animeId = longArg(req.arguments(), "animeId", null);
                    if (animeId == null) {
                        return err("animeId 必填");
                    }
                    var vo = animeService.getAnimeById(animeId);
                    return vo == null ? err("番剧不存在") : jsonOk(vo);
                })
                .build());

        list.add(McpServerFeatures.SyncToolSpecification.builder()
                .tool(tool("anilink_list_episodes", """
                        【场景】某部番在磁盘上已有哪些视频文件、对应哪一集（本地已匹配结果）。【前置】animeId 来自 anilink_get_anime / list_animes。
                        【非场景】不是搜磁链；下载新资源用 resource_search → download_start。
                        """,
                        """
                        {"type":"object","required":["animeId"],"properties":{
                        "animeId":{"type":"integer"},
                        "page":{"type":"integer","description":"默认1"},
                        "pageSize":{"type":"integer","description":"默认20"}
                        }}
                        """))
                .callHandler((ex, req) -> {
                    Map<String, Object> a = req.arguments();
                    Long animeId = longArg(a, "animeId", null);
                    if (animeId == null) {
                        return err("animeId 必填");
                    }
                    int page = intArg(a, "page", 1);
                    int pageSize = intArg(a, "pageSize", 20);
                    return jsonOk(animeService.getEpisodesByAnimeId(animeId, page, pageSize));
                })
                .build());

        list.add(McpServerFeatures.SyncToolSpecification.builder()
                .tool(tool("anilink_get_shin_schedule", """
                        【场景】当季/新番时间表（弹弹源，服务端缓存 JSON）。【非场景】不是本地文件列表；本地番剧用 anilink_list_animes。
                        """, SCHEMA_EMPTY_OBJECT))
                .callHandler((ex, req) -> {
                    String raw = animeService.getShinRawJson();
                    if (raw == null) {
                        return err("暂无新番缓存数据");
                    }
                    try {
                        return jsonOk(objectMapper.readTree(raw));
                    } catch (Exception e) {
                        return err("解析新番 JSON 失败: " + e.getMessage());
                    }
                })
                .build());

        list.add(McpServerFeatures.SyncToolSpecification.builder()
                .tool(tool("anilink_dandan_search_episodes", """
                        【场景】按番名/话数/tmdb 找弹弹侧的剧集候选，用于对齐 episodeId、配弹幕。【前置】anime、episode、tmdbId 至少填一个。
                        【下一步】拿到 episodeId 后调用 anilink_dandan_get_comment。【勿用】不要用 anilink_resource_search 做弹幕匹配。
                        """,
                        """
                        {"type":"object","properties":{
                        "anime":{"type":"string","description":"番剧标题关键词"},
                        "episode":{"type":"string","description":"剧集关键词"},
                        "tmdbId":{"type":"string","description":"TMDB ID"}
                        }}
                        """))
                .callHandler((ex, req) -> {
                    Map<String, Object> a = req.arguments();
                    String anime = strArg(a, "anime");
                    String episode = strArg(a, "episode");
                    String tmdbId = strArg(a, "tmdbId");
                    try {
                        String raw = danmakuService.searchEpisodes(anime, episode, tmdbId);
                        if (raw == null) {
                            return err("未找到匹配结果");
                        }
                        return jsonOk(objectMapper.readTree(raw));
                    } catch (IllegalArgumentException e) {
                        return err(e.getMessage());
                    } catch (Exception e) {
                        return err("搜索失败: " + e.getMessage());
                    }
                })
                .build());

        list.add(McpServerFeatures.SyncToolSpecification.builder()
                .tool(tool("anilink_dandan_get_comment", """
                        【场景】拉取某弹弹 episodeId 的弹幕数据（JSON，带缓存）。【前置】episodeId 通常来自 anilink_dandan_search_episodes。
                        【参数】withRelated=true 可含第三方关联弹幕。
                        """,
                        """
                        {"type":"object","required":["episodeId"],"properties":{
                        "episodeId":{"type":"integer","description":"弹幕库剧集 ID"},
                        "withRelated":{"type":"boolean","description":"是否包含第三方关联弹幕，默认 false"}
                        }}
                        """))
                .callHandler((ex, req) -> {
                    Map<String, Object> a = req.arguments();
                    Long episodeId = longArg(a, "episodeId", null);
                    if (episodeId == null) {
                        return err("episodeId 必填");
                    }
                    boolean withRelated = boolArg(a, "withRelated", false);
                    String raw = danmakuService.getCommentByEpisodeId(episodeId, withRelated);
                    if (raw == null) {
                        return err("弹幕数据不存在");
                    }
                    try {
                        return jsonOk(objectMapper.readTree(raw));
                    } catch (Exception e) {
                        return err("解析弹幕 JSON 失败: " + e.getMessage());
                    }
                })
                .build());

        return list;
    }

    private List<McpServerFeatures.SyncToolSpecification> bangumiTools() {
        List<McpServerFeatures.SyncToolSpecification> list = new ArrayList<>();
        list.add(McpServerFeatures.SyncToolSpecification.builder()
                .tool(tool("anilink_bangumi_subject_comments", "获取 Bangumi 条目短评/吐槽箱",
                        """
                        {"type":"object","required":["subjectId"],"properties":{
                        "subjectId":{"type":"integer"},
                        "limit":{"type":"integer","description":"1-50，默认20"},
                        "offset":{"type":"integer","description":"默认0"}
                        }}
                        """))
                .callHandler((ex, req) -> {
                    Map<String, Object> a = req.arguments();
                    Long subjectId = longArg(a, "subjectId", null);
                    if (subjectId == null) {
                        return err("subjectId 必填");
                    }
                    int limit = intArg(a, "limit", 20);
                    int offset = intArg(a, "offset", 0);
                    if (limit < 1 || limit > 50) {
                        limit = 20;
                    }
                    if (offset < 0) {
                        offset = 0;
                    }
                    String json = bangumiApiService.getSubjectComments(subjectId, limit, offset);
                    if (!StringUtils.hasText(json)) {
                        return err("获取评论失败");
                    }
                    try {
                        return jsonOk(objectMapper.readTree(json));
                    } catch (Exception e) {
                        return err("解析评论 JSON 失败");
                    }
                })
                .build());

        list.add(McpServerFeatures.SyncToolSpecification.builder()
                .tool(tool("anilink_bangumi_get_collection", "当前 MCP 用户已绑定 Bangumi 时，查询对某条目的收藏/评分",
                        """
                        {"type":"object","required":["subjectId"],"properties":{
                        "subjectId":{"type":"integer"}
                        }}
                        """))
                .callHandler((ex, req) -> {
                    Long userId = userIdFrom(ex);
                    if (userId == null) {
                        return err("缺少用户上下文");
                    }
                    Long subjectId = longArg(req.arguments(), "subjectId", null);
                    if (subjectId == null) {
                        return err("subjectId 必填");
                    }
                    User user = userService.findById(userId).orElse(null);
                    if (user == null || !StringUtils.hasText(user.getBangumiAccessToken())
                            || !StringUtils.hasText(user.getBangumiUsername())) {
                        return err("请先在网页端绑定 Bangumi 账号");
                    }
                    ResponseEntity<String> response = bangumiApiService.getUserCollection(
                            user.getBangumiAccessToken(), user.getBangumiUsername(), subjectId);
                    if (response.getStatusCode().value() == 401) {
                        return err("Bangumi Token 无效或过期");
                    }
                    if (response.getStatusCode().value() == 404) {
                        return err("当前未记录 Bangumi 收藏");
                    }
                    if (!response.getStatusCode().is2xxSuccessful() || !StringUtils.hasText(response.getBody())) {
                        return err("获取 Bangumi 收藏失败");
                    }
                    try {
                        return jsonOk(objectMapper.readTree(response.getBody()));
                    } catch (Exception e) {
                        return err("解析收藏 JSON 失败");
                    }
                })
                .build());

        list.add(McpServerFeatures.SyncToolSpecification.builder()
                .tool(tool("anilink_bangumi_save_collection", "已绑定 Bangumi 时，写入或更新条目收藏/评分/短评",
                        """
                        {"type":"object","required":["subjectId"],"properties":{
                        "subjectId":{"type":"integer"},
                        "type":{"type":"integer","description":"收藏类型 1-5，可选"},
                        "rate":{"type":"integer","description":"评分 0-10，可选"},
                        "comment":{"type":"string","description":"短评，可选"},
                        "privateCollection":{"type":"boolean","description":"是否私密收藏，可选"}
                        }}
                        """))
                .callHandler((ex, req) -> {
                    Long userId = userIdFrom(ex);
                    if (userId == null) {
                        return err("缺少用户上下文");
                    }
                    Map<String, Object> a = req.arguments();
                    Long subjectId = longArg(a, "subjectId", null);
                    if (subjectId == null) {
                        return err("subjectId 必填");
                    }
                    User user = userService.findById(userId).orElse(null);
                    if (user == null || !StringUtils.hasText(user.getBangumiAccessToken())) {
                        return err("请先在网页端绑定 Bangumi 账号");
                    }
                    UpdateBangumiCollectionRequest u = new UpdateBangumiCollectionRequest();
                    if (a.get("type") != null) {
                        u.setType(((Number) a.get("type")).intValue());
                    }
                    if (a.get("rate") != null) {
                        u.setRate(((Number) a.get("rate")).intValue());
                    }
                    if (a.get("comment") != null) {
                        u.setComment(String.valueOf(a.get("comment")));
                    }
                    if (a.get("privateCollection") != null) {
                        u.setPrivateCollection(Boolean.TRUE.equals(a.get("privateCollection")));
                    }
                    if (u.getType() == null && u.getRate() == null && u.getComment() == null
                            && u.getPrivateCollection() == null) {
                        return err("请至少提供 type、rate、comment、privateCollection 中的一项");
                    }
                    if (u.getRate() != null && (u.getRate() < 0 || u.getRate() > 10)) {
                        return err("评分范围 0-10");
                    }
                    if (u.getType() != null && (u.getType() < 1 || u.getType() > 5)) {
                        return err("收藏类型不合法");
                    }
                    ObjectNode payload = objectMapper.createObjectNode();
                    if (u.getType() != null) {
                        payload.put("type", u.getType());
                    }
                    if (u.getRate() != null) {
                        payload.put("rate", u.getRate());
                    }
                    if (u.getComment() != null) {
                        payload.put("comment", u.getComment());
                    }
                    if (u.getPrivateCollection() != null) {
                        payload.put("private", u.getPrivateCollection());
                    }
                    ResponseEntity<String> response = bangumiApiService.postUserCollection(
                            user.getBangumiAccessToken(), subjectId, payload.toString());
                    if (response.getStatusCode().value() == 401) {
                        return err("Bangumi Token 无效或过期");
                    }
                    if (!response.getStatusCode().is2xxSuccessful()) {
                        return err("提交 Bangumi 收藏失败");
                    }
                    return jsonOk(Map.of("ok", true, "message", "已同步到 Bangumi"));
                })
                .build());

        return list;
    }

    private List<McpServerFeatures.SyncToolSpecification> userStateTools() {
        List<McpServerFeatures.SyncToolSpecification> list = new ArrayList<>();
        list.add(McpServerFeatures.SyncToolSpecification.builder()
                .tool(tool("anilink_play_history_list", "分页获取当前用户的播放历史",
                        """
                        {"type":"object","properties":{
                        "page":{"type":"integer","description":"默认1"},
                        "pageSize":{"type":"integer","description":"默认20"}
                        }}
                        """))
                .callHandler((ex, req) -> {
                    Long userId = userIdFrom(ex);
                    if (userId == null) {
                        return err("缺少用户上下文");
                    }
                    Map<String, Object> a = req.arguments();
                    return jsonOk(playHistoryService.getUserPlayHistory(userId, intArg(a, "page", 1),
                            intArg(a, "pageSize", 20)));
                })
                .build());

        list.add(McpServerFeatures.SyncToolSpecification.builder()
                .tool(tool("anilink_play_progress_update", "更新播放进度（字段与站内 API 一致）",
                        """
                        {"type":"object","required":["animeId"],"properties":{
                        "animeId":{"type":"integer"},
                        "videoId":{"type":"integer"},
                        "videoName":{"type":"string"},
                        "animeTitle":{"type":"string"},
                        "episodeId":{"type":"string"},
                        "progressSeconds":{"type":"integer"},
                        "durationSeconds":{"type":"integer"},
                        "isCompleted":{"type":"boolean"}
                        }}
                        """))
                .callHandler((ex, req) -> {
                    Long userId = userIdFrom(ex);
                    if (userId == null) {
                        return err("缺少用户上下文");
                    }
                    Map<String, Object> a = req.arguments();
                    Long animeId = longArg(a, "animeId", null);
                    if (animeId == null) {
                        return err("animeId 必填");
                    }
                    Boolean completed = null;
                    if (a.get("isCompleted") instanceof Boolean b) {
                        completed = b;
                    }
                    PlayHistoryDTO dto = PlayHistoryDTO.builder()
                            .animeId(animeId)
                            .videoId(longArg(a, "videoId", null))
                            .videoName(strArg(a, "videoName"))
                            .animeTitle(strArg(a, "animeTitle"))
                            .episodeId(strArg(a, "episodeId"))
                            .progressSeconds(longArg(a, "progressSeconds", null))
                            .durationSeconds(longArg(a, "durationSeconds", null))
                            .isCompleted(completed)
                            .build();
                    return jsonOk(playHistoryService.updatePlayProgress(userId, dto));
                })
                .build());

        list.add(McpServerFeatures.SyncToolSpecification.builder()
                .tool(tool("anilink_play_resume", "获取某番续播信息（无记录时 data 为 null）",
                        """
                        {"type":"object","required":["animeId"],"properties":{
                        "animeId":{"type":"integer"}
                        }}
                        """))
                .callHandler((ex, req) -> {
                    Long userId = userIdFrom(ex);
                    if (userId == null) {
                        return err("缺少用户上下文");
                    }
                    Long animeId = longArg(req.arguments(), "animeId", null);
                    if (animeId == null) {
                        return err("animeId 必填");
                    }
                    return jsonOk(playHistoryService.getAnimePlayProgress(userId, animeId));
                })
                .build());

        list.add(McpServerFeatures.SyncToolSpecification.builder()
                .tool(tool("anilink_follows_list", "分页获取追番列表",
                        """
                        {"type":"object","properties":{
                        "page":{"type":"integer","description":"默认1"},
                        "pageSize":{"type":"integer","description":"默认20"}
                        }}
                        """))
                .callHandler((ex, req) -> {
                    Long userId = userIdFrom(ex);
                    if (userId == null) {
                        return err("缺少用户上下文");
                    }
                    Map<String, Object> a = req.arguments();
                    return jsonOk(animeFollowService.getUserFollows(userId, intArg(a, "page", 1),
                            intArg(a, "pageSize", 20)));
                })
                .build());

        list.add(McpServerFeatures.SyncToolSpecification.builder()
                .tool(tool("anilink_follows_all", "获取全部追番（不分页）", SCHEMA_EMPTY_OBJECT))
                .callHandler((ex, req) -> {
                    Long userId = userIdFrom(ex);
                    return userId == null ? err("缺少用户上下文")
                            : jsonOk(animeFollowService.getUserFollowsList(userId));
                })
                .build());

        list.add(McpServerFeatures.SyncToolSpecification.builder()
                .tool(tool("anilink_follow_add", "添加或更新追番",
                        """
                        {"type":"object","required":["animeId"],"properties":{
                        "animeId":{"type":"integer"},
                        "animeTitle":{"type":"string"},
                        "imageUrl":{"type":"string"},
                        "status":{"type":"string","description":"watching/completed/dropped"},
                        "tags":{"type":"string"}
                        }}
                        """))
                .callHandler((ex, req) -> {
                    Long userId = userIdFrom(ex);
                    if (userId == null) {
                        return err("缺少用户上下文");
                    }
                    Map<String, Object> a = req.arguments();
                    Long animeId = longArg(a, "animeId", null);
                    if (animeId == null) {
                        return err("animeId 必填");
                    }
                    AnimeFollowDTO dto = AnimeFollowDTO.builder()
                            .animeId(animeId)
                            .animeTitle(strArg(a, "animeTitle"))
                            .imageUrl(strArg(a, "imageUrl"))
                            .status(strArg(a, "status"))
                            .tags(strArg(a, "tags"))
                            .build();
                    return jsonOk(animeFollowService.followAnime(userId, dto));
                })
                .build());

        list.add(McpServerFeatures.SyncToolSpecification.builder()
                .tool(tool("anilink_follow_remove", "取消追番",
                        """
                        {"type":"object","required":["animeId"],"properties":{
                        "animeId":{"type":"integer"}
                        }}
                        """))
                .callHandler((ex, req) -> {
                    Long _uid = userIdFrom(ex);
                    if (_uid == null) {
                        return err("缺少用户上下文");
                    }
                    long userId = _uid;
                    Long animeId = longArg(req.arguments(), "animeId", null);
                    if (animeId == null) {
                        return err("animeId 必填");
                    }
                    return jsonOk(Map.of("removed", animeFollowService.unfollowAnime(userId, animeId)));
                })
                .build());

        list.add(McpServerFeatures.SyncToolSpecification.builder()
                .tool(tool("anilink_follow_update_status", "更新追番状态",
                        """
                        {"type":"object","required":["animeId","status"],"properties":{
                        "animeId":{"type":"integer"},
                        "status":{"type":"string","description":"watching/completed/dropped"}
                        }}
                        """))
                .callHandler((ex, req) -> {
                    Long _uid = userIdFrom(ex);
                    if (_uid == null) {
                        return err("缺少用户上下文");
                    }
                    long userId = _uid;
                    Map<String, Object> a = req.arguments();
                    Long animeId = longArg(a, "animeId", null);
                    String status = strArg(a, "status");
                    if (animeId == null || !StringUtils.hasText(status)) {
                        return err("animeId 与 status 必填");
                    }
                    var vo = animeFollowService.updateFollowStatus(userId, animeId, status.trim());
                    return vo == null ? err("追番记录不存在") : jsonOk(vo);
                })
                .build());

        list.add(McpServerFeatures.SyncToolSpecification.builder()
                .tool(tool("anilink_messages_list", "分页获取站内消息",
                        """
                        {"type":"object","properties":{
                        "page":{"type":"integer","description":"默认1"},
                        "pageSize":{"type":"integer","description":"默认20"}
                        }}
                        """))
                .callHandler((ex, req) -> {
                    Long _uid = userIdFrom(ex);
                    if (_uid == null) {
                        return err("缺少用户上下文");
                    }
                    long userId = _uid;
                    Map<String, Object> a = req.arguments();
                    return jsonOk(messageService.getUserMessages(userId, intArg(a, "page", 1),
                            intArg(a, "pageSize", 20)));
                })
                .build());

        list.add(McpServerFeatures.SyncToolSpecification.builder()
                .tool(tool("anilink_messages_unread", "未读消息列表", SCHEMA_EMPTY_OBJECT))
                .callHandler((ex, req) -> {
                    Long uid = userIdFrom(ex);
                    if (uid == null) {
                        return err("缺少用户上下文");
                    }
                    return jsonOk(messageService.getUserUnreadMessages(uid));
                })
                .build());

        list.add(McpServerFeatures.SyncToolSpecification.builder()
                .tool(tool("anilink_messages_unread_count", "未读消息数", SCHEMA_EMPTY_OBJECT))
                .callHandler((ex, req) -> {
                    Long uid = userIdFrom(ex);
                    if (uid == null) {
                        return err("缺少用户上下文");
                    }
                    return jsonOk(Map.of("unreadCount", messageService.getUnreadCount(uid)));
                })
                .build());

        list.add(McpServerFeatures.SyncToolSpecification.builder()
                .tool(tool("anilink_messages_mark_read", "将单条消息标为已读",
                        """
                        {"type":"object","required":["messageId"],"properties":{
                        "messageId":{"type":"integer"}
                        }}
                        """))
                .callHandler((ex, req) -> {
                    Long messageId = longArg(req.arguments(), "messageId", null);
                    if (messageId == null) {
                        return err("messageId 必填");
                    }
                    var vo = messageService.markMessageAsRead(messageId);
                    return vo == null ? err("消息不存在") : jsonOk(vo);
                })
                .build());

        return list;
    }

    private List<McpServerFeatures.SyncToolSpecification> resourceTools() {
        List<McpServerFeatures.SyncToolSpecification> list = new ArrayList<>();
        list.add(McpServerFeatures.SyncToolSpecification.builder()
                .tool(tool("anilink_resource_subgroups", "资源搜索：字幕组列表（需超级管理员）", SCHEMA_EMPTY_OBJECT))
                .callHandler((ex, req) -> requireSuper(ex, () -> jsonOk(resourceSearchProxyService.fetchSubgroups())))
                .build());
        list.add(McpServerFeatures.SyncToolSpecification.builder()
                .tool(tool("anilink_resource_types", "资源搜索：类型列表（需超级管理员）", SCHEMA_EMPTY_OBJECT))
                .callHandler((ex, req) -> requireSuper(ex, () -> jsonOk(resourceSearchProxyService.fetchTypes())))
                .build());
        list.add(McpServerFeatures.SyncToolSpecification.builder()
                .tool(tool("anilink_resource_search", "资源搜索（需超级管理员）",
                        """
                        {"type":"object","required":["keyword"],"properties":{
                        "keyword":{"type":"string"},
                        "subgroup":{"type":"integer"},
                        "type":{"type":"integer"}
                        }}
                        """))
                .callHandler((ex, req) -> requireSuper(ex, () -> {
                    String keyword = strArg(req.arguments(), "keyword");
                    if (!StringUtils.hasText(keyword)) {
                        return err("keyword 必填");
                    }
                    Integer subgroup = req.arguments().get("subgroup") instanceof Number n ? n.intValue() : null;
                    Integer type = req.arguments().get("type") instanceof Number n ? n.intValue() : null;
                    return jsonOk(resourceSearchProxyService.fetchResources(keyword.trim(), subgroup, type));
                }))
                .build());
        list.add(McpServerFeatures.SyncToolSpecification.builder()
                .tool(tool("anilink_resource_download_start", "创建磁链下载任务（需超级管理员）",
                        """
                        {"type":"object","required":["magnet","libraryId"],"properties":{
                        "title":{"type":"string"},
                        "magnet":{"type":"string"},
                        "pageUrl":{"type":"string"},
                        "fileSize":{"type":"string"},
                        "publishDate":{"type":"string"},
                        "subgroupName":{"type":"string"},
                        "typeName":{"type":"string"},
                        "libraryId":{"type":"integer"}
                        }}
                        """))
                .callHandler((ex, req) -> requireSuper(ex, () -> {
                    Map<String, Object> a = req.arguments();
                    String magnet = strArg(a, "magnet");
                    Long libraryId = longArg(a, "libraryId", null);
                    if (!StringUtils.hasText(magnet) || libraryId == null) {
                        return err("magnet 与 libraryId 必填");
                    }
                    ResourceSearchDownloadRequest r = new ResourceSearchDownloadRequest();
                    r.setTitle(strArg(a, "title"));
                    r.setMagnet(magnet.trim());
                    r.setPageUrl(strArg(a, "pageUrl"));
                    r.setFileSize(strArg(a, "fileSize"));
                    r.setPublishDate(strArg(a, "publishDate"));
                    r.setSubgroupName(strArg(a, "subgroupName"));
                    r.setTypeName(strArg(a, "typeName"));
                    r.setLibraryId(libraryId);
                    return jsonOk(resourceDownloadService.startDownload(r));
                }))
                .build());
        list.add(McpServerFeatures.SyncToolSpecification.builder()
                .tool(tool("anilink_resource_download_tasks", "最近下载任务列表（需超级管理员）", SCHEMA_EMPTY_OBJECT))
                .callHandler((ex, req) -> requireSuper(ex, () -> jsonOk(resourceDownloadService.listRecentTasks())))
                .build());
        list.add(McpServerFeatures.SyncToolSpecification.builder()
                .tool(tool("anilink_resource_download_cancel", "取消下载任务（需超级管理员）",
                        """
                        {"type":"object","required":["taskId"],"properties":{"taskId":{"type":"integer"}}}
                        """))
                .callHandler((ex, req) -> requireSuper(ex, () -> {
                    Long id = longArg(req.arguments(), "taskId", null);
                    if (id == null) {
                        return err("taskId 必填");
                    }
                    return jsonOk(resourceDownloadService.cancelTask(id));
                }))
                .build());
        list.add(McpServerFeatures.SyncToolSpecification.builder()
                .tool(tool("anilink_resource_download_retry", "重试下载任务（需超级管理员）",
                        """
                        {"type":"object","required":["taskId"],"properties":{"taskId":{"type":"integer"}}}
                        """))
                .callHandler((ex, req) -> requireSuper(ex, () -> {
                    Long id = longArg(req.arguments(), "taskId", null);
                    if (id == null) {
                        return err("taskId 必填");
                    }
                    return jsonOk(resourceDownloadService.retryTask(id));
                }))
                .build());
        list.add(McpServerFeatures.SyncToolSpecification.builder()
                .tool(tool("anilink_resource_download_delete", "删除下载任务记录（需超级管理员）",
                        """
                        {"type":"object","required":["taskId"],"properties":{"taskId":{"type":"integer"}}}
                        """))
                .callHandler((ex, req) -> requireSuper(ex, () -> {
                    Long id = longArg(req.arguments(), "taskId", null);
                    if (id == null) {
                        return err("taskId 必填");
                    }
                    resourceDownloadService.deleteTask(id);
                    return jsonOk(Map.of("ok", true));
                }))
                .build());
        list.add(McpServerFeatures.SyncToolSpecification.builder()
                .tool(tool("anilink_rss_list", "RSS 订阅列表（需超级管理员）", SCHEMA_EMPTY_OBJECT))
                .callHandler((ex, req) -> requireSuper(ex, () -> jsonOk(resourceRssSubscriptionService.listSubscriptions())))
                .build());
        list.add(McpServerFeatures.SyncToolSpecification.builder()
                .tool(tool("anilink_rss_create", "创建 RSS 订阅（需超级管理员）",
                        """
                        {"type":"object","required":["name","feedUrl","libraryId"],"properties":{
                        "name":{"type":"string"},
                        "feedUrl":{"type":"string"},
                        "libraryId":{"type":"integer"},
                        "intervalMinutes":{"type":"integer"},
                        "enabled":{"type":"boolean"}
                        }}
                        """))
                .callHandler((ex, req) -> requireSuper(ex, () -> {
                    Map<String, Object> a = req.arguments();
                    ResourceRssSubscriptionRequest r = new ResourceRssSubscriptionRequest();
                    r.setName(strArg(a, "name"));
                    r.setFeedUrl(strArg(a, "feedUrl"));
                    r.setLibraryId(longArg(a, "libraryId", null));
                    if (a.get("intervalMinutes") instanceof Number n) {
                        r.setIntervalMinutes(n.intValue());
                    }
                    if (a.get("enabled") instanceof Boolean b) {
                        r.setEnabled(b);
                    }
                    if (!StringUtils.hasText(r.getName()) || !StringUtils.hasText(r.getFeedUrl()) || r.getLibraryId() == null) {
                        return err("name、feedUrl、libraryId 必填");
                    }
                    return jsonOk(resourceRssSubscriptionService.createSubscription(r));
                }))
                .build());
        list.add(McpServerFeatures.SyncToolSpecification.builder()
                .tool(tool("anilink_rss_trigger", "立即检查 RSS（需超级管理员）",
                        """
                        {"type":"object","required":["id"],"properties":{"id":{"type":"integer"}}}
                        """))
                .callHandler((ex, req) -> requireSuper(ex, () -> {
                    Long id = longArg(req.arguments(), "id", null);
                    if (id == null) {
                        return err("id 必填");
                    }
                    resourceRssSubscriptionService.triggerNow(id);
                    return jsonOk(Map.of("ok", true));
                }))
                .build());
        list.add(McpServerFeatures.SyncToolSpecification.builder()
                .tool(tool("anilink_rss_delete", "删除 RSS 订阅（需超级管理员）",
                        """
                        {"type":"object","required":["id"],"properties":{"id":{"type":"integer"}}}
                        """))
                .callHandler((ex, req) -> requireSuper(ex, () -> {
                    Long id = longArg(req.arguments(), "id", null);
                    if (id == null) {
                        return err("id 必填");
                    }
                    resourceRssSubscriptionService.deleteSubscription(id);
                    return jsonOk(Map.of("ok", true));
                }))
                .build());
        list.add(McpServerFeatures.SyncToolSpecification.builder()
                .tool(tool("anilink_rss_last_content", "查询 RSS 最近拉取内容（需超级管理员）",
                        """
                        {"type":"object","required":["id"],"properties":{"id":{"type":"integer"}}}
                        """))
                .callHandler((ex, req) -> requireSuper(ex, () -> {
                    Long id = longArg(req.arguments(), "id", null);
                    if (id == null) {
                        return err("id 必填");
                    }
                    return jsonOk(resourceRssSubscriptionService.getLastFetchedContent(id));
                }))
                .build());
        return list;
    }

    private List<McpServerFeatures.SyncToolSpecification> mediaAdminTools() {
        List<McpServerFeatures.SyncToolSpecification> list = new ArrayList<>();
        list.add(McpServerFeatures.SyncToolSpecification.builder()
                .tool(tool("anilink_media_libraries", "列出媒体库（需超级管理员）", SCHEMA_EMPTY_OBJECT))
                .callHandler((ex, req) -> requireSuper(ex, () -> jsonOk(mediaLibraryService.findAll())))
                .build());
        list.add(McpServerFeatures.SyncToolSpecification.builder()
                .tool(tool("anilink_media_scan", "扫描指定媒体库（需超级管理员）",
                        """
                        {"type":"object","required":["libraryId"],"properties":{"libraryId":{"type":"integer"}}}
                        """))
                .callHandler((ex, req) -> requireSuper(ex, () -> {
                    Long id = longArg(req.arguments(), "libraryId", null);
                    if (id == null) {
                        return err("libraryId 必填");
                    }
                    mediaLibraryService.scanLibrary(id);
                    return jsonOk(Map.of("ok", true, "message", "扫描已触发"));
                }))
                .build());
        list.add(McpServerFeatures.SyncToolSpecification.builder()
                .tool(tool("anilink_media_scan_all", "扫描全部媒体库（需超级管理员）", SCHEMA_EMPTY_OBJECT))
                .callHandler((ex, req) -> requireSuper(ex, () -> {
                    mediaLibraryService.scanAllLibraries();
                    return jsonOk(Map.of("ok", true, "message", "扫描已触发"));
                }))
                .build());
        list.add(McpServerFeatures.SyncToolSpecification.builder()
                .tool(tool("anilink_media_rematch_library", "将指定媒体库可匹配文件加入弹弹匹配队列（需超级管理员）",
                        """
                        {"type":"object","required":["libraryId"],"properties":{"libraryId":{"type":"integer"}}}
                        """))
                .callHandler((ex, req) -> requireSuper(ex, () -> {
                    Long id = longArg(req.arguments(), "libraryId", null);
                    if (id == null) {
                        return err("libraryId 必填");
                    }
                    int n = mediaMatchQueueManager.enqueueLibraryForRematch(id);
                    return jsonOk(Map.of("enqueued", n));
                }))
                .build());
        list.add(McpServerFeatures.SyncToolSpecification.builder()
                .tool(tool("anilink_media_files", "分页查询视频文件（需超级管理员）",
                        """
                        {"type":"object","properties":{
                        "libraryId":{"type":"integer"},
                        "keyword":{"type":"string"},
                        "matched":{"type":"boolean","description":"true=仅已匹配，false=仅未匹配"},
                        "page":{"type":"integer","description":"从0开始，默认0"},
                        "pageSize":{"type":"integer","description":"默认20"}
                        }}
                        """))
                .callHandler((ex, req) -> requireSuper(ex, () -> {
                    Map<String, Object> a = req.arguments();
                    Long libraryId = longArg(a, "libraryId", null);
                    String keyword = strArg(a, "keyword");
                    Boolean matched = null;
                    if (a.get("matched") instanceof Boolean b) {
                        matched = b;
                    }
                    int page = intArg(a, "page", 0);
                    int pageSize = intArg(a, "pageSize", 20);
                    return jsonOk(mediaFileService.getMediaFiles(libraryId, page, pageSize, keyword, matched));
                }))
                .build());
        list.add(McpServerFeatures.SyncToolSpecification.builder()
                .tool(tool("anilink_media_file_rematch", "单文件重新匹配弹弹（需超级管理员）",
                        """
                        {"type":"object","required":["fileId"],"properties":{"fileId":{"type":"integer"}}}
                        """))
                .callHandler((ex, req) -> requireSuper(ex, () -> {
                    Long id = longArg(req.arguments(), "fileId", null);
                    if (id == null) {
                        return err("fileId 必填");
                    }
                    try {
                        return jsonOk(mediaFileService.rematchMediaFile(id));
                    } catch (IllegalArgumentException e) {
                        return err(e.getMessage());
                    } catch (Exception e) {
                        return err("重新匹配失败: " + e.getMessage());
                    }
                }))
                .build());
        list.add(McpServerFeatures.SyncToolSpecification.builder()
                .tool(tool("anilink_queue_metadata_status", "元数据提取队列状态（需超级管理员）", SCHEMA_EMPTY_OBJECT))
                .callHandler((ex, req) -> requireSuper(ex, () -> {
                    QueueStatusVO status = QueueStatusVO.builder()
                            .pendingTasks(mediaMetadataQueueManager.getQueueSize()
                                    + mediaMetadataQueueManager.getActiveThreadCount())
                            .activeThreads(mediaMetadataQueueManager.getActiveThreadCount())
                            .maxPoolSize(mediaMetadataQueueManager.getMaxPoolSize())
                            .totalProcessed(mediaMetadataQueueManager.getTotalProcessed())
                            .build();
                    return jsonOk(status);
                }))
                .build());
        list.add(McpServerFeatures.SyncToolSpecification.builder()
                .tool(tool("anilink_match_progress", "弹弹匹配进度（需超级管理员）",
                        """
                        {"type":"object","properties":{
                        "libraryId":{"type":"integer","description":"不传则汇总全部"}
                        }}
                        """))
                .callHandler((ex, req) -> requireSuper(ex, () ->
                        jsonOk(mediaFileService.getMatchProgress(longArg(req.arguments(), "libraryId", null)))))
                .build());
        list.add(McpServerFeatures.SyncToolSpecification.builder()
                .tool(tool("anilink_metadata_progress", "元数据提取进度（需超级管理员）",
                        """
                        {"type":"object","properties":{
                        "libraryId":{"type":"integer","description":"不传则汇总全部"}
                        }}
                        """))
                .callHandler((ex, req) -> requireSuper(ex, () ->
                        jsonOk(mediaFileService.getMetadataProgress(longArg(req.arguments(), "libraryId", null)))))
                .build());
        return list;
    }

    @FunctionalInterface
    private interface ToolSupplier {
        CallToolResult get() throws Exception;
    }

    private CallToolResult requireSuper(McpSyncServerExchange ex, ToolSupplier supplier) {
        if (!isSuperAdmin(ex)) {
            return err("需要超级管理员权限（super-admin）");
        }
        try {
            return supplier.get();
        } catch (Exception e) {
            return err(e.getMessage() != null ? e.getMessage() : "执行失败");
        }
    }

    private boolean isSuperAdmin(McpSyncServerExchange ex) {
        Object roles = ex.transportContext().get("roleCodes");
        if (roles instanceof List<?> list) {
            return list.stream().anyMatch(r -> "super-admin".equals(String.valueOf(r)));
        }
        return false;
    }

    private Long userIdFrom(McpSyncServerExchange ex) {
        Object u = ex.transportContext().get("userId");
        return u instanceof Number n ? n.longValue() : null;
    }

    private McpSchema.Tool tool(String name, String description, String schemaJson) {
        return McpSchema.Tool.builder()
                .name(name)
                .description(description)
                .inputSchema(mcpJsonMapper, schemaJson)
                .build();
    }

    private CallToolResult jsonOk(Object body) {
        try {
            String json = objectMapper.writeValueAsString(body);
            return CallToolResult.builder()
                    .content(List.of(new McpSchema.TextContent(json)))
                    .isError(false)
                    .build();
        } catch (Exception e) {
            return err("序列化结果失败: " + e.getMessage());
        }
    }

    private CallToolResult err(String message) {
        try {
            String json = objectMapper.writeValueAsString(Map.of("error", true, "message", message));
            return CallToolResult.builder()
                    .content(List.of(new McpSchema.TextContent(json)))
                    .isError(true)
                    .build();
        } catch (Exception e) {
            return CallToolResult.builder()
                    .content(List.of(new McpSchema.TextContent("{\"error\":true,\"message\":\"internal\"}")))
                    .isError(true)
                    .build();
        }
    }

    private static String strArg(Map<String, Object> args, String key) {
        Object v = args.get(key);
        if (v == null) {
            return null;
        }
        String s = String.valueOf(v).trim();
        return s.isEmpty() ? null : s;
    }

    private static int intArg(Map<String, Object> args, String key, int defaultVal) {
        Object v = args.get(key);
        if (v instanceof Number n) {
            return n.intValue();
        }
        return defaultVal;
    }

    private static Long longArg(Map<String, Object> args, String key, Long defaultVal) {
        Object v = args.get(key);
        if (v instanceof Number n) {
            return n.longValue();
        }
        if (v instanceof String s && StringUtils.hasText(s)) {
            try {
                return Long.parseLong(s.trim());
            } catch (NumberFormatException ignored) {
                return defaultVal;
            }
        }
        return defaultVal;
    }

    private static boolean boolArg(Map<String, Object> args, String key, boolean defaultVal) {
        Object v = args.get(key);
        if (v instanceof Boolean b) {
            return b;
        }
        return defaultVal;
    }
}
