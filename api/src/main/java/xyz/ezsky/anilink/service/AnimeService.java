package xyz.ezsky.anilink.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import xyz.ezsky.anilink.model.entity.ApiCache;
import xyz.ezsky.anilink.model.entity.Anime;
import xyz.ezsky.anilink.model.entity.MediaFile;
import xyz.ezsky.anilink.model.vo.AnimeVO;
import xyz.ezsky.anilink.model.vo.EpisodeVO;
import xyz.ezsky.anilink.model.vo.PageVO;
import xyz.ezsky.anilink.repository.ApiCacheRepository;
import xyz.ezsky.anilink.repository.AnimeRepository;
import xyz.ezsky.anilink.repository.MediaFileRepository;
import xyz.ezsky.anilink.util.DandanClientUtil;
import lombok.extern.log4j.Log4j2;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 动漫管理服务。
 * 
 * <p>提供动漫查询以及根据动漫获取视频库中的剧集功能。</p>
 */
@Service
@Log4j2
public class AnimeService {

    private static final long BANGUMI_CACHE_TTL_MINUTES = 360;

    @Autowired
    private AnimeRepository animeRepository;

    @Autowired
    private MediaFileRepository mediaFileRepository;

    @Autowired
    private ApiCacheRepository apiCacheRepository;

    @Autowired
    private DandanClientUtil dandanClientUtil;

    @Autowired
    private SiteConfigService siteConfigService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    // setter used by unit tests
    public void setMediaFileRepository(MediaFileRepository mediaFileRepository) {
        this.mediaFileRepository = mediaFileRepository;
    }

    /**
     * 获取所有动漫列表
     *
     * @return 动漫信息列表
     */
    public List<AnimeVO> getAllAnimes() {
        List<Anime> animes = animeRepository.findAll();
        return animes.stream()
                .map(this::convertToVO)
                .collect(Collectors.toList());
    }

    /**
     * 获取分页的动漫列表
     *
     * @param page 页码（从1开始）
     * @param pageSize 每页大小
     * @param keyword 搜索关键词（可选，为null时查询所有）
     * @return 分页结果VO
     */
    public PageVO<AnimeVO> getAnimesPage(int page, int pageSize, String keyword) {
        // 创建分页请求（Spring Data JPA中page从0开始）
        Pageable pageable = PageRequest.of(page - 1, pageSize);
        
        // 根据是否有关键词选择查询方法
        Page<Anime> animePage;
        if (keyword != null && !keyword.trim().isEmpty()) {
            animePage = animeRepository.findByTitleContainingIgnoreCase(keyword.trim(), pageable);
        } else {
            animePage = animeRepository.findAll(pageable);
        }
        
        // 将实体转换为VO
        List<AnimeVO> data = animePage.getContent().stream()
                .map(this::convertToVO)
                .collect(Collectors.toList());
        
        // 构建返回结果
        return PageVO.<AnimeVO>builder()
                .content(data)
                .totalElements(animePage.getTotalElements())
                .totalPages(animePage.getTotalPages())
                .currentPage(page)
                .pageSize(pageSize)
                .hasNext(animePage.hasNext())
                .hasPrevious(animePage.hasPrevious())
                .build();
    }


    /**
     * 根据动漫ID获取动漫详情。
     *
     * @param animeId 弹幕库动漫ID
     * @return 动漫信息，若无则返回 null
     */
    public AnimeVO getAnimeById(Long animeId) {
        return animeRepository.findByAnimeId(animeId)
                .map(this::convertToVO)
                .orElse(null);
    }

