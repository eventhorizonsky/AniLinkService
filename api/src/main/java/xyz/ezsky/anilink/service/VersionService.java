package xyz.ezsky.anilink.service;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import lombok.extern.log4j.Log4j2;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.info.BuildProperties;
import org.springframework.stereotype.Service;
import xyz.ezsky.anilink.model.vo.ReleaseVO;
import xyz.ezsky.anilink.model.vo.VersionInfoVO;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 版本信息服务：提供当前版本与 GitHub Release 列表
 */
@Service
@Log4j2
public class VersionService {

    private final OkHttpClient httpClient = new OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .build();

    private final ObjectProvider<BuildProperties> buildPropertiesProvider;
    private final String githubRepo;

    public VersionService(ObjectProvider<BuildProperties> buildPropertiesProvider,
                          @Value("${github.repo:eventhorizonsky/AniLinkService}") String githubRepo) {
        this.buildPropertiesProvider = buildPropertiesProvider;
        this.githubRepo = githubRepo;
    }

    /**
     * 获取当前运行版本（来自构建时生成的 build-info.properties）
     */
    public String getCurrentVersion() {
        BuildProperties buildProperties = buildPropertiesProvider.getIfAvailable();
        if (buildProperties != null && buildProperties.getVersion() != null) {
            return buildProperties.getVersion();
        }
        return "unknown";
    }

    /**
     * 获取版本信息：当前版本 + GitHub Release 列表
     */
    public VersionInfoVO getVersionInfo() {
        String currentVersion = getCurrentVersion();
        return new VersionInfoVO(currentVersion, githubRepo, fetchReleases(currentVersion));
    }

    private List<ReleaseVO> fetchReleases(String currentVersion) {
        String url = "https://api.github.com/repos/" + githubRepo + "/releases?per_page=30";
        Request request = new Request.Builder()
                .url(url)
                .header("User-Agent", "AniLinkService/" + currentVersion)
                .header("Accept", "application/vnd.github+json")
                .build();
        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful() || response.body() == null) {
                log.warn("GitHub releases request failed, repo={}, code={}", githubRepo, response.code());
                return new ArrayList<>();
            }
            String body = response.body().string();
            JSONArray array = JSON.parseArray(body);
            if (array == null) {
                return new ArrayList<>();
            }
            List<ReleaseVO> releases = new ArrayList<>(array.size());
            for (int i = 0; i < array.size(); i++) {
                JSONObject obj = array.getJSONObject(i);
                String tagName = obj.getString("tag_name");
                releases.add(new ReleaseVO(
                        tagName,
                        obj.getString("name"),
                        obj.getString("published_at"),
                        obj.getString("html_url"),
                        obj.getString("body"),
                        tagName != null && normalizeVersion(tagName).equals(normalizeVersion(currentVersion))
                ));
            }
            return releases;
        } catch (IOException e) {
            log.error("Failed to fetch GitHub releases, repo={}", githubRepo, e);
            return new ArrayList<>();
        }
    }

    private String normalizeVersion(String version) {
        if (version == null) {
            return "";
        }
        String v = version.trim().toLowerCase();
        if (v.startsWith("v")) {
            v = v.substring(1);
        }
        return v;
    }
}
