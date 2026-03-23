package xyz.ezsky.anilink.service;

import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
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
     * 根据动漫ID获取动漫详情
     *
     * @param animeId 弹幕库动漫ID
     * @return 动漫信息
     */
    public AnimeVO getAnimeById(Long animeId) {
        return animeRepository.findByAnimeId(animeId)
                .map(this::convertToVO)
                .orElse(null);
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
     * 根据动漫ID获取原始JSON数据
     *
     * @param animeId 动漫ID
     * @return 原始JSON数据
     */
    public String getRawJsonByAnimeId(Long animeId) {
        String cacheKey = buildBangumiCacheKey(animeId);
        LocalDateTime now = LocalDateTime.now();

        Optional<ApiCache> validCache = apiCacheRepository.findByCacheKeyAndExpireTimeAfter(cacheKey, now);
        String validCacheValue = extractUsableJsonCacheValue(validCache, cacheKey);
        if (validCacheValue != null) {
            return validCacheValue;
        }

        Optional<ApiCache> staleCache = apiCacheRepository.findByCacheKey(cacheKey);
        String path = "/api/v2/bangumi/" + animeId;
        try {
            ResponseEntity<String> response = dandanClientUtil.get(siteConfigService.getDandanBaseUrl(), path);
            String responseBody = response.getBody();
            if (response.getStatusCode().is2xxSuccessful() && StringUtils.hasText(responseBody)) {
                upsertCache(cacheKey, responseBody, now.plusMinutes(BANGUMI_CACHE_TTL_MINUTES));
                return responseBody;
            }
            log.warn("Dandan bangumi request returned non-success status for animeId={}, status={}",
                    animeId, response.getStatusCode());
        } catch (Exception ex) {
            log.error("Dandan bangumi request failed for animeId={}", animeId, ex);
        }

        String staleCacheValue = extractUsableJsonCacheValue(staleCache, cacheKey);
        if (staleCacheValue != null) {
            log.warn("Returning stale api cache for animeId={} due to upstream failure", animeId);
            return staleCacheValue;
        }

        // 最后退避到本地已存 raw_json，兼容历史数据。
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
