package xyz.ezsky.anilink.service;

import lombok.extern.log4j.Log4j2;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import xyz.ezsky.anilink.model.entity.Anime;
import xyz.ezsky.anilink.model.entity.AnimeFollow;
import xyz.ezsky.anilink.repository.AnimeFollowRepository;
import xyz.ezsky.anilink.repository.AnimeRepository;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Bangumi 镜像切换后的封面地址自愈。
 * <p>
 * 切换镜像后，旧镜像 CDN 入库的封面地址（anime.imageUrl / anime_follow.imageUrl）
 * 可能已失效。本服务在镜像地址变化时异步执行：
 * <ol>
 *   <li>汇总两表中非弹弹 CDN 的封面主机（通常即旧镜像主机或官方 lain.bgm.tv）；</li>
 *   <li>每个主机仅取一张图，用"新主机 + 路径"（host 替换，最常见）和
 *       "新镜像基址 + 相对路径"两种候选各探测一次，确认新镜像可服务后
 *       得到"旧前缀 → 新前缀"的替换规律；</li>
 *   <li>按规律批量改写数据库中的 URL（纯 DB 更新，不逐条发起请求）。</li>
 * </ol>
 * 全程 best-effort、异步、失败静默，不影响配置保存。
 */
@Service
@Log4j2
public class BangumiImageSelfHealService {

    /** 弹弹 / 本地 CDN 主机：不参与镜像替换 */
    private static final Set<String> EXCLUDED_HOSTS = Set.of(
            "assets.anixplayer.net",
            "image.dandanplay.com",
            "localhost",
            "127.0.0.1"
    );

    /** 官方 CDN 主机：未配置镜像时使用；切镜像时同样作为候选替换源 */
    private static final String OFFICIAL_CDN_HOST = "lain.bgm.tv";

    /** 探测时最多处理的主机数，防止极端数据下探测过多 */
    private static final int MAX_PROBE_HOSTS = 8;

    @Autowired
    private AnimeRepository animeRepository;

    @Autowired
    private AnimeFollowRepository animeFollowRepository;

    private final OkHttpClient probeClient = new OkHttpClient.Builder()
            .connectTimeout(6, TimeUnit.SECONDS)
            .readTimeout(6, TimeUnit.SECONDS)
            .followRedirects(true)
            .build();

