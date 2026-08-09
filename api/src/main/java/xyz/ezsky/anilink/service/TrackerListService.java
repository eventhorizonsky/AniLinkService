package xyz.ezsky.anilink.service;

import jakarta.annotation.PostConstruct;
import lombok.extern.log4j.Log4j2;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import xyz.ezsky.anilink.model.vo.TrackerListStatusVO;
import xyz.ezsky.anilink.schedule.ScheduledTaskDefinition;
import xyz.ezsky.anilink.schedule.ScheduledTaskRegistry;

import java.net.InetSocketAddress;
import java.net.Proxy;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Tracker 列表订阅服务。
 *
 * <p>订阅外部 Tracker 列表（每行一个 URL 的纯文本），定期拉取并缓存到内存，
 * 供 {@link ResourceDownloadService} 在创建新下载任务时合并到磁力链接。
 * 拉取使用 RSS 代理配置，失败时保留上一次成功的结果。</p>
 */
@Log4j2
@Service
public class TrackerListService {

    private static final long DEFAULT_REFRESH_DELAY_MILLIS = 6 * 60 * 60 * 1000L;
    private static final int MAX_TRACKERS = 5000;

    @Autowired
    private SiteConfigService siteConfigService;

    @Autowired
    private ScheduledTaskRegistry scheduledTaskRegistry;

    private final OkHttpClient baseClient = new OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .build();

    private volatile OkHttpClient proxyClient;
    private volatile String lastProxyHost;
    private volatile Integer lastProxyPort;

    private final AtomicBoolean refreshing = new AtomicBoolean(false);
    private volatile List<String> subscribedTrackers = List.of();
    private volatile Instant lastFetchedAt;
    private volatile String lastError;
    private volatile int lastTrackerCount;

    @PostConstruct
    public void initialize() {
        scheduledTaskRegistry.register(new ScheduledTaskDefinition(
                "tracker-list-refresh",
                "Tracker 列表订阅刷新",
                "定期拉取订阅的 Tracker 列表并合并到新下载任务附加 Tracker。",
                ScheduledTaskDefinition.TYPE_FIXED_DELAY,
                null,
                DEFAULT_REFRESH_DELAY_MILLIS,
                this::refreshNow,
                true
        ));
        Thread thread = new Thread(this::refreshNow, "anilink-tracker-list-init");
        thread.setDaemon(true);
        thread.start();
    }

    public List<String> getSubscribedTrackers() {
        return subscribedTrackers;
    }

    public TrackerListStatusVO getStatus() {
        TrackerListStatusVO vo = new TrackerListStatusVO();
        vo.setUrl(siteConfigService.getResourceTrackerListUrl());
        vo.setEnabled(!parseConfiguredUrls().isEmpty());
        vo.setRefreshing(refreshing.get());
        vo.setLastFetchedAt(lastFetchedAt);
        vo.setLastError(lastError);
        vo.setTrackerCount(lastTrackerCount);
        vo.setTrackers(new ArrayList<>(subscribedTrackers));
        return vo;
    }

    public TrackerListStatusVO refreshNow() {
        if (!refreshing.compareAndSet(false, true)) {
            return getStatus();
        }
        try {
            List<String> urls = parseConfiguredUrls();
            if (urls.isEmpty()) {
                subscribedTrackers = List.of();
                lastTrackerCount = 0;
                lastFetchedAt = null;
                lastError = null;
                return getStatus();
            }
            Set<String> merged = new LinkedHashSet<>();
            List<String> errors = new ArrayList<>();
            int success = 0;
            for (String url : urls) {
                try {
                    merged.addAll(fetchTrackerList(url));
                    success++;
                } catch (Exception e) {
                    log.warn("Tracker list fetch failed, url={}", url, e);
                    errors.add(url + ": " + e.getMessage());
                }
            }
            if (success > 0 && !merged.isEmpty()) {
                List<String> result = new ArrayList<>(merged);
                if (result.size() > MAX_TRACKERS) {
                    result = result.subList(0, MAX_TRACKERS);
                }
                subscribedTrackers = List.copyOf(result);
                lastTrackerCount = result.size();
                lastFetchedAt = Instant.now();
                lastError = errors.isEmpty() ? null : String.join("\n", errors);
                log.info("Tracker list refreshed, urls={}, success={}, count={}", urls.size(), success, lastTrackerCount);
            } else {
                lastError = errors.isEmpty() ? "未从订阅地址解析到 Tracker" : String.join("\n", errors);
                log.warn("Tracker list refresh got no trackers, urls={}, errors={}", urls.size(), errors);
            }
        } finally {
            refreshing.set(false);
        }
        return getStatus();
    }

    private List<String> parseConfiguredUrls() {
        List<String> urls = new ArrayList<>();
        String configured = siteConfigService.getResourceTrackerListUrl();
        if (configured == null || configured.isBlank()) {
            return urls;
        }
        for (String raw : configured.split("[\\r\\n,]")) {
            String url = raw == null ? "" : raw.trim();
            if (url.isEmpty()) {
                continue;
            }
            if (url.startsWith("http://") || url.startsWith("https://")) {
                urls.add(url);
            } else {
                log.warn("Ignoring invalid tracker list URL: {}", url);
            }
        }
        return urls;
    }

    private List<String> fetchTrackerList(String url) throws Exception {
        Request request = new Request.Builder().url(url).get().build();
        try (Response response = buildClientWithProxy().newCall(request).execute()) {
            if (!response.isSuccessful() || response.body() == null) {
                throw new IllegalStateException("HTTP " + response.code());
            }
            String body = response.body().string();
            List<String> trackers = new ArrayList<>();
            for (String line : body.split("[\\r\\n]+")) {
                String tracker = line.trim();
                if (tracker.isEmpty() || tracker.startsWith("#")) {
                    continue;
                }
                if (isValidTrackerUrl(tracker)) {
                    trackers.add(tracker);
                }
            }
            return trackers;
        }
    }

    private boolean isValidTrackerUrl(String tracker) {
        int schemeEnd = tracker.indexOf("://");
        if (schemeEnd <= 0) {
            return false;
        }
        String scheme = tracker.substring(0, schemeEnd).toLowerCase(Locale.ROOT);
        return scheme.equals("udp") || scheme.equals("http") || scheme.equals("https")
                || scheme.equals("ws") || scheme.equals("wss");
    }

    private OkHttpClient buildClientWithProxy() {
        String host = siteConfigService.getRssProxyHost();
        Integer port = siteConfigService.getRssProxyPort();
        if (host == null || host.isBlank() || port == null || port <= 0) {
            return baseClient;
        }
        String trimmedHost = host.trim();
        if (proxyClient != null && trimmedHost.equals(lastProxyHost) && port.equals(lastProxyPort)) {
            return proxyClient;
        }
        synchronized (this) {
            if (proxyClient != null && trimmedHost.equals(lastProxyHost) && port.equals(lastProxyPort)) {
                return proxyClient;
            }
            Proxy proxy = new Proxy(Proxy.Type.HTTP, new InetSocketAddress(trimmedHost, port));
            proxyClient = baseClient.newBuilder().proxy(proxy).build();
            lastProxyHost = trimmedHost;
            lastProxyPort = port;
            return proxyClient;
        }
    }
}