    /**
     * 确保 Anime 记录存在。
     * 已存在则跳过，不存在则用提供的信息创建。
     *
     * @param animeId  弹弹动漫ID
     * @param title    动漫标题
     * @param type     动漫类型（可为 null）
     * @param imageUrl 封面图URL（可为 null）
     */
    public void ensureAnimeExists(Long animeId, String title, String type, String imageUrl) {
        if (animeId == null) return;
        if (animeRepository.findByAnimeId(animeId).isPresent()) return;

        Anime anime = new Anime();
        anime.setAnimeId(animeId);
        anime.setTitle(title != null && !title.isBlank() ? title : "未知动漫");
        anime.setType(type);
        anime.setImageUrl(imageUrl);
        saveAnimeSafely(anime, "ensureAnimeExists", animeId);
    }

    /**
     * 用 raw JSON 中的完整信息补建/更新 Anime 记录。
     * 解析 type、typeDescription、imageUrl 等字段，比 media file 兜底更完整。
     *
     * @param animeId 弹弹动漫ID
     * @param rawJson 从缓存或上游获取的原始 JSON
     */
    private void upsertAnimeFromRawJson(Long animeId, String rawJson) {
        // Only create/enrich Anime records when the media library actually contains episodes
        // for this anime, so raw-json fetches never produce orphan records without episodes.
        if (animeId == null) {
            return;
        }
        if (!mediaFileRepository.existsByAnimeId(animeId)) {
            log.debug("Skip upsert anime from raw JSON: no media files found for animeId={}", animeId);
            return;
        }
        try {
            JsonNode root = objectMapper.readTree(rawJson);
            // 弹弹 bangumi API 响应格式为 { success, bangumi: {...} }，先解包
            JsonNode data = root.has("bangumi") ? root.get("bangumi") : root;
            String title = readTextField(data, "animeTitle", "未知动漫");
            String type = readTextField(data, "type", null);
            String typeDesc = readTextField(data, "typeDescription", null);
            String imageUrl = readTextField(data, "imageUrl", null);
            String bangumiUrl = readTextField(data, "bangumiUrl", null);
            Long bangumiSubjectId = extractBangumiSubjectId(bangumiUrl);

            // 优先用 typeDescription（更可读），没有则用 type
            String resolvedType = (typeDesc != null && !typeDesc.isBlank()) ? typeDesc : type;

            animeRepository.findByAnimeId(animeId).ifPresentOrElse(
                    existing -> {
                        // 已存在则补全缺失字段
                        boolean changed = false;
                        if ((existing.getType() == null || existing.getType().isBlank()) && resolvedType != null) {
                            existing.setType(resolvedType);
                            changed = true;
                        }
                        if ((existing.getImageUrl() == null || existing.getImageUrl().isBlank()) && imageUrl != null) {
                            existing.setImageUrl(imageUrl);
                            changed = true;
                        }
                        if (existing.getBangumiSubjectId() == null && bangumiSubjectId != null) {
                            existing.setBangumiSubjectId(bangumiSubjectId);
                            changed = true;
                        }
                        if (changed) {
                            existing.setUpdatedAt(LocalDateTime.now());
                            animeRepository.save(existing);
                            log.info("Enriched existing anime record: animeId={}, type={}, imageUrl={}, bangumiSubjectId={}",
                                    animeId, resolvedType, imageUrl, bangumiSubjectId);
                        }
                    },
                    () -> {
                        // 不存在则创建完整记录
                        Anime anime = new Anime();
                        anime.setAnimeId(animeId);
                        anime.setTitle(title);
                        anime.setType(resolvedType);
                        anime.setImageUrl(imageUrl);
                        anime.setBangumiSubjectId(bangumiSubjectId);
                        saveAnimeSafely(anime, "upsertAnimeFromRawJson", animeId);
                    }
            );
        } catch (Exception e) {
            log.warn("Failed to upsert anime from raw JSON for animeId={}", animeId, e);
        }
    }

    private String readTextField(JsonNode node, String fieldName, String defaultValue) {
        JsonNode field = node.get(fieldName);
        if (field != null && !field.isNull()) {
            String text = field.asText(null);
            if (text != null && !text.isBlank()) {
                return text;
            }
        }
        return defaultValue;
    }

    private static final Pattern BANGUMI_SUBJECT_ID_PATTERN = Pattern.compile("/subject/(\\d+)");

