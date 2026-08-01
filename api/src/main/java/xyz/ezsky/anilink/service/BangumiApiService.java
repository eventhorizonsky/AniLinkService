package xyz.ezsky.anilink.service;

import lombok.extern.log4j.Log4j2;
import okhttp3.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Bangumi.tv API 代理服务。
 * 无需鉴权直接请求公开接口。
 */
@Service
@Log4j2
public class BangumiApiService {

    private static final String BANGUMI_NEXT_BASE = "https://next.bgm.tv";
    private static final String BANGUMI_API_BASE = "https://api.bgm.tv";

    @Autowired
    private SiteConfigService siteConfigService;

    private final OkHttpClient baseClient = new OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .build();

    /**
     * 获取番剧吐槽箱（短评列表）。
     *
     * @param subjectId Bangumi subjectID
     * @param limit     每页数量
     * @param offset    偏移量
     * @return 原始 JSON 字符串，失败返回 null
     */
    public String getSubjectComments(Long subjectId, Integer limit, Integer offset) {
        ResponseEntity<String> response = execute(resolveBaseUrl(BANGUMI_NEXT_BASE), "GET", "/p1/subjects/" + subjectId + "/comments",
                null, null, Map.of("limit", String.valueOf(limit), "offset", String.valueOf(offset)));

        if (response.getStatusCode().is2xxSuccessful()) {
            return response.getBody();
        }
        return null;
    }

    public ResponseEntity<String> getMe(String accessToken) {
        return execute(resolveBaseUrl(BANGUMI_API_BASE), "GET", "/v0/me", accessToken, null, null);
    }

    public ResponseEntity<String> getUserCollection(String accessToken, String username, Long subjectId) {
        return execute(resolveBaseUrl(BANGUMI_API_BASE), "GET", "/v0/users/" + username + "/collections/" + subjectId, accessToken, null, null);
    }

    public ResponseEntity<String> postUserCollection(String accessToken, Long subjectId, String payloadJson) {
        return execute(resolveBaseUrl(BANGUMI_API_BASE), "POST", "/v0/users/-/collections/" + subjectId, accessToken, payloadJson, null);
    }

    /**
     * 修改用户单个条目收藏（PATCH）。
     * 所有请求体字段可选；条目未收藏时返回 404（不会自动创建）。
     */
    public ResponseEntity<String> patchUserCollection(String accessToken, Long subjectId, String payloadJson) {
        return execute(resolveBaseUrl(BANGUMI_API_BASE), "PATCH", "/v0/users/-/collections/" + subjectId, accessToken, payloadJson, null);
    }

    /**
     * 获取用户收藏列表（支持分页和类型筛选）。
     *
     * @param accessToken 用户 access token
     * @param username    Bangumi 用户名
     * @param subjectType 条目类型（2=动画），null 表示全部
     * @param collectionType 收藏类型（1-5），null 表示全部
     * @param limit       每页数量（最大 50）
     * @param offset      偏移量
     */
    public ResponseEntity<String> getUserCollections(String accessToken, String username,
                                                      Integer subjectType, Integer collectionType,
                                                      Integer limit, Integer offset) {
        Map<String, String> params = new java.util.LinkedHashMap<>();
        if (subjectType != null) params.put("subject_type", String.valueOf(subjectType));
        if (collectionType != null) params.put("type", String.valueOf(collectionType));
        params.put("limit", String.valueOf(limit != null ? Math.min(limit, 50) : 30));
        params.put("offset", String.valueOf(offset != null ? offset : 0));
        return execute(resolveBaseUrl(BANGUMI_API_BASE), "GET", "/v0/users/" + username + "/collections",
                accessToken, null, params);
    }

    // ===== 剧集级 API =====

    /**
     * 获取 Bangumi 条目的剧集列表。
     *
     * @param subjectId Bangumi subject ID
     * @param type      剧集类型（0=本篇, 1=SP, 2=OP, 3=ED 等），null 表示全部
     * @param limit     每页数量（最大 200）
     * @param offset    偏移量
     */
    public ResponseEntity<String> getEpisodes(Long subjectId, Integer type, Integer limit, Integer offset) {
        return execute(resolveBaseUrl(BANGUMI_API_BASE), "GET", "/v0/episodes", null, null,
                buildEpisodeQueryParams(subjectId, type, limit, offset));
    }

