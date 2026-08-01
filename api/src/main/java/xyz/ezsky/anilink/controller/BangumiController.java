package xyz.ezsky.anilink.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.stp.StpUtil;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Map;

import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import xyz.ezsky.anilink.model.dto.BindBangumiTokenRequest;
import xyz.ezsky.anilink.model.dto.UpdateBangumiCollectionRequest;
import xyz.ezsky.anilink.model.entity.User;
import xyz.ezsky.anilink.model.vo.ApiResponseVO;
import xyz.ezsky.anilink.model.vo.BangumiBindingStatusVO;
import xyz.ezsky.anilink.service.BangumiApiService;
import xyz.ezsky.anilink.service.BangumiSyncService;
import xyz.ezsky.anilink.service.UserService;

/**
 * Bangumi.tv API 代理接口
 */
@Tag(name = "Bangumi API", description = "Bangumi.tv 公开接口代理")
@RestController
@RequestMapping("/api/bangumi")
@Log4j2
public class BangumiController {

    @Autowired
    private BangumiApiService bangumiApiService;

    @Autowired
    private UserService userService;

    @Autowired
    private BangumiSyncService bangumiSyncService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 获取番剧吐槽箱（短评）
     *
     * @param subjectId Bangumi subjectID（来自番剧详情页 bangumiUrl 中的数字）
     * @param limit     每页数量（默认 20，最大 50）
     * @param offset    偏移量（默认 0）
     * @return 评论列表
     */
    @Operation(summary = "获取番剧评论", description = "代理 Bangumi API 获取番剧吐槽箱内容")
    @GetMapping("/subjects/{subjectId}/comments")
    public ApiResponseVO<Object> getSubjectComments(
            @Parameter(description = "Bangumi subjectID", required = true)
            @PathVariable Long subjectId,
            @Parameter(description = "每页数量")
            @RequestParam(defaultValue = "20") Integer limit,
            @Parameter(description = "偏移量")
            @RequestParam(defaultValue = "0") Integer offset) {

        if (limit < 1 || limit > 50) {
            limit = 20;
        }
        if (offset < 0) {
            offset = 0;
        }

        String result = bangumiApiService.getSubjectComments(subjectId, limit, offset);
        if (!StringUtils.hasText(result)) {
            return ApiResponseVO.fail(502, "获取评论失败");
        }
        try {
            Object json = objectMapper.readValue(result, Object.class);
            return ApiResponseVO.success(json);
        } catch (JsonProcessingException e) {
            log.error("Failed to parse Bangumi comments JSON for subjectId={}", subjectId, e);
            return ApiResponseVO.fail(500, "评论数据解析失败");
        }
    }

    /**
     * Get Bangumi 吐槽 (episode comments) for the currently playing episode.
     * <p>
     * The backend maps the dandan episode number to a Bangumi episode by the
     * position in the Bangumi main-episode list, then returns the comments.
     *
     * @param animeId       local anime id (dandan animeId)
     * @param episodeNumber dandan episode number, e.g. "1"
     * @return comments list with matched episode info
     */
    @Operation(summary = "获取单集 Bangumi 吐槽", description = "根据本地番剧与集数匹配 Bangumi 剧集并返回吐槽箱内容")
    @GetMapping("/episodes/comments")
    public ApiResponseVO<Object> getEpisodeComments(
            @Parameter(description = "本地番剧 ID（弹弹 animeId）", required = true)
            @RequestParam Long animeId,
            @Parameter(description = "集数（如 \"1\"）", required = true)
            @RequestParam String episodeNumber) {

        Map<String, Object> result = bangumiSyncService.getEpisodeCommentsForAnime(animeId, episodeNumber);
        return ApiResponseVO.success(result);
    }

    @SaCheckLogin
    @GetMapping("/account/status")
    @Operation(summary = "获取当前用户 Bangumi 绑定状态", description = "验证当前已绑定 token 是否有效，并返回 Bangumi 用户信息")
    public ApiResponseVO<BangumiBindingStatusVO> getCurrentBindingStatus() {
        User user = getCurrentUser();
        if (!StringUtils.hasText(user.getBangumiAccessToken())) {
            return ApiResponseVO.success(new BangumiBindingStatusVO(
                    false,
                    false,
                    false,
                    user.getBangumiUserId(),
                    user.getBangumiUsername(),
                    user.getBangumiNickname(),
                    null,
                    "未绑定 Bangumi 账号"
            ));
        }

        ResponseEntity<String> response = bangumiApiService.getMe(user.getBangumiAccessToken());
        if (response.getStatusCode().is2xxSuccessful() && StringUtils.hasText(response.getBody())) {
            try {
                JsonNode profile = objectMapper.readTree(response.getBody());
                syncBangumiBinding(user.getId(), profile, user.getBangumiAccessToken());
                return ApiResponseVO.success(buildBindingStatusVO(profile, true, false, "Bangumi Token 有效"));
            } catch (JsonProcessingException e) {
                log.error("Failed to parse Bangumi /v0/me response for userId={}", user.getId(), e);
                return ApiResponseVO.fail(500, "Bangumi 用户信息解析失败");
            }
        }

        if (response.getStatusCode().value() == 401) {
            return ApiResponseVO.success(new BangumiBindingStatusVO(
                    true,
                    false,
                    true,
                    user.getBangumiUserId(),
                    user.getBangumiUsername(),
                    user.getBangumiNickname(),
                    null,
                    "Bangumi Token 已过期或无效"
            ));
        }

        return ApiResponseVO.success(new BangumiBindingStatusVO(
                true,
                false,
                false,
                user.getBangumiUserId(),
                user.getBangumiUsername(),
                user.getBangumiNickname(),
                null,
                "暂时无法验证 Bangumi Token 状态"
        ));
    }