    private final ExecutorService healExecutor = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "bangumi-image-heal");
        t.setDaemon(true);
        return t;
    });

    /**
     * 镜像地址变化后触发自愈（异步，best-effort）。
     *
     * @param oldMirrorBase 变更前的镜像基址（可为空）
     * @param newMirrorBase 变更后的镜像基址（可为空，表示清空镜像、回落到官方 CDN）
     */
    public void healAfterMirrorChange(String oldMirrorBase, String newMirrorBase) {
        String oldBase = normalizeBase(oldMirrorBase);
        String newBase = normalizeBase(newMirrorBase);
        if (Objects.equals(oldBase, newBase)) {
            return;
        }
        runHealTask(() -> doHeal(oldBase, newBase));
    }

    /**
     * 手动触发：将库中所有非弹弹 CDN 封面主机替换为当前镜像主机（未配置镜像则官方 CDN）。
     * 供后台在切换镜像后封面裂图时调用（异步，best-effort）。
     *
     * @param currentMirrorBase 当前已保存的镜像基址（可为空）
     */
    public void healAllToCurrentMirror(String currentMirrorBase) {
        String newBase = normalizeBase(currentMirrorBase);
        runHealTask(() -> doHeal("", newBase));
    }

    private void runHealTask(Runnable task) {
        CompletableFuture.runAsync(() -> {
            try {
                task.run();
            } catch (Exception e) {
                log.warn("Bangumi 镜像封面自愈失败: {}", e.getMessage());
            }
        }, healExecutor);
    }

    private void doHeal(String oldBase, String newBase) {
        // 1. 汇总两表封面 URL，按主机保留一条示例（不逐条请求）
        Map<String, String> sampleByHost = new LinkedHashMap<>();
        for (String url : collectImageUrls()) {
            String host = extractHost(url);
            if (host == null || EXCLUDED_HOSTS.contains(host)) {
                continue;
            }
            sampleByHost.putIfAbsent(host, url);
        }
        if (sampleByHost.isEmpty()) {
            log.debug("Bangumi 镜像自愈：库中没有需要处理的封面 URL");
            return;
        }

        // 候选目标主机（按优先级）：Bangumi 镜像的图片 CDN 与 API 是分离的
        // （官方 api.bgm.tv ↔ lain.bgm.tv，镜像同理 api.xxx ↔ lain.xxx），
        // 因此优先生成 lain. 变体，其次才是镜像基址主机本身。
        List<String> targetCandidates = buildTargetCandidates(newBase);
        if (targetCandidates.isEmpty()) {
            log.warn("Bangumi 镜像自愈：无法从新镜像基址解析主机: {}", newBase);
            return;
        }

        // 2. 逐主机探测替换规律（每主机最多 1~2 次请求）
        List<String[]> mappings = new ArrayList<>(); // {oldPrefix, newPrefix}
        String confirmedTarget = null; // 已确认可服务图片的目标主机，后续主机优先复用
        int hostCount = 0;
        for (Map.Entry<String, String> entry : sampleByHost.entrySet()) {
            if (hostCount >= MAX_PROBE_HOSTS) {
                break;
            }
            String host = entry.getKey();
            if (confirmedTarget != null && host.equals(confirmedTarget)) {
                continue;
            }
            String[] mapping = discoverMapping(entry.getValue(), host, oldBase, newBase,
                    targetCandidates, confirmedTarget);
            if (mapping != null) {
                mappings.add(mapping);
                confirmedTarget = mapping[1].substring("https://".length());
                log.info("Bangumi 镜像自愈：发现替换规律 {} -> {}", mapping[0], mapping[1]);
            }
            hostCount++;
        }
        if (mappings.isEmpty()) {
            log.debug("Bangumi 镜像自愈：未探测到可用替换规律（新镜像可能无法服务图片），跳过");
            return;
        }

        // 3. 批量改写数据库（纯 DB 更新）
        int followUpdated = applyToFollows(mappings);
        int animeUpdated = applyToAnime(mappings);
        log.info("Bangumi 镜像自愈完成：anime_follow 更新 {} 条，anime 更新 {} 条",
                followUpdated, animeUpdated);
    }

    /**
     * 构建候选目标主机（图片 CDN）：
     * - 镜像基址主机以 "api." 开头时，优先 "lain." + 域名其余部分（官方惯例）；
     * - 其次为镜像基址主机本身（部分镜像直接在 API 主机上提供图片）；
     * - 未配置镜像时回落到官方 CDN lain.bgm.tv。
     */
    private List<String> buildTargetCandidates(String newBase) {
        List<String> candidates = new ArrayList<>();
        if (StringUtils.hasText(newBase)) {
            String host = extractHost(newBase);
            if (host == null) {
                return candidates;
            }
            if (host.startsWith("api.")) {
                candidates.add("lain." + host.substring("api.".length()));
            }
            candidates.add(host);
        } else {
            candidates.add(OFFICIAL_CDN_HOST);
        }
        return candidates;
    }

    /**
     * 探测单个主机的替换规律。
     * 优先复用已确认的目标主机；否则按候选优先级逐个探测
     * （"https://候选主机 + 原路径"），最后兜底"旧基址前缀 → 新基址"。
     *
     * @return {oldPrefix, newPrefix}，探测失败返回 null
     */
    private String[] discoverMapping(String sampleUrl, String oldHost, String oldBase, String newBase,
                                     List<String> targetCandidates, String confirmedTarget) {
        // 按序组装待探测的目标主机（已确认的排最前）
        List<String> ordered = new ArrayList<>();
        if (confirmedTarget != null && !confirmedTarget.equals(oldHost)) {
            ordered.add(confirmedTarget);
        }
        for (String candidate : targetCandidates) {
            if (!ordered.contains(candidate) && !candidate.equals(oldHost)) {
                ordered.add(candidate);
            }
        }

        String relativePath = extractPathAndQuery(sampleUrl);
        for (String candidate : ordered) {
            String swapped = "https://" + candidate + relativePath;
            if (probe(swapped)) {
                return new String[]{"https://" + oldHost, "https://" + candidate};
            }
        }

        // 兜底：旧基址带路径前缀时，整基址替换（oldBase → newBase）
        if (StringUtils.hasText(oldBase) && StringUtils.hasText(newBase)
                && sampleUrl.startsWith(oldBase)) {
            String path = sampleUrl.substring(oldBase.length());
            if (!path.startsWith("/")) {
                path = "/" + path;
            }
            String baseSwapped = newBase + path;
            if (probe(baseSwapped)) {
                return new String[]{oldBase, newBase};
            }
        }
        return null;
    }

    private int applyToFollows(List<String[]> mappings) {
        int updated = 0;
        List<AnimeFollow> toSave = new ArrayList<>();
        for (AnimeFollow follow : animeFollowRepository.findAll()) {
            String url = follow.getImageUrl();
            if (!StringUtils.hasText(url)) {
                continue;
            }
            String replaced = replacePrefix(url, mappings);
            if (replaced != null && !replaced.equals(url)) {
                follow.setImageUrl(replaced);
                toSave.add(follow);
                updated++;
            }
        }
        if (!toSave.isEmpty()) {
            animeFollowRepository.saveAll(toSave);
        }
        return updated;
    }

    private int applyToAnime(List<String[]> mappings) {
        int updated = 0;
        List<Anime> toSave = new ArrayList<>();
        for (Anime anime : animeRepository.findAll()) {
            String url = anime.getImageUrl();
            if (!StringUtils.hasText(url)) {
                continue;
            }
            String replaced = replacePrefix(url, mappings);
            if (replaced != null && !replaced.equals(url)) {
                anime.setImageUrl(replaced);
                toSave.add(anime);
                updated++;
            }
        }
        if (!toSave.isEmpty()) {
            animeRepository.saveAll(toSave);
        }
        return updated;
    }

    private String replacePrefix(String url, List<String[]> mappings) {
        for (String[] mapping : mappings) {
            if (url.startsWith(mapping[0])) {
                return mapping[1] + url.substring(mapping[0].length());
            }
            // 兼容协议相对地址（//host/...），替换后统一为 https
            String protoRelativeOld = "//" + mapping[0].substring("https://".length());
            if (url.startsWith(protoRelativeOld)) {
                return mapping[1] + url.substring(protoRelativeOld.length());
            }
        }
        return null;
    }

    private List<String> collectImageUrls() {
        List<String> urls = new ArrayList<>();
        animeFollowRepository.findAll().forEach(f -> {
            if (StringUtils.hasText(f.getImageUrl())) {
                urls.add(f.getImageUrl());
            }
        });
        animeRepository.findAll().forEach(a -> {
            if (StringUtils.hasText(a.getImageUrl())) {
                urls.add(a.getImageUrl());
            }
        });
        return urls;
    }

    private boolean probe(String url) {
        Request request = new Request.Builder()
                .url(url)
                .header("Range", "bytes=0-0")
                .header("User-Agent", "AniLinkService/1.0 (https://github.com/AniLink)")
                .get()
                .build();
        try (Response response = probeClient.newCall(request).execute()) {
            int code = response.code();
            return code == 206 || (code >= 200 && code < 300);
        } catch (IOException e) {
            log.debug("Bangumi 镜像自愈探测失败: {} ({})", url, e.getMessage());
            return false;
        }
    }

    private String extractHost(String url) {
        String normalized = url;
        if (normalized.startsWith("//")) {
            normalized = "https:" + normalized;
        }
        try {
            URI uri = new URI(normalized);
            return uri.getHost() != null ? uri.getHost().toLowerCase(Locale.ROOT) : null;
        } catch (Exception e) {
            return null;
        }
    }

    private String extractPathAndQuery(String url) {
        String normalized = url;
        if (normalized.startsWith("//")) {
            normalized = "https:" + normalized;
        }
        try {
            URI uri = new URI(normalized);
            String path = uri.getPath();
            if (path == null || path.isBlank()) {
                path = "/";
            }
            if (uri.getQuery() != null && !uri.getQuery().isBlank()) {
                path += "?" + uri.getQuery();
            }
            return path;
        } catch (Exception e) {
            return "/";
        }
    }

    private String normalizeBase(String base) {
        if (base == null) {
            return "";
        }
        String trimmed = base.trim();
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }
}