    /**
     * 从 Bangumi URL 中提取 subject ID。
     * 例如: "https://bgm.tv/subject/12345" → 12345L
     *
     * @param bangumiUrl Bangumi 条目 URL，可能为 null
     * @return subject ID，无法提取时返回 null
     */
    private Long extractBangumiSubjectId(String bangumiUrl) {
        if (bangumiUrl == null || bangumiUrl.isBlank()) {
            return null;
        }
        Matcher matcher = BANGUMI_SUBJECT_ID_PATTERN.matcher(bangumiUrl);
        if (matcher.find()) {
            try {
                return Long.parseLong(matcher.group(1));
            } catch (NumberFormatException e) {
                log.warn("Failed to parse Bangumi subject ID from URL: {}", bangumiUrl);
            }
        }
        return null;
    }

    /**
     * 兜底：从媒体库中提取动漫信息来创建 Anime 记录（仅有 title，无 type/imageUrl）。
     * 仅在完全拿不到 raw JSON 时使用。
     *
     * @param animeId 弹弹动漫ID
     * @return 新创建的 Anime，失败或无媒体文件则返回 null
     */
    private Anime tryCreateAnimeFromMediaFiles(Long animeId) {
        try {
            Optional<MediaFile> mediaFileOpt = mediaFileRepository.findFirstByAnimeId(animeId);
            if (mediaFileOpt.isEmpty()) {
                log.debug("No media files found for animeId={}, skip lazy create", animeId);
                return null;
            }
            MediaFile mf = mediaFileOpt.get();
            Anime anime = new Anime();
            anime.setAnimeId(animeId);
            anime.setTitle(mf.getAnimeTitle() != null && !mf.getAnimeTitle().isBlank()
                    ? mf.getAnimeTitle() : "未知动漫");
            if (saveAnimeSafely(anime, "tryCreateAnimeFromMediaFiles", animeId)) {
                log.info("Lazy-created anime from media files: animeId={}, title={}, sourceFileId={}",
                        animeId, anime.getTitle(), mf.getId());
            }
            return anime;
        } catch (Exception e) {
            log.error("Failed to lazy-create anime from media files for animeId={}", animeId, e);
            return null;
        }
    }

    /**
     * 安全保存 Anime，遇到并发插入导致的唯一约束冲突时静默跳过。
     *
     * @return true 表示保存成功，false 表示冲突（已有其他线程先插入了）
     */
    private boolean saveAnimeSafely(Anime anime, String caller, Long animeId) {
        try {
            animeRepository.save(anime);
            return true;
        } catch (DataIntegrityViolationException e) {
            log.debug("Anime already exists (concurrent insert from {}): animeId={}", caller, animeId);
            return false;
        }
    }

    /**
     * 根据动漫ID获取视频库中该动漫的剧集（数据库分页）
     *
     * @param animeId 弹幕库动漫ID
     * @param page 页码（从1开始）
     * @param pageSize 每页大小
     * @return 分页结果VO（按媒体文件ID升序）
     */
    public PageVO<EpisodeVO> getEpisodesByAnimeId(Long animeId, int page, int pageSize) {
        Pageable pageable = PageRequest.of(page - 1, pageSize);
        // 指定按照 id 升序排序
        pageable = PageRequest.of(page - 1, pageSize, org.springframework.data.domain.Sort.by("episodeId").ascending());

        Page<MediaFile> mediaPage = mediaFileRepository.findByAnimeId(animeId, pageable);
        List<EpisodeVO> episodes = mediaPage.getContent().stream()
                .map(this::convertToEpisodeVO)
                .collect(Collectors.toList());

        return PageVO.<EpisodeVO>builder()
                .content(episodes)
                .totalElements(mediaPage.getTotalElements())
                .totalPages(mediaPage.getTotalPages())
                .currentPage(page)
                .pageSize(pageSize)
                .hasNext(mediaPage.hasNext())
                .hasPrevious(mediaPage.hasPrevious())
                .build();
    }