    @SaCheckLogin
    @PostMapping("/account/bind")
    @Operation(summary = "绑定 Bangumi 账号", description = "保存 Bangumi Access Token，并通过 /v0/me 校验有效性")
    public ApiResponseVO<BangumiBindingStatusVO> bindBangumiAccount(@RequestBody BindBangumiTokenRequest request) {
        if (request == null || !StringUtils.hasText(request.getAccessToken())) {
            return ApiResponseVO.fail(400, "请提供 Bangumi Access Token");
        }

        ResponseEntity<String> response = bangumiApiService.getMe(request.getAccessToken().trim());
        if (response.getStatusCode().value() == 401) {
            return ApiResponseVO.fail(401, "Bangumi Token 无效或已过期");
        }
        if (!response.getStatusCode().is2xxSuccessful() || !StringUtils.hasText(response.getBody())) {
            return ApiResponseVO.fail(502, "Bangumi 服务暂时不可用，请稍后重试");
        }

        try {
            JsonNode profile = objectMapper.readTree(response.getBody());
            syncBangumiBinding(getCurrentUserId(), profile, request.getAccessToken().trim());
            return ApiResponseVO.success(buildBindingStatusVO(profile, true, false, "绑定成功"), "绑定成功");
        } catch (JsonProcessingException e) {
            log.error("Failed to parse Bangumi binding response", e);
            return ApiResponseVO.fail(500, "Bangumi 用户信息解析失败");
        }
    }

    @SaCheckLogin
    @DeleteMapping("/account/bind")
    @Operation(summary = "解除 Bangumi 绑定", description = "删除当前用户已保存的 Bangumi Token 和绑定信息")
    public ApiResponseVO<String> unbindBangumiAccount() {
        userService.clearBangumiBinding(getCurrentUserId());
        return ApiResponseVO.success("ok", "已解除 Bangumi 绑定");
    }

    @SaCheckLogin
    @GetMapping("/subjects/{subjectId}/collection")
    @Operation(summary = "获取当前绑定 Bangumi 用户的条目收藏", description = "通过已绑定 token 查询当前用户对指定条目的评分和评论")
    public ApiResponseVO<Object> getCurrentUserCollection(
            @Parameter(description = "Bangumi subjectID", required = true)
            @PathVariable Long subjectId) {
        User user = getCurrentUser();
        if (!StringUtils.hasText(user.getBangumiAccessToken()) || !StringUtils.hasText(user.getBangumiUsername())) {
            return ApiResponseVO.fail(400, "当前账号尚未绑定 Bangumi");
        }
        ResponseEntity<String> response = bangumiApiService.getUserCollection(
                user.getBangumiAccessToken(),
                user.getBangumiUsername(),
                subjectId
        );

        if (response.getStatusCode().value() == 401) {
            return ApiResponseVO.fail(401, "Bangumi Token 已过期或无效");
        }
        if (response.getStatusCode().value() == 404) {
            return ApiResponseVO.fail(404, "当前未记录 Bangumi 收藏");
        }
        if (!response.getStatusCode().is2xxSuccessful() || !StringUtils.hasText(response.getBody())) {
            return ApiResponseVO.fail(502, "获取 Bangumi 收藏失败");
        }

        try {
            Object json = objectMapper.readValue(response.getBody(), Object.class);
            return ApiResponseVO.success(json);
        } catch (JsonProcessingException e) {
            log.error("Failed to parse Bangumi collection for subjectId={}", subjectId, e);
            return ApiResponseVO.fail(500, "Bangumi 收藏数据解析失败");
        }
    }

