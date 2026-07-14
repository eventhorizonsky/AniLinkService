package xyz.ezsky.anilink.service;

import com.frostwire.jlibtorrent.TorrentInfo;
import lombok.extern.log4j.Log4j2;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import xyz.ezsky.anilink.model.dto.ResourceRssSubscriptionRequest;
import xyz.ezsky.anilink.model.dto.RssFilterPreviewRequest;
import xyz.ezsky.anilink.model.dto.ResourceSearchDownloadRequest;
import xyz.ezsky.anilink.model.entity.MediaLibrary;
import xyz.ezsky.anilink.model.entity.ResourceRssSubscription;
import xyz.ezsky.anilink.model.vo.ResourceSearchVO;
import xyz.ezsky.anilink.repository.MediaLibraryRepository;
import xyz.ezsky.anilink.repository.ResourceDownloadTaskRepository;
import xyz.ezsky.anilink.repository.ResourceRssSubscriptionRepository;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

@Log4j2
@Service
public class ResourceRssSubscriptionService {

    private static final Pattern MAGNET_PATTERN = Pattern.compile("(magnet:\\?xt=urn:btih:[A-Za-z0-9]+[^\\s\"'<>]*)", Pattern.CASE_INSENSITIVE);
    private static final int MAX_FETCHED_CONTENT_LENGTH = 200_000;
    private static final int MAX_ENTRY_LINES = 200;

    @Autowired
    private ResourceRssSubscriptionRepository rssRepository;

    @Autowired
    private MediaLibraryRepository mediaLibraryRepository;

    @Autowired
    private ResourceDownloadTaskRepository taskRepository;

    @Autowired
    private ResourceDownloadService resourceDownloadService;

    @Autowired
    private SiteConfigService siteConfigService;

    private final OkHttpClient baseClient = new OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .build();

    private volatile OkHttpClient proxyClient;
    private volatile String lastProxyHost;
    private volatile Integer lastProxyPort;

    public List<ResourceSearchVO.RssSubscriptionItem> listSubscriptions() {
        List<ResourceSearchVO.RssSubscriptionItem> list = new ArrayList<>();
        for (ResourceRssSubscription item : rssRepository.findAllByOrderByCreatedAtDesc()) {
            list.add(toVO(item));
        }
        return list;
    }

    public ResourceSearchVO.RssSubscriptionItem createSubscription(ResourceRssSubscriptionRequest request) {
        ResourceRssSubscription entity = new ResourceRssSubscription();
        applyRequest(entity, request);
        return toVO(rssRepository.save(entity));
    }