    /**
     * 根据数据库ID获取动漫详情
     *
     * @param id 数据库ID
     * @return 动漫信息
     */
    public AnimeVO getAnimeByDbId(Long id) {
        return animeRepository.findById(id)
                .map(this::convertToVO)
                .orElse(null);
    }

    /**
     * 根据动漫ID获取原始JSON数据。
     * 同时作为兜底：优先用 raw JSON 中的完整信息补建 Anime 记录（含 type、imageUrl），
     * 拿不到 raw JSON 时退而用媒体库中的基本信息。
     *
     * @param animeId 动漫ID
     * @return 原始JSON数据
     */
    public String getRawJsonByAnimeId(Long animeId) {
        String cacheKey = buildBangumiCacheKey(animeId);
        LocalDateTime now = LocalDateTime.now();

        // 路径 A：命中有效缓存 → 用缓存数据补建
        Optional<ApiCache> validCache = apiCacheRepository.findByCacheKeyAndExpireTimeAfter(cacheKey, now);
        String validCacheValue = extractUsableJsonCacheValue(validCache, cacheKey);
        if (validCacheValue != null) {
            upsertAnimeFromRawJson(animeId, validCacheValue);
            return validCacheValue;
        }

        // 路径 B：缓存过期或不存在，请求上游
        Optional<ApiCache> staleCache = apiCacheRepository.findByCacheKey(cacheKey);
        String path = "/api/v2/bangumi/" + animeId;
        try {
            ResponseEntity<String> response = dandanClientUtil.get(siteConfigService.getDandanBaseUrl(), path);
            String responseBody = response.getBody();
            if (response.getStatusCode().is2xxSuccessful() && StringUtils.hasText(responseBody)) {
                upsertCache(cacheKey, responseBody, now.plusMinutes(BANGUMI_CACHE_TTL_MINUTES));
                upsertAnimeFromRawJson(animeId, responseBody);
                return responseBody;
            }
            log.warn("Dandan bangumi request returned non-success status for animeId={}, status={}",
                    animeId, response.getStatusCode());
        } catch (Exception ex) {
            log.error("Dandan bangumi request failed for animeId={}", animeId, ex);
        }

        // 路径 C：上游失败，返回过期缓存兜底 → 过期缓存也是完整数据
        String staleCacheValue = extractUsableJsonCacheValue(staleCache, cacheKey);
        if (staleCacheValue != null) {
            log.warn("Returning stale api cache for animeId={} due to upstream failure", animeId);
            upsertAnimeFromRawJson(animeId, staleCacheValue);
            return staleCacheValue;
        }

        // 路径 D：完全无数据，从媒体库兜底基本信息
        tryCreateAnimeFromMediaFiles(animeId);
        return null;
    }