    @SaCheckLogin
    @PostMapping("/subjects/{subjectId}/collection")
    @Operation(summary = "写入当前绑定 Bangumi 用户的条目收藏", description = "通过 /v0/users/-/collections/{subject_id} 保存评分、收藏状态和评论")
    public ApiResponseVO<String> saveCurrentUserCollection(
            @Parameter(description = "Bangumi subjectID", required = true)
            @PathVariable Long subjectId,
            @RequestBody UpdateBangumiCollectionRequest request) {
        if (request == null || isEmptyCollectionRequest(request)) {
            return ApiResponseVO.fail(400, "请至少提供收藏状态、评分或评论中的一项");
        }
        if (request.getRate() != null && (request.getRate() < 0 || request.getRate() > 10)) {
            return ApiResponseVO.fail(400, "评分范围必须在 0 到 10 之间");
        }
        if (request.getType() != null && (request.getType() < 1 || request.getType() > 5)) {
            return ApiResponseVO.fail(400, "收藏状态不合法");
        }

        User user = getCurrentUser();
        if (!StringUtils.hasText(user.getBangumiAccessToken()) || !StringUtils.hasText(user.getBangumiUsername())) {
            return ApiResponseVO.fail(400, "当前账号尚未绑定 Bangumi");
        }
        ObjectNode payload = objectMapper.createObjectNode();
        if (request.getType() != null) {
            payload.put("type", request.getType());
        }
        if (request.getRate() != null) {
            payload.put("rate", request.getRate());
        }
        if (request.getComment() != null) {
            payload.put("comment", request.getComment());
        }
        if (request.getPrivateCollection() != null) {
            payload.put("private", request.getPrivateCollection());
        }

        ResponseEntity<String> response = bangumiApiService.postUserCollection(
                user.getBangumiAccessToken(),
                subjectId,
                payload.toString()
        );

        if (response.getStatusCode().value() == 401) {
            return ApiResponseVO.fail(401, "Bangumi Token 已过期或无效");
        }
        if (!response.getStatusCode().is2xxSuccessful()) {
            return ApiResponseVO.fail(502, "提交 Bangumi 评分失败");
        }

        return ApiResponseVO.success("ok", "已同步到 Bangumi");
    }

    @SaCheckLogin
    @PostMapping("/sync/episode-watched")
    @Operation(summary = "同步单集已看状态到 Bangumi", description = "将当前用户已看完的剧集标记为 Bangumi '看过'。异步执行，即发即忘。")
    public ApiResponseVO<String> syncEpisodeWatched(
            @Parameter(description = "本地番剧 ID（弹弹 animeId）", required = true)
            @RequestParam Long animeId,
            @Parameter(description = "集数（如 \"5\"）", required = true)
            @RequestParam String episodeNumber) {
        Long userId = Long.valueOf(StpUtil.getLoginId().toString());
        bangumiSyncService.syncEpisodeWatchedToBangumi(userId, animeId, episodeNumber);
        return ApiResponseVO.success("ok");
    }

    @SaCheckLogin
    @PostMapping("/sync/pull-collections")
    @Operation(summary = "从 Bangumi 拉取追番数据", description = "拉取当前绑定 Bangumi 账号的所有动画收藏，同步到本地追番列表。以 Bangumi 数据为准。")
    public ApiResponseVO<Map<String, Object>> pullBangumiCollections() {
        Long userId = Long.valueOf(StpUtil.getLoginId().toString());
        Map<String, Object> result = bangumiSyncService.pullBangumiCollections(userId);
        if (Boolean.FALSE.equals(result.get("success"))) {
            return ApiResponseVO.fail(400, String.valueOf(result.getOrDefault("message", "同步失败")));
        }
        return ApiResponseVO.success(result);
    }

    private User getCurrentUser() {
        return userService.findById(getCurrentUserId())
                .orElseThrow(() -> new RuntimeException("用户不存在"));
    }

    private Long getCurrentUserId() {
        return Long.valueOf(StpUtil.getLoginId().toString());
    }

    private void syncBangumiBinding(Long userId, JsonNode profile, String accessToken) {
        Long bangumiUserId = profile.path("id").isIntegralNumber() ? profile.path("id").asLong() : null;
        String username = profile.path("username").asText(null);
        String nickname = profile.path("nickname").asText(null);
        userService.bindBangumiAccount(userId, accessToken, bangumiUserId, username, nickname);
    }

    private BangumiBindingStatusVO buildBindingStatusVO(JsonNode profile, boolean tokenValid, boolean tokenExpired, String message)
            throws JsonProcessingException {
        return new BangumiBindingStatusVO(
                true,
                tokenValid,
                tokenExpired,
                profile.path("id").isIntegralNumber() ? profile.path("id").asLong() : null,
                profile.path("username").asText(null),
                profile.path("nickname").asText(null),
                objectMapper.readValue(profile.toString(), Object.class),
                message
        );
    }

    private boolean isEmptyCollectionRequest(UpdateBangumiCollectionRequest request) {
        return request.getType() == null
                && request.getRate() == null
                && request.getComment() == null
                && request.getPrivateCollection() == null;
    }
}