    /**
     * 获取用户对某条目的剧集收藏状态（哪些集已看/抛弃）。
     *
     * @param accessToken 用户 Bangumi access token
     * @param subjectId   Bangumi subject ID
     * @param offset      偏移量
     * @param limit       每页数量（最大 1000）
     */
    public ResponseEntity<String> getUserSubjectEpisodeCollection(String accessToken, Long subjectId, Integer offset, Integer limit) {
        return execute(resolveBaseUrl(BANGUMI_API_BASE), "GET", "/v0/users/-/collections/" + subjectId + "/episodes",
                accessToken, null, Map.of("offset", String.valueOf(offset != null ? offset : 0),
                        "limit", String.valueOf(limit != null ? limit : 100)));
    }

    /**
     * 批量标记用户对某条目剧集的收藏状态。
     * body: {"episode_id": [1, 2, 3], "type": 2}
     *
     * @param accessToken 用户 Bangumi access token
     * @param subjectId   Bangumi subject ID
     * @param payloadJson 请求体 JSON 字符串
     */
    public ResponseEntity<String> patchUserSubjectEpisodeCollection(String accessToken, Long subjectId, String payloadJson) {
        return execute(resolveBaseUrl(BANGUMI_API_BASE), "PATCH", "/v0/users/-/collections/" + subjectId + "/episodes",
                accessToken, payloadJson, null);
    }

    /**
     * 标记单个剧集的收藏状态。
     * body: {"type": 2}
     *
     * @param accessToken 用户 Bangumi access token
     * @param episodeId   Bangumi episode ID
     * @param payloadJson 请求体 JSON 字符串
     */
    public ResponseEntity<String> putUserEpisodeCollection(String accessToken, Long episodeId, String payloadJson) {
        return execute(resolveBaseUrl(BANGUMI_API_BASE), "PUT", "/v0/users/-/collections/-/episodes/" + episodeId,
                accessToken, payloadJson, null);
    }

    private Map<String, String> buildEpisodeQueryParams(Long subjectId, Integer type, Integer limit, Integer offset) {
        Map<String, String> params = new java.util.LinkedHashMap<>();
        params.put("subject_id", String.valueOf(subjectId));
        if (type != null) {
            params.put("type", String.valueOf(type));
        }
        params.put("limit", String.valueOf(limit != null ? Math.min(limit, 200) : 100));
        params.put("offset", String.valueOf(offset != null ? offset : 0));
        return params;
    }

    private ResponseEntity<String> execute(String baseUrl, String method, String path, String accessToken, String payloadJson,
                                           Map<String, String> queryParams) {
        HttpUrl.Builder urlBuilder = HttpUrl.parse(baseUrl + path).newBuilder();
        if (queryParams != null) {
            queryParams.forEach(urlBuilder::addQueryParameter);
        }

        Request request = new Request.Builder()
                .url(urlBuilder.build())
                .headers(buildHeaders(accessToken))
                .method(method, buildRequestBody(method, payloadJson))
                .build();

        OkHttpClient client = baseClient;

        try (Response response = client.newCall(request).execute()) {
            String body = response.body() != null ? response.body().string() : "";
            log.debug("Bangumi API {} {} returned {}", method, path, response.code());
            return new ResponseEntity<>(body, HttpStatus.valueOf(response.code()));
        } catch (IOException e) {
            log.error("Bangumi API request failed for {} {}", method, path, e);
            return new ResponseEntity<>("", HttpStatus.BAD_GATEWAY);
        }
    }

    private Headers buildHeaders(String accessToken) {
        Headers.Builder builder = new Headers.Builder()
                .add("User-Agent", "AniLinkService/1.0 (https://github.com/AniLink)")
                .add("Accept", "application/json");
        if (StringUtils.hasText(accessToken)) {
            builder.add("Authorization", "Bearer " + accessToken.trim());
        }
        return builder.build();
    }

    private RequestBody buildRequestBody(String method, String payloadJson) {
        if ("GET".equalsIgnoreCase(method) || "DELETE".equalsIgnoreCase(method)) {
            return null;
        }
        byte[] bytes = (payloadJson == null ? "{}" : payloadJson).getBytes(StandardCharsets.UTF_8);
        return RequestBody.create(bytes, MediaType.parse("application/json"));
    }

    /**
     * 解析请求基础地址：配置了 Bangumi 镜像地址时使用镜像，否则使用官方地址。
     */
    private String resolveBaseUrl(String defaultBase) {
        String mirror = siteConfigService.getBangumiMirrorBaseUrl();
        if (StringUtils.hasText(mirror)) {
            return mirror.endsWith("/") ? mirror.substring(0, mirror.length() - 1) : mirror;
        }
        return defaultBase;
    }
}
