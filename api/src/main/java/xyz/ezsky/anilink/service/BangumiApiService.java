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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Bangumi.tv API 代理服务。
 * <p>网络方案为“镜像”：api.bgm.tv (v0) 与 next.bgm.tv (p1) 各自可配置镜像地址
 * （如 bangumi.pro 镜像），留空使用官方地址。无需鉴权的公开接口直接请求。</p>
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

    /**
     * Get the episode comments (吐槽 box) from Bangumi.
     *
     * @param episodeId Bangumi episode ID
     * @return raw JSON array of comments, or null on failure
     */
    public String getEpisodeComments(Long episodeId) {
        ResponseEntity<String> response = execute(resolveBaseUrl(BANGUMI_NEXT_BASE), "GET",
                "/p1/episodes/" + episodeId + "/comments", null, null, null);
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
     * @param accessToken    用户 access token
     * @param username       Bangumi 用户名
     * @param subjectType    条目类型（2=动画），null 表示全部
     * @param collectionType 收藏类型（1-5），null 表示全部
     * @param limit          每页数量（最大 50）
     * @param offset         偏移量
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

    /**
     * 浏览条目分页原始 JSON（v0 列表接口；排行榜已改用 p1，此方法保留供其它调用方使用）。
     * 代理 GET /v0/subjects；失败或非 2xx 返回 null。
     */
    public String browseSubjectsPage(int type, String sort, int offset, int limit, Boolean nsfw) {
        Map<String, String> params = new java.util.LinkedHashMap<>();
        params.put("type", String.valueOf(type));
        if (StringUtils.hasText(sort)) {
            params.put("sort", sort.trim());
        }
        params.put("limit", String.valueOf(Math.min(Math.max(limit, 1), 100)));
        params.put("offset", String.valueOf(Math.max(offset, 0)));
        if (nsfw != null) {
            params.put("nsfw", String.valueOf(nsfw));
        }
        ResponseEntity<String> response = execute(resolveBaseUrl(BANGUMI_API_BASE), "GET", "/v0/subjects", null, null, params);
        if (response.getStatusCode().is2xxSuccessful()) {
            return response.getBody();
        }
        log.warn("Bangumi /v0/subjects browse failed offset={} status={}", offset, response.getStatusCode().value());
        return null;
    }

    /**
     * p1 条目浏览（排行榜）：GET /p1/subjects
     * <p>支持动画全筛选：type=2 + sort(rank/trends/collects/date/title) + page/cat/year/month +
     * 公共标签 tags(可多个，AND) + tagsCat=meta。匿名可用，无需登录。</p>
     *
     * @param metaTags 公共标签（地区/来源/题材/受众等），可为空
     * @return 原始 JSON 或 null
     */
    public String getP1SubjectsRaw(Integer cat, Integer year, Integer month, String sort, int page, List<String> metaTags) {
        List<String[]> pairs = new ArrayList<>();
        pairs.add(new String[]{"type", "2"});
        pairs.add(new String[]{"sort", StringUtils.hasText(sort) ? sort.trim() : "rank"});
        pairs.add(new String[]{"page", String.valueOf(Math.max(1, page))});
        if (cat != null) {
            pairs.add(new String[]{"cat", String.valueOf(cat)});
        }
        if (year != null && year > 0) {
            pairs.add(new String[]{"year", String.valueOf(year)});
            if (month != null && month > 0) {
                pairs.add(new String[]{"month", String.valueOf(month)});
            }
        }
        if (metaTags != null) {
            for (String tag : metaTags) {
                if (StringUtils.hasText(tag)) {
                    pairs.add(new String[]{"tags", tag.trim()});
                }
            }
            if (!metaTags.isEmpty()) {
                pairs.add(new String[]{"tagsCat", "meta"});
            }
        }
        ResponseEntity<String> response = executeQuery(resolveBaseUrl(BANGUMI_NEXT_BASE), "GET", "/p1/subjects", null, null, pairs);
        if (response.getStatusCode().is2xxSuccessful()) {
            return response.getBody();
        }
        log.warn("Bangumi /p1/subjects browse failed page={} status={}", page, response.getStatusCode().value());
        return null;
    }

    /**
     * p1 条目推荐：GET /p1/subjects/{subjectID}/recs
     * <p>官方「猜你喜欢」数据源（即条目页侧栏推荐），返回与该条目相似的条目列表（含 sim 相似度与
     * SlimSubject 摘要：nameCN/metaTags/rating/images 等），匿名可用，无需登录态；limit 最大 10。</p>
     *
     * @return 原始 JSON 或 null
     */
    public String getP1SubjectRecsRaw(Long subjectId, int limit) {
        List<String[]> pairs = new ArrayList<>();
        pairs.add(new String[]{"limit", String.valueOf(Math.max(1, Math.min(limit, 10)))});
        ResponseEntity<String> response = executeQuery(resolveBaseUrl(BANGUMI_NEXT_BASE), "GET",
                "/p1/subjects/" + subjectId + "/recs", null, null, pairs);
        if (response.getStatusCode().is2xxSuccessful()) {
            return response.getBody();
        }
        log.debug("Bangumi /p1/subjects/{}/recs failed status={}", subjectId, response.getStatusCode().value());
        return null;
    }

    private ResponseEntity<String> execute(String baseUrl, String method, String path, String accessToken, String payloadJson,
                                           Map<String, String> queryParams) {
        List<String[]> pairs = new ArrayList<>();
        if (queryParams != null) {
            queryParams.forEach((k, v) -> pairs.add(new String[]{k, v}));
        }
        return executeQuery(baseUrl, method, path, accessToken, payloadJson, pairs);
    }

    /**
     * 支持重复同名参数（如 p1 的 tags=a&tags=b）的请求执行。
     */
    private ResponseEntity<String> executeQuery(String baseUrl, String method, String path, String accessToken,
                                                String payloadJson, List<String[]> queryParams) {
        try {
            HttpUrl.Builder urlBuilder = HttpUrl.parse(baseUrl + path).newBuilder();
            if (queryParams != null) {
                for (String[] p : queryParams) {
                    urlBuilder.addQueryParameter(p[0], p[1]);
                }
            }
            Request request = new Request.Builder()
                    .url(urlBuilder.build())
                    .headers(buildHeaders(accessToken))
                    .method(method, buildRequestBody(method, payloadJson))
                    .build();
            try (Response response = baseClient.newCall(request).execute()) {
                String body = response.body() != null ? response.body().string() : "";
                log.debug("Bangumi API {} {} returned {}", method, path, response.code());
                return new ResponseEntity<>(body, HttpStatus.valueOf(response.code()));
            }
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
     * 解析请求基础地址：配置了对应镜像地址时使用镜像，否则使用官方地址。
     */
    private String resolveBaseUrl(String defaultBase) {
        // next.bgm.tv (p1) 与 api.bgm.tv (v0) 可能使用不同的镜像服务，分开配置。
        // 未配置 next 镜像时回退到旧的统一镜像配置，兼容已有部署。
        String mirror = BANGUMI_NEXT_BASE.equals(defaultBase)
                ? siteConfigService.getBangumiNextMirrorBaseUrl()
                : siteConfigService.getBangumiMirrorBaseUrl();
        if (!StringUtils.hasText(mirror) && BANGUMI_NEXT_BASE.equals(defaultBase)) {
            mirror = siteConfigService.getBangumiMirrorBaseUrl();
        }
        if (StringUtils.hasText(mirror)) {
            return mirror.endsWith("/") ? mirror.substring(0, mirror.length() - 1) : mirror;
        }
        return defaultBase;
    }
}
