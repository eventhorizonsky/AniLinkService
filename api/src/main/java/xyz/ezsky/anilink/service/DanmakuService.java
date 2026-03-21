package xyz.ezsky.anilink.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import xyz.ezsky.anilink.model.entity.ApiCache;
import xyz.ezsky.anilink.repository.ApiCacheRepository;
import xyz.ezsky.anilink.util.DandanClientUtil;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 弹幕服务
 * 
 * <p>提供弹幕获取功能，带数据库缓存</p>
 */
@Service
@Log4j2
public class DanmakuService {

    private static final String DANDAN_BASE = "https://api.dandanplay.net";
    // 弹幕缓存时间：30分钟
    private static final long COMMENT_CACHE_TTL_MINUTES = 30;
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final Set<String> COMMENT_REFRESHING_KEYS = ConcurrentHashMap.newKeySet();

    @Autowired
    private ApiCacheRepository apiCacheRepository;

    @Autowired
    private DandanClientUtil dandanClientUtil;

    /**
     * 获取指定弹幕库的弹幕（带缓存）
     *
     * @param episodeId 弹幕库ID
     * @param withRelated 是否包含第三方关联弹幕
     * @return 弹幕JSON字符串
     */
    public String getCommentByEpisodeId(Long episodeId, Boolean withRelated) {
        String cacheKey = buildCommentCacheKey(episodeId, withRelated);
        LocalDateTime now = LocalDateTime.now();

        // 检查有效缓存
        Optional<ApiCache> validCache = apiCacheRepository.findByCacheKeyAndExpireTimeAfter(cacheKey, now);
        String validCacheValue = extractUsableJsonCacheValue(validCache, cacheKey);
        if (validCacheValue != null) {
            log.debug("返回缓存的弹幕数据: episodeId={}", episodeId);
            return validCacheValue;
        }

        // 检查过期缓存：存在则立即返回，并异步刷新（防重入）。
        Optional<ApiCache> staleCache = apiCacheRepository.findByCacheKey(cacheKey);
        String staleCacheValue = extractUsableJsonCacheValue(staleCache, cacheKey);
        if (staleCacheValue != null) {
            refreshCommentCacheAsync(cacheKey, episodeId, withRelated);
            log.debug("返回过期缓存并触发异步刷新: episodeId={}, withRelated={}", episodeId, withRelated);
            return staleCacheValue;
        }

        // 首次请求（无缓存）同步请求上游。
        String freshBody = fetchCommentFromUpstream(episodeId, withRelated);
        if (StringUtils.hasText(freshBody)) {
            upsertCache(cacheKey, freshBody, now.plusMinutes(COMMENT_CACHE_TTL_MINUTES));
            log.info("首次请求同步拉取并缓存弹幕: episodeId={}, withRelated={}", episodeId, withRelated);
            return freshBody;
        }

        return null;
    }

    private String extractUsableJsonCacheValue(Optional<ApiCache> cacheOpt, String cacheKey) {
        if (cacheOpt.isEmpty()) {
            return null;
        }

        String value = cacheOpt.get().getCacheValue();
        if (!StringUtils.hasText(value)) {
            return null;
        }

        String trimmed = value.trim();
        if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
            return value;
        }

