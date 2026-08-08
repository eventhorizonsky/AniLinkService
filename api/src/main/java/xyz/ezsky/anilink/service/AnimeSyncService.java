package xyz.ezsky.anilink.service;

import jakarta.annotation.PostConstruct;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import xyz.ezsky.anilink.repository.AnimeRepository;
import xyz.ezsky.anilink.repository.MediaFileRepository;
import xyz.ezsky.anilink.schedule.ScheduledTaskDefinition;
import xyz.ezsky.anilink.schedule.ScheduledTaskRegistry;

import java.util.List;

/**
 * 动漫记录同步定时任务。
 *
 * <p>周期性执行两项清理/补录工作：</p>
 * <ol>
 *   <li><strong>补录缺失 Anime</strong>：媒体库中有剧集（media_file 含 animeId）、
 *       但 anime 表中对应动漫未入库的，调用弹弹接口（/api/v2/bangumi/{animeId}）拉取完整信息入库。
 *       调用频率限制为最多每秒 1 次，触发风控时由 spring-retry 自动退避重试。</li>
 *   <li><strong>清理孤儿 Anime</strong>：媒体库中已没有剧集的 Anime 记录，对其进行删除。</li>
 * </ol>
 */
@Log4j2
@Service
public class AnimeSyncService {

    /** 弹弹 bangumi 详情路径模板 */
    private static final String DANDAN_BANGUMI_PATH = "/api/v2/bangumi/%d";

    @Autowired
    private AnimeRepository animeRepository;

    @Autowired
    private MediaFileRepository mediaFileRepository;

    @Autowired
    private AnimeService animeService;

    @Autowired
    private DandanRateLimitedFetcher dandanRateLimitedFetcher;

    @Autowired
    private SiteConfigService siteConfigService;

    @Autowired
    private ScheduledTaskRegistry scheduledTaskRegistry;

    @PostConstruct
    public void registerScheduledTask() {
        scheduledTaskRegistry.register(new ScheduledTaskDefinition(
                "anime-sync",
                "番剧信息补全",
                "定期检查媒体库中的番剧信息是否完整，缺少的自动从弹弹补齐，并清理媒体库中已没有任何剧集的番剧记录。为避免触发接口风控，调用频率限制为每秒 1 次。",
                ScheduledTaskDefinition.TYPE_CRON,
                "0 15 */6 * * *",
                null,
                this::syncAnimeRecords
        ));
    }

    /**
     * 执行动漫记录同步任务。
     *
     * @return 执行结果摘要
     */
    public String syncAnimeRecords() {
        StringBuilder summary = new StringBuilder();

        // 1. 补录缺失 Anime
        List<Long> missingAnimeIds = mediaFileRepository.findAnimeIdsMissingInAnimeTable();
        log.info("动漫记录同步开始，待补录 Anime {} 个", missingAnimeIds.size());
        int imported = 0;
        int skipped = 0;
        boolean riskControlStopped = false;
        for (Long animeId : missingAnimeIds) {
            try {
                if (importAnime(animeId)) {
                    imported++;
                } else {
                    skipped++;
                }
            } catch (DandanRiskControlException e) {
                log.warn("动漫记录同步触发弹弹风控，中断补录：{}", e.getMessage());
                riskControlStopped = true;
                break;
            } catch (Exception e) {
                log.warn("动漫记录同步补录失败 animeId={}", animeId, e);
                skipped++;
            }
        }
        summary.append("补录缺失 Anime: 待补录 ").append(missingAnimeIds.size())
                .append(" 个，成功 ").append(imported)
                .append(" 个，跳过/失败 ").append(skipped).append(" 个");
        if (riskControlStopped) {
            summary.append("（因触发风控提前中断，剩余待下次执行）");
        }

        // 2. 清理孤儿 Anime
        int cleaned = cleanupAnimeWithoutEpisodes();
        summary.append("；清理无剧集 Anime ").append(cleaned).append(" 条");

        log.info("动漫记录同步完成：{}", summary);
        return summary.toString();
    }

    /**
     * 对单个 animeId 调用弹弹接口拉取详情并入库。
     *
     * @return true 表示成功入库；false 表示弹弹无数据（已用媒体库信息兜底）
     */
    private boolean importAnime(Long animeId) {
        ResponseEntity<String> response = dandanRateLimitedFetcher.get(
                siteConfigService.getDandanBaseUrl(), String.format(DANDAN_BANGUMI_PATH, animeId));

        if (response.getStatusCode().is2xxSuccessful() && StringUtils.hasText(response.getBody())) {
            animeService.saveBangumiCache(animeId, response.getBody());
            animeService.upsertAnimeFromRawJson(animeId, response.getBody());
            log.info("已通过弹弹接口补录 Anime animeId={}", animeId);
            return true;
        }

        log.warn("弹弹接口无该番剧数据 animeId={}, status={}，使用媒体库信息兜底", animeId, response.getStatusCode());
        animeService.ensureAnimeFromMediaFiles(animeId);
        return false;
    }

    /**
     * 删除媒体库中已无任何剧集的 Anime 记录。
     *
     * <p>注意：本方法被 {@link #syncAnimeRecords()} 通过自调用执行，Spring 事务代理不会生效，
     * 因此这里不依赖 @Transactional，事务由 {@link AnimeRepository#deleteByAnimeIdIn} 的
     * 自带 @Transactional + @Modifying 批量删除保证。</p>
     *
     * @return 删除数量
     */
    public int cleanupAnimeWithoutEpisodes() {
        List<Long> orphanAnimeIds = animeRepository.findAnimeIdsWithoutMediaFiles();
        if (orphanAnimeIds.isEmpty()) {
            return 0;
        }
        animeRepository.deleteByAnimeIdIn(orphanAnimeIds);
        log.info("清理媒体库中无剧集的 Anime 记录 {} 条", orphanAnimeIds.size());
        return orphanAnimeIds.size();
    }
}
