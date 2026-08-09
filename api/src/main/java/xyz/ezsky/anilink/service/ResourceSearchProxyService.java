package xyz.ezsky.anilink.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import xyz.ezsky.anilink.model.vo.ResourceSearchVO;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class ResourceSearchProxyService {

    @Autowired
    private SiteConfigService siteConfigService;

    @Autowired
    private ObjectMapper objectMapper;

    private final OkHttpClient baseClient = new OkHttpClient();

    public List<ResourceSearchVO.NamedItem> fetchSubgroups() {
        JsonNode root = executeGet("subgroup", null, null, null);
        JsonNode items = root.path("Subgroups");
        if (!items.isArray()) {
            items = root.path("subgroups");
        }
        return mapNamedItems(items);
    }

    public List<ResourceSearchVO.NamedItem> fetchTypes() {
        JsonNode root = executeGet("type", null, null, null);
        JsonNode items = root.path("Types");
        if (!items.isArray()) {
            items = root.path("types");
        }
        return mapNamedItems(items);
    }

    public ResourceSearchVO.ResourceListResult fetchResources(String keyword, Integer subgroup, Integer type, Integer offset) {
        JsonNode root = executeGet("list", keyword, subgroup, type, offset);
        boolean hasMore = false;
        if (root.has("HasMore")) {
            hasMore = root.path("HasMore").asBoolean(false);
        } else if (root.has("hasMore")) {
            hasMore = root.path("hasMore").asBoolean(false);
        }

        JsonNode resources = root.path("Resources");
        if (!resources.isArray()) {
            resources = root.path("resources");
        }

        List<ResourceSearchVO.ResourceItem> list = new ArrayList<>();
        if (resources.isArray()) {
            for (JsonNode item : resources) {
                list.add(ResourceSearchVO.ResourceItem.builder()
                        .title(text(item, "Title", "title"))
                        .typeId(number(item, "TypeId", "typeId"))
                        .typeName(text(item, "TypeName", "typeName"))
                        .subgroupId(number(item, "SubgroupId", "subgroupId"))
                        .subgroupName(text(item, "SubgroupName", "subgroupName"))
                        .magnet(text(item, "Magnet", "magnet"))
                        .pageUrl(text(item, "PageUrl", "pageUrl"))
                        .fileSize(text(item, "FileSize", "fileSize"))
                        .publishDate(text(item, "PublishDate", "publishDate"))
                        .build());
            }
        }

        return ResourceSearchVO.ResourceListResult.builder()
                .hasMore(hasMore)
                .resources(list)
                .build();
    }

    public ResourceSearchVO.NodeConnectionTestResult testConnection() {
        long start = System.currentTimeMillis();
        try {
            executeGet("subgroup", null, null, null);
            return ResourceSearchVO.NodeConnectionTestResult.builder()
                    .ok(true)
                    .latencyMs(System.currentTimeMillis() - start)
                    .message("连接成功")
                    .build();
        } catch (Exception e) {
            return ResourceSearchVO.NodeConnectionTestResult.builder()
                    .ok(false)
                    .latencyMs(System.currentTimeMillis() - start)
                    .message(e.getMessage() == null ? "连接失败" : e.getMessage())
                    .build();
        }
    }

    private List<ResourceSearchVO.NamedItem> mapNamedItems(JsonNode items) {
        List<ResourceSearchVO.NamedItem> list = new ArrayList<>();
        if (!items.isArray()) {
            return list;
        }
        for (JsonNode item : items) {
            list.add(ResourceSearchVO.NamedItem.builder()
                    .id(number(item, "Id", "id"))
                    .name(text(item, "Name", "name"))
                    .build());
        }
        return list;
    }

    private JsonNode executeGet(String path, String keyword, Integer subgroup, Integer type) {
        return executeGet(path, keyword, subgroup, type, null);
    }

    private JsonNode executeGet(String path, String keyword, Integer subgroup, Integer type, Integer offset) {
        String baseUrl = siteConfigService.getResourceNodeBaseUrl();
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalStateException("请先在站点配置中填写资源搜索节点地址");
        }

        HttpUrl parsed = HttpUrl.parse(baseUrl);
        if (parsed == null) {
            throw new IllegalArgumentException("资源搜索节点地址格式不正确");
        }

        HttpUrl.Builder urlBuilder = parsed.newBuilder();
        for (String seg : path.split("/")) {
            if (!seg.isBlank()) {
                urlBuilder.addPathSegment(seg);
            }
        }

        if (keyword != null && !keyword.isBlank()) {
            urlBuilder.addQueryParameter("keyword", keyword);
        }
        if (subgroup != null && subgroup >= 0) {
            urlBuilder.addQueryParameter("subgroup", String.valueOf(subgroup));
        }
        if (type != null && type >= 0) {
            urlBuilder.addQueryParameter("type", String.valueOf(type));
        }
        if (offset != null && offset >= 0) {
            urlBuilder.addQueryParameter("offset", String.valueOf(offset));
        }
        urlBuilder.addQueryParameter("r", String.valueOf(ThreadLocalRandom.current().nextDouble()));

        Request request = new Request.Builder().url(urlBuilder.build()).get().build();

        OkHttpClient client = buildClientWithProxy();
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IllegalStateException("节点请求失败，状态码: " + response.code());
            }
            if (response.body() == null) {
                throw new IllegalStateException("节点返回空响应");
            }
            String body = response.body().string();
            return objectMapper.readTree(body);
        } catch (IOException e) {
            throw new IllegalStateException("请求节点失败: " + e.getMessage(), e);
        }
    }

    private OkHttpClient buildClientWithProxy() {
        String host = siteConfigService.getResourceNodeProxyHost();
        Integer port = siteConfigService.getResourceNodeProxyPort();
        if (host == null || host.isBlank() || port == null || port <= 0) {
            return baseClient;
        }
        Proxy proxy = new Proxy(Proxy.Type.HTTP, new InetSocketAddress(host.trim(), port));
        return baseClient.newBuilder().proxy(proxy).build();
    }

    private String text(JsonNode node, String key1, String key2) {
        if (node.has(key1) && !node.path(key1).isNull()) {
            return node.path(key1).asText();
        }
        if (node.has(key2) && !node.path(key2).isNull()) {
            return node.path(key2).asText();
        }
        return null;
    }

    private Integer number(JsonNode node, String key1, String key2) {
        if (node.has(key1) && node.path(key1).canConvertToInt()) {
            return node.path(key1).asInt();
        }
        if (node.has(key2) && node.path(key2).canConvertToInt()) {
            return node.path(key2).asInt();
        }
        return null;
    }
}