        log.warn("Ignoring invalid api cache payload for cacheKey={}, preview={}", cacheKey, trimmed);
        return null;
    }

    private void refreshCommentCacheAsync(String cacheKey, Long episodeId, Boolean withRelated) {
        // 防重入：同一个 cacheKey 同一时刻只允许一个刷新任务。
        if (!COMMENT_REFRESHING_KEYS.add(cacheKey)) {
            return;
        }

        CompletableFuture.runAsync(() -> {
            try {
                String freshBody = fetchCommentFromUpstream(episodeId, withRelated);
                if (StringUtils.hasText(freshBody)) {
                    upsertCache(cacheKey, freshBody, LocalDateTime.now().plusMinutes(COMMENT_CACHE_TTL_MINUTES));
                    log.info("异步刷新弹幕缓存成功: episodeId={}, withRelated={}", episodeId, withRelated);
                }
            } catch (Exception ex) {
                log.warn("异步刷新弹幕缓存失败: episodeId={}, withRelated={}", episodeId, withRelated, ex);
            } finally {
                COMMENT_REFRESHING_KEYS.remove(cacheKey);
            }
        });
    }

    private String fetchCommentFromUpstream(Long episodeId, Boolean withRelated) {
        String path = "/api/v2/comment/" + episodeId;
        Map<String, String> queryParams = Boolean.TRUE.equals(withRelated)
                ? Collections.singletonMap("withRelated", "true")
                : null;

        try {
            ResponseEntity<String> response = dandanClientUtil.get(DANDAN_BASE, path, queryParams);
            String responseBody = response.getBody();
            if (response.getStatusCode().is2xxSuccessful() && StringUtils.hasText(responseBody)) {
                return responseBody;
            }
            log.warn("弹幕请求返回非成功状态: episodeId={}, status={}", episodeId, response.getStatusCode());
        } catch (Exception ex) {
            log.error("弹幕请求失败: episodeId={}", episodeId, ex);
        }

        return null;
    }

    /**
     * 获取指定弹幕库的弹幕并转换为 Bilibili XML 结构（带缓存）。
     *
     * @param episodeId 弹幕库ID
     * @param withRelated 是否包含第三方关联弹幕
     * @return XML 字符串
     */
    public String getCommentXmlByEpisodeId(Long episodeId, Boolean withRelated) {
        String rawJson = getCommentByEpisodeId(episodeId, withRelated);
        if (!StringUtils.hasText(rawJson)) {
            return null;
        }

        try {
            JsonNode root = OBJECT_MAPPER.readTree(rawJson);
            JsonNode comments = root.path("comments");

            StringBuilder xml = new StringBuilder(4096);
            xml.append("<?xml version=\"1.0\"?>\n");
            xml.append("<i xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" xmlns:xsd=\"http://www.w3.org/2001/XMLSchema\">\n");
            xml.append("  <chatserver>chat.bilibili.com</chatserver>\n");
            xml.append("  <chatid>").append(episodeId).append("</chatid>\n");
            xml.append("  <mission>0</mission>\n");
            xml.append("  <maxlimit>8000</maxlimit>\n");
            xml.append("  <source>e-r</source>\n");
            xml.append("  <ds>0</ds>\n");
            xml.append("  <de>0</de>\n");
            xml.append("  <max_count>8000</max_count>\n");

            if (comments.isArray()) {
                for (JsonNode comment : comments) {
                    String p = escapeXml(comment.path("p").asText(""));
                    String m = escapeXml(comment.path("m").asText(""));
                    xml.append("  <d p=\"").append(p).append("\">").append(m).append("</d>\n");
                }
            }

            xml.append("</i>");
            return xml.toString();
        } catch (Exception e) {
            log.error("弹幕XML转换失败: episodeId={}", episodeId, e);
            return null;
        }
    }

    /**
     * 代理弹弹 /api/v2/search/episodes 接口。
     *
     * @param anime 动漫标题关键词
     * @param episode 剧集关键词
     * @param tmdbId TMDB ID
     * @return 原始 JSON
     */
    public String searchEpisodes(String anime, String episode, String tmdbId) {
        String path = "/api/v2/search/episodes";
        Map<String, String> queryParams = new HashMap<>();

        if (StringUtils.hasText(anime)) {
            queryParams.put("anime", anime.trim());
        }
        if (StringUtils.hasText(episode)) {
            queryParams.put("episode", episode.trim());
        }
        if (StringUtils.hasText(tmdbId)) {
            queryParams.put("tmdbId", tmdbId.trim());
        }

        if (!queryParams.containsKey("anime") && !queryParams.containsKey("tmdbId")) {
            throw new IllegalArgumentException("anime 和 tmdbId 至少提供一个");
        }

        ResponseEntity<String> response = dandanClientUtil.get(DANDAN_BASE, path, queryParams);
        if (!response.getStatusCode().is2xxSuccessful() || !StringUtils.hasText(response.getBody())) {
            return null;
        }
        return response.getBody();
    }

    /**
     * 构建弹幕缓存键
     */
    private String buildCommentCacheKey(Long episodeId, Boolean withRelated) {
        String key = "dandan:comment:" + episodeId;
        if (Boolean.TRUE.equals(withRelated)) {
            key += ":related";
        }
        return key;
    }

    /**
     * 更新或插入缓存
     */
    private void upsertCache(String cacheKey, String cacheValue, LocalDateTime expireTime) {
        LocalDateTime now = LocalDateTime.now();
        ApiCache cache = apiCacheRepository.findByCacheKey(cacheKey).orElseGet(ApiCache::new);
        
        if (cache.getId() == null) {
            cache.setCreatedAt(now);
        }
        
        cache.setCacheKey(cacheKey);
        cache.setCacheValue(cacheValue);
        cache.setExpireTime(expireTime);
        cache.setUpdatedAt(now);
        
        apiCacheRepository.save(cache);
    }

    /**
     * 进行最小化 XML 转义，避免无效 XML。
     */
    private String escapeXml(String value) {
        if (value == null) {
            return "";
        }
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }
}