    /**
     * 通过 Bangumi subjectId 获取番剧 raw JSON。
     * 调用 Dandan 的 /api/v2/bangumi/bgmtv/{subjectId} 接口，
     * 返回结构与普通 animeId 接口相同。
     *
     * @param subjectId Bangumi subject ID
     * @return 原始 JSON 字符串，找不到对应番剧时返回 null
     */
    public String getRawJsonByBangumiSubjectId(Long subjectId) {
        String cacheKey = "dandan:bgmtv:" + subjectId;
        LocalDateTime now = LocalDateTime.now();

        // 检查缓存
        Optional<ApiCache> validCache = apiCacheRepository.findByCacheKeyAndExpireTimeAfter(cacheKey, now);
        String validValue = extractUsableJsonCacheValue(validCache, cacheKey);
        if (validValue != null) {
            // 从响应中提取 animeId 并补建/更新
            Long animeId = extractAnimeIdFromBangumiResponse(validValue);
            if (animeId != null) {
                upsertAnimeFromRawJson(animeId, validValue);
            }
            return validValue;
        }

        String path = "/api/v2/bangumi/bgmtv/" + subjectId;
        try {
            ResponseEntity<String> response = dandanClientUtil.get(siteConfigService.getDandanBaseUrl(), path);
            String responseBody = response.getBody();
            if (response.getStatusCode().is2xxSuccessful() && StringUtils.hasText(responseBody)) {
                upsertCache(cacheKey, responseBody, now.plusMinutes(BANGUMI_CACHE_TTL_MINUTES));
                Long animeId = extractAnimeIdFromBangumiResponse(responseBody);
                if (animeId != null) {
                    upsertAnimeFromRawJson(animeId, responseBody);
                }
                return responseBody;
            }
            log.warn("Dandan bgmtv request returned non-success for subjectId={}, status={}",
                    subjectId, response.getStatusCode());
        } catch (Exception ex) {
            log.error("Dandan bgmtv request failed for subjectId={}", subjectId, ex);
        }

        // 过期缓存兜底
        Optional<ApiCache> staleCache = apiCacheRepository.findByCacheKey(cacheKey);
        String staleValue = extractUsableJsonCacheValue(staleCache, cacheKey);
        if (staleValue != null) {
            log.warn("Returning stale bgmtv cache for subjectId={}", subjectId);
            return staleValue;
        }
        return null;
    }

    /**
     * 通过 Bangumi subjectId 匹配本地番剧。
     * 先在本地番剧库查找；查不到再调用弹弹 bgmtv 接口查询并入库。
     * 该方法是用户点击时才触发（单次请求），避免同步时批量调用触发风控。
     *
     * @param subjectId Bangumi subject ID
     * @return 匹配到的本地 Anime；查不到对应番剧时返回 null
     */
    public Anime matchAnimeByBangumiSubjectId(Long subjectId) {
        if (subjectId == null) {
            log.info("[match] subjectId 为 null，无法匹配");
            return null;
        }
        log.info("[match] matchAnimeByBangumiSubjectId 开始 subjectId={}", subjectId);

        // 1. 先查本地番剧库
        Optional<Anime> local = animeRepository.findAll().stream()
                .filter(a -> subjectId.equals(a.getBangumiSubjectId()))
                .findFirst();
        if (local.isPresent()) {
            log.info("[match] 本地按 subjectId 命中 animeId={} title={}",
                    local.get().getAnimeId(), local.get().getTitle());
            return local.get();
        }
        log.info("[match] 本地按 subjectId 未命中，调弹弹 bgmtv subjectId={}", subjectId);

        // 2. 调弹弹 bgmtv 接口按 subjectId 获取番剧，直接用其 animeId 绑定（不要求本地已存在）
        String rawJson = getRawJsonByBangumiSubjectId(subjectId);
        if (rawJson == null) {
            log.warn("[match] 弹弹 bgmtv 返回 null subjectId={}", subjectId);
            return null;
        }
        Long animeId = extractAnimeIdFromBangumiResponse(rawJson);
        log.info("[match] 弹弹 bgmtv 提取 animeId={}，直接用于绑定", animeId);
        if (animeId == null) {
            return null;
        }
        // 直接返回含 animeId 的记录用于绑定，标题保持追番原有标题
        Anime remote = new Anime();
        remote.setAnimeId(animeId);
        return remote;
    }

    /**
     * 将 Bangumi subjectId 关联到本地番剧记录（若尚未关联）。
     *
     * @param animeId   本地番剧 ID
     * @param subjectId Bangumi subject ID
     */
    public void attachBangumiSubjectId(Long animeId, Long subjectId) {
        if (animeId == null || subjectId == null) {
            return;
        }
        animeRepository.findByAnimeId(animeId).ifPresent(anime -> {
            if (anime.getBangumiSubjectId() == null) {
                anime.setBangumiSubjectId(subjectId);
                anime.setUpdatedAt(LocalDateTime.now());
                animeRepository.save(anime);
                log.info("Attached bangumiSubjectId={} to anime {}", subjectId, animeId);
            }
        });
    }

