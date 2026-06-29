package xyz.ezsky.anilink.service;

import lombok.extern.log4j.Log4j2;
import okhttp3.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Proxy;
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
        ResponseEntity<String> response = execute(BANGUMI_NEXT_BASE, "GET", "/p1/subjects/" + subjectId + "/comments",
                null, null, Map.of("limit", String.valueOf(limit), "offset", String.valueOf(offset)));

        if (response.getStatusCode().is2xxSuccessful()) {
            return response.getBody();
        }
        return null;
    }

    public ResponseEntity<String> getMe(String accessToken) {
        return execute(BANGUMI_API_BASE, "GET", "/v0/me", accessToken, null, null);
    }

    public ResponseEntity<String> getUserCollection(String accessToken, String username, Long subjectId) {
        return execute(BANGUMI_API_BASE, "GET", "/v0/users/" + username + "/collections/" + subjectId, accessToken, null, null);
    }

    public ResponseEntity<String> postUserCollection(String accessToken, Long subjectId, String payloadJson) {
        return execute(BANGUMI_API_BASE, "POST", "/v0/users/-/collections/" + subjectId, accessToken, payloadJson, null);
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

        OkHttpClient client = buildClientWithProxy();

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

    private OkHttpClient buildClientWithProxy() {
        String host = siteConfigService.getBangumiProxyHost();
        Integer port = siteConfigService.getBangumiProxyPort();
        if (host == null || host.isBlank() || port == null || port <= 0) {
            return baseClient;
        }
        Proxy proxy = new Proxy(Proxy.Type.HTTP, new InetSocketAddress(host.trim(), port));
        return baseClient.newBuilder().proxy(proxy).build();
    }
}