    public ResourceSearchVO.RssSubscriptionItem updateSubscription(Long id, ResourceRssSubscriptionRequest request) {
        ResourceRssSubscription entity = rssRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "订阅不存在"));
        applyRequest(entity, request);
        return toVO(rssRepository.save(entity));
    }

    public void deleteSubscription(Long id) {
        ResourceRssSubscription entity = rssRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "订阅不存在"));
        rssRepository.delete(entity);
    }

    public void triggerNow(Long id) {
        ResourceRssSubscription entity = rssRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "订阅不存在"));
        pollSubscription(entity);
    }

        public ResourceSearchVO.RssFetchedContent getLastFetchedContent(Long id) {
        ResourceRssSubscription entity = rssRepository.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "订阅不存在"));
        return ResourceSearchVO.RssFetchedContent.builder()
            .id(entity.getId())
            .name(entity.getName())
            .lastCheckedAt(entity.getLastCheckedAt())
            .lastFetchedContent(entity.getLastFetchedContent())
            .build();
        }

    @Scheduled(fixedDelay = 60000)
    public void pollEnabledSubscriptions() {
        List<ResourceRssSubscription> enabled = rssRepository.findByEnabledTrueOrderByCreatedAtAsc();
        for (ResourceRssSubscription item : enabled) {
            if (!isDue(item)) {
                continue;
            }
            pollSubscription(item);
        }
    }

    private boolean isDue(ResourceRssSubscription item) {
        Timestamp lastChecked = item.getLastCheckedAt();
        if (lastChecked == null) {
            return true;
        }
        long intervalMinutes = Math.max(1, item.getIntervalMinutes() == null ? 30 : item.getIntervalMinutes());
        long elapsedMs = System.currentTimeMillis() - lastChecked.getTime();
        return elapsedMs >= intervalMinutes * 60_000L;
    }

    private void pollSubscription(ResourceRssSubscription item) {
        item.setLastCheckedAt(Timestamp.from(Instant.now()));
        try {
            String xml = fetchRss(item.getFeedUrl());
            List<RssEntry> entries = parseEntries(xml);
            if (entries.isEmpty()) {
                item.setLastFetchedContent("解析结果:\n- 本次未识别到可处理条目（未提取到 magnet）");
                item.setLastError("RSS 没有解析到可处理的条目（请检查 RSS 格式或地址）");
                rssRepository.save(item);
                return;
            }
            log.info("RSS subscription {} got {} magnet entries", item.getName(), entries.size());
            int beforeFilterCount = entries.size();
            entries = applyFilters(entries, item.getIncludeFilter(), item.getExcludeFilter());
            int filteredOut = beforeFilterCount - entries.size();
            if (entries.isEmpty()) {
                item.setLastFetchedContent(buildParsedResultContent(beforeFilterCount, filteredOut, 0, 0, List.of()));
                item.setLastError("过滤后无匹配条目");
                rssRepository.save(item);
                return;
            }
            int created = 0;
            int duplicated = 0;
            List<String> resultLines = new ArrayList<>();
            for (RssEntry entry : entries) {
                if (entry.magnet == null || entry.magnet.isBlank()) {
                    continue;
                }
                if (taskRepository.existsByMagnet(entry.magnet)) {
                    duplicated++;
                    appendEntryLine(resultLines, "已存在", entry);
                    continue;
                }
                ResourceSearchDownloadRequest req = new ResourceSearchDownloadRequest(
                        entry.title != null ? entry.title : "RSS 资源",
                        entry.magnet,
                        entry.link,
                        null,
                        null,
                        null,
                        null,
                        item.getLibrary().getId()
                );
                resourceDownloadService.startDownload(req);
                created++;
                appendEntryLine(resultLines, "已创建", entry);
            }
            item.setLastFetchedContent(buildParsedResultContent(beforeFilterCount, filteredOut, created, duplicated, resultLines));
            item.setLastSuccessAt(Timestamp.from(Instant.now()));
            if (created == 0) {
                item.setLastError("本次检查未发现新磁链（可能都已存在）");
            } else {
                item.setLastError(null);
            }
            rssRepository.save(item);
            if (created > 0) {
                log.info("RSS subscription {} created {} task(s)", item.getName(), created);
            }
        } catch (Exception e) {
            item.setLastError(e.getMessage());
            rssRepository.save(item);
            log.warn("RSS subscription poll failed, id={}, name={}", item.getId(), item.getName(), e);
        }
    }

    private String fetchRss(String feedUrl) throws Exception {
        Request request = new Request.Builder().url(feedUrl).get().build();
        OkHttpClient client = buildClientWithProxy();
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful() || response.body() == null) {
                throw new IllegalStateException("RSS 请求失败: " + response.code());
            }
            return response.body().string();
        }
    }

    private OkHttpClient buildClientWithProxy() {
        String host = siteConfigService.getRssProxyHost();
        Integer port = siteConfigService.getRssProxyPort();
        if (host == null || host.isBlank() || port == null || port <= 0) {
            return baseClient;
        }
        String trimmedHost = host.trim();
        // Cache the proxy client to avoid rebuilding on every call
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

    private List<RssEntry> parseEntries(String xml) throws Exception {
        List<RssEntry> list = new ArrayList<>();
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setExpandEntityReferences(false);
        factory.setNamespaceAware(false);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        Document doc = factory.newDocumentBuilder().parse(new InputSource(new StringReader(xml)));
        List<Element> entryNodes = collectEntryNodes(doc);
        log.info("parseEntries: found {} entry nodes in RSS", entryNodes.size());
        for (Element item : entryNodes) {
            String title = firstNonBlank(textOf(item, "title"), textOf(item, "name"));
            String linkText = textOf(item, "link");
            String guid = textOf(item, "guid");
            String description = firstNonBlank(textOf(item, "description"), textOf(item, "content"), textOf(item, "summary"));

            List<String> candidateLinks = collectCandidateLinks(item, linkText, guid);
            String canonicalLink = candidateLinks.isEmpty() ? linkText : candidateLinks.get(0);

            String magnet = null;
            String magnetSource = null;
            for (String value : candidateLinks) {
                magnet = firstMagnet(value);
                if (magnet != null) {
                    magnetSource = "candidateLink";
                    break;
                }
            }
            if (magnet == null) {
                magnet = firstMagnet(description);
                if (magnet != null) {
                    magnetSource = "description";
                }
            }
            if (magnet == null) {
                for (String link : candidateLinks) {
                    magnet = tryConvertTorrentUrlToMagnet(link);
                    if (magnet != null) {
                        magnetSource = "torrent:" + link;
                        break;
                    }
                }
            }

            if (magnet != null) {
                list.add(new RssEntry(title, canonicalLink, magnet));
            } else {
                log.warn("parseEntries: no magnet for entry \"{}\", candidateLinks={}", title, candidateLinks);
            }
        }
        return list;
    }

    private List<Element> collectEntryNodes(Document doc) {
        List<Element> result = new ArrayList<>();
        NodeList nodes = doc.getElementsByTagName("*");
        for (int i = 0; i < nodes.getLength(); i++) {
            Node node = nodes.item(i);
            if (node.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }
            String name = node.getNodeName();
            if (matchesTag(name, "item") || matchesTag(name, "entry")) {
                result.add((Element) node);
            }
        }
        return result;
    }

    private List<String> collectCandidateLinks(Element item, String linkText, String guid) {
        LinkedHashSet<String> links = new LinkedHashSet<>();
        addIfPresent(links, linkText);
        addIfPresent(links, guid);

        NodeList children = item.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }
            Element element = (Element) child;
            String nodeName = element.getNodeName();
            if (matchesTag(nodeName, "link")) {
                addIfPresent(links, element.getAttribute("href"));
                addIfPresent(links, element.getTextContent());
            }
            if (matchesTag(nodeName, "enclosure")) {
                addIfPresent(links, element.getAttribute("url"));
                addIfPresent(links, element.getAttribute("href"));
            }
        }
        return new ArrayList<>(links);
    }

    private String textOf(Element parent, String tagName) {
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }
            if (!matchesTag(child.getNodeName(), tagName)) {
                continue;
            }
            String text = child.getTextContent();
            if (text != null && !text.isBlank()) {
                return text;
            }
        }
        return null;
    }

    private String firstMagnet(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        Matcher matcher = MAGNET_PATTERN.matcher(text);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }

    private String tryConvertTorrentUrlToMagnet(String url) {
        if (url == null || url.isBlank()) {
            return null;
        }
        String lowerUrl = url.toLowerCase(Locale.ROOT);
        if (!lowerUrl.endsWith(".torrent") && !lowerUrl.contains(".torrent?")) {
            return null;
        }
        if (!lowerUrl.startsWith("http://") && !lowerUrl.startsWith("https://")) {
            return null;
        }

        Request request = new Request.Builder().url(url).get().build();
        OkHttpClient client = buildClientWithProxy();
        // Retry up to 3 times with backoff, to handle proxy rate limiting gracefully
        for (int attempt = 0; attempt < 3; attempt++) {
            if (attempt > 0) {
                try {
                    Thread.sleep(1000L * attempt);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return null;
                }
            }
            try (Response response = client.newCall(request).execute()) {
                if (!response.isSuccessful() || response.body() == null) {
                    if (attempt < 2) continue;
                    log.warn("Torrent download non-success: {} -> HTTP {}", url, response.code());
                    return null;
                }
                byte[] data = response.body().bytes();
                if (data.length == 0 || data.length > 10 * 1024 * 1024) {
                    if (attempt < 2) continue;
                    log.warn("Torrent download bad size: {} -> {} bytes", url, data.length);
                    return null;
                }
                if (data.length <= 1 || data[0] != 'd') {
                    String preview = new String(data, 0, Math.min(data.length, 200), java.nio.charset.StandardCharsets.UTF_8);
                    log.warn("Torrent not bencoded: {} -> '{}'", url, preview.substring(0, Math.min(preview.length(), 80)));
                    return null;
                }
                TorrentInfo torrentInfo = new TorrentInfo(data);
                String magnet = torrentInfo.makeMagnetUri();
                if (magnet != null && !magnet.isBlank()) {
                    return magnet;
                }
                log.warn("Torrent parse produced no magnet: {}", url);
                return null;
            } catch (Exception e) {
                if (attempt < 2) continue;
                log.warn("Torrent download failed: {} -> {}", url, e.getMessage());
                return null;
            }
        }
        return null;
    }

    private boolean matchesTag(String nodeName, String expected) {
        if (nodeName == null) {
            return false;
        }
        return nodeName.equalsIgnoreCase(expected) || nodeName.toLowerCase(Locale.ROOT).endsWith(":" + expected.toLowerCase(Locale.ROOT));
    }

    private void addIfPresent(LinkedHashSet<String> set, String value) {
        if (value == null) {
            return;
        }
        String trimmed = value.trim();
        if (!trimmed.isEmpty()) {
            set.add(trimmed);
        }
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private String buildParsedResultContent(int parsedCount, int filteredOut, int createdCount, int duplicatedCount, List<String> lines) {
        StringBuilder builder = new StringBuilder();
        builder.append("解析结果:\n");
        builder.append("- 解析条目: ").append(parsedCount).append("\n");
        if (filteredOut > 0) {
            builder.append("- 过滤排除: ").append(filteredOut).append("\n");
        }
        builder.append("- 新建任务: ").append(createdCount).append("\n");
        builder.append("- 已存在: ").append(duplicatedCount).append("\n");
        builder.append("\n条目明细:\n");

        int showCount = Math.min(lines.size(), MAX_ENTRY_LINES);
        for (int i = 0; i < showCount; i++) {
            builder.append(lines.get(i)).append("\n");
        }
        if (lines.size() > showCount) {
            builder.append("... 其余 ").append(lines.size() - showCount).append(" 条已省略\n");
        }

        String content = builder.toString();
        if (content.length() <= MAX_FETCHED_CONTENT_LENGTH) {
            return content;
        }
        return content.substring(0, MAX_FETCHED_CONTENT_LENGTH) + "\n\n... [TRUNCATED]";
    }

    private void appendEntryLine(List<String> lines, String action, RssEntry entry) {
        String title = entry.title == null || entry.title.isBlank() ? "(无标题)" : entry.title;
        String magnet = entry.magnet == null ? "" : entry.magnet;
        if (magnet.length() > 80) {
            magnet = magnet.substring(0, 80) + "...";
        }
        lines.add("- [" + action + "] " + title + " | " + magnet);
    }

    private void applyRequest(ResourceRssSubscription entity, ResourceRssSubscriptionRequest request) {
        if (request.getName() == null || request.getName().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "订阅源名称不能为空");
        }
        if (request.getFeedUrl() == null || request.getFeedUrl().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "RSS 地址不能为空");
        }
        if (request.getLibraryId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "目标媒体库不能为空");
        }

        MediaLibrary library = mediaLibraryRepository.findById(request.getLibraryId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "目标媒体库不存在"));

        entity.setName(request.getName().trim());
        entity.setFeedUrl(request.getFeedUrl().trim());
        entity.setLibrary(library);
        entity.setIntervalMinutes(Math.max(1, request.getIntervalMinutes() == null ? 30 : request.getIntervalMinutes()));
        entity.setEnabled(request.getEnabled() == null || request.getEnabled());
        entity.setIncludeFilter(blankToNull(request.getIncludeFilter()));
        entity.setExcludeFilter(blankToNull(request.getExcludeFilter()));
    }

    private ResourceSearchVO.RssSubscriptionItem toVO(ResourceRssSubscription entity) {
        return ResourceSearchVO.RssSubscriptionItem.builder()
                .id(entity.getId())
                .name(entity.getName())
                .feedUrl(entity.getFeedUrl())
                .libraryId(entity.getLibrary() != null ? entity.getLibrary().getId() : null)
                .libraryName(entity.getLibrary() != null ? entity.getLibrary().getName() : null)
                .intervalMinutes(entity.getIntervalMinutes())
                .enabled(entity.getEnabled())
                .includeFilter(entity.getIncludeFilter())
                .excludeFilter(entity.getExcludeFilter())
                .lastCheckedAt(entity.getLastCheckedAt())
                .lastSuccessAt(entity.getLastSuccessAt())
                .lastError(entity.getLastError())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    private String blankToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private List<RssEntry> applyFilters(List<RssEntry> entries, String includeFilter, String excludeFilter) {
        Predicate<RssEntry> predicate = buildFilterPredicate(includeFilter, excludeFilter);
        List<RssEntry> filtered = new ArrayList<>();
        for (RssEntry entry : entries) {
            if (predicate.test(entry)) {
                filtered.add(entry);
            }
        }
        return filtered;
    }

    private Predicate<RssEntry> buildFilterPredicate(String includeFilter, String excludeFilter) {
        Pattern includePattern = compileFilterPattern(includeFilter);
        Pattern excludePattern = compileFilterPattern(excludeFilter);

        return entry -> {
            String title = entry.title() != null ? entry.title() : "";
            if (includePattern != null && !includePattern.matcher(title).find()) {
                return false;
            }
            if (excludePattern != null && excludePattern.matcher(title).find()) {
                return false;
            }
            return true;
        };
    }

    private Pattern compileFilterPattern(String regex) {
        if (regex == null || regex.isBlank()) {
            return null;
        }
        try {
            return Pattern.compile(regex);
        } catch (PatternSyntaxException e) {
            log.warn("Invalid regex pattern ignored: {}", regex, e);
            return null;
        }
    }

    /**
     * Lightweight RSS entry parsing for preview — only extracts title/link, skips magnet extraction and torrent downloads entirely.
     */
    private List<RssEntry> parseEntriesForPreview(String xml) throws Exception {
        List<RssEntry> list = new ArrayList<>();
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setExpandEntityReferences(false);
        factory.setNamespaceAware(false);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        Document doc = factory.newDocumentBuilder().parse(new InputSource(new StringReader(xml)));
        List<Element> entryNodes = collectEntryNodes(doc);
        for (Element item : entryNodes) {
            String title = firstNonBlank(textOf(item, "title"), textOf(item, "name"));
            String linkText = textOf(item, "link");
            list.add(new RssEntry(title, linkText, null));
        }
        return list;
    }

    public ResourceSearchVO.RssFilterPreviewResult previewFilter(RssFilterPreviewRequest request) {
        if (request.getFeedUrl() == null || request.getFeedUrl().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "RSS 地址不能为空");
        }

        String includeFilter = blankToNull(request.getIncludeFilter());
        String excludeFilter = blankToNull(request.getExcludeFilter());

        // Validate regex patterns early
        boolean includeFilterValid = true;
        String includeFilterError = null;
        if (includeFilter != null) {
            try {
                Pattern.compile(includeFilter);
            } catch (PatternSyntaxException e) {
                includeFilterValid = false;
                includeFilterError = e.getMessage();
            }
        }

        boolean excludeFilterValid = true;
        String excludeFilterError = null;
        if (excludeFilter != null) {
            try {
                Pattern.compile(excludeFilter);
            } catch (PatternSyntaxException e) {
                excludeFilterValid = false;
                excludeFilterError = e.getMessage();
            }
        }

        String xml;
        try {
            xml = fetchRss(request.getFeedUrl());
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "获取 RSS 数据失败: " + e.getMessage());
        }

        List<RssEntry> entries;
        try {
            entries = parseEntriesForPreview(xml);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "解析 RSS 数据失败: " + e.getMessage());
        }

        Predicate<RssEntry> predicate = buildFilterPredicate(includeFilter, excludeFilter);
        Pattern includePattern = includeFilterValid && includeFilter != null ? Pattern.compile(includeFilter) : null;
        Pattern excludePattern = excludeFilterValid && excludeFilter != null ? Pattern.compile(excludeFilter) : null;

        int includedCount = 0;
        int excludedCount = 0;
        List<ResourceSearchVO.RssFilterPreviewItem> previewItems = new ArrayList<>();

        for (RssEntry entry : entries) {
            boolean wouldDownload = predicate.test(entry);
            if (wouldDownload) {
                includedCount++;
            } else {
                excludedCount++;
            }

            String title = entry.title() != null ? entry.title() : "";
            boolean included = includePattern == null || includePattern.matcher(title).find();
            boolean excluded = excludePattern != null && excludePattern.matcher(title).find();

            previewItems.add(ResourceSearchVO.RssFilterPreviewItem.builder()
                    .title(entry.title())
                    .link(entry.link())
                    .included(included)
                    .excluded(excluded)
                    .build());
        }

        return ResourceSearchVO.RssFilterPreviewResult.builder()
                .totalCount(entries.size())
                .includedCount(includedCount)
                .excludedCount(excludedCount)
                .includeFilter(includeFilter)
                .excludeFilter(excludeFilter)
                .includeFilterValid(includeFilterValid)
                .excludeFilterValid(excludeFilterValid)
                .includeFilterError(includeFilterError)
                .excludeFilterError(excludeFilterError)
                .entries(previewItems)
                .build();
    }

    private record RssEntry(String title, String link, String magnet) {
    }
}