    /**
     * 从 Dandan BangumiDetailsResponse 中提取 animeId。
     * 响应格式: { success: true, bangumi: { animeId: 18319, ... } }
     */
    private Long extractAnimeIdFromBangumiResponse(String json) {
        try {
            JsonNode root = objectMapper.readTree(json);
            JsonNode data = root.has("bangumi") ? root.get("bangumi") : root;
            JsonNode animeIdNode = data.get("animeId");
            if (animeIdNode != null && animeIdNode.isNumber()) {
                return animeIdNode.asLong();
            }
        } catch (Exception e) {
            log.warn("Failed to extract animeId from bgmtv response", e);
        }
        return null;
    }

    /**
     * 搜索弹弹番剧（代理 /api/v2/search/anime）。
     */
    public String searchDandanAnime(String keyword) {
        String path = "/api/v2/search/anime";
        try {
            ResponseEntity<String> response = dandanClientUtil.get(
                    siteConfigService.getDandanBaseUrl(), path,
                    java.util.Map.of("keyword", keyword, "v2", "true"));
            if (response.getStatusCode().is2xxSuccessful() && StringUtils.hasText(response.getBody())) {
                return response.getBody();
            }
        } catch (Exception e) {
            log.error("Dandan search failed for keyword={}", keyword, e);
        }
        return null;
    }

    /**
     * 获取弹弹 /api/v2/bangumi/shin 原始JSON数据（带数据库缓存）。
     *
     * @return 原始JSON字符串
     */
    public String getShinRawJson() {
        String cacheKey = "dandan:bangumi:shin";
        String path = "/api/v2/bangumi/shin";
        return getWithDbCache(cacheKey, path);
    }

    private String buildBangumiCacheKey(Long animeId) {
        return "dandan:bangumi:" + animeId;
    }

    private String getWithDbCache(String cacheKey, String path) {
        LocalDateTime now = LocalDateTime.now();
        Optional<ApiCache> validCache = apiCacheRepository.findByCacheKeyAndExpireTimeAfter(cacheKey, now);
        String validCacheValue = extractUsableJsonCacheValue(validCache, cacheKey);
        if (validCacheValue != null) {
            return validCacheValue;
        }

        Optional<ApiCache> staleCache = apiCacheRepository.findByCacheKey(cacheKey);
        try {
            ResponseEntity<String> response = dandanClientUtil.get(siteConfigService.getDandanBaseUrl(), path);
            String responseBody = response.getBody();
            if (response.getStatusCode().is2xxSuccessful() && StringUtils.hasText(responseBody)) {
                upsertCache(cacheKey, responseBody, now.plusMinutes(BANGUMI_CACHE_TTL_MINUTES));
                return responseBody;
            }
            log.warn("Dandan request returned non-success status for path={}, status={}",
                    path, response.getStatusCode());
        } catch (Exception ex) {
            log.error("Dandan request failed for path={}", path, ex);
        }

        String staleCacheValue = extractUsableJsonCacheValue(staleCache, cacheKey);
        if (staleCacheValue != null) {
            log.warn("Returning stale api cache for path={} due to upstream failure", path);
            return staleCacheValue;
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
     * 将 Anime 实体转换为 AnimeVO
     *
     * @param anime 动漫实体
     * @return 动漫视图对象
     */
    private AnimeVO convertToVO(Anime anime) {
        AnimeVO animeVO = new AnimeVO();
        BeanUtils.copyProperties(anime, animeVO);
        return animeVO;
    }

    /**
     * 将 MediaFile 实体转换为 EpisodeVO
     *
     * @param mediaFile 媒体文件实体
     * @return 剧集视图对象
     */
    private EpisodeVO convertToEpisodeVO(MediaFile mediaFile) {
        EpisodeVO episodeVO = new EpisodeVO();
        BeanUtils.copyProperties(mediaFile, episodeVO);
        return episodeVO;
    }
}
