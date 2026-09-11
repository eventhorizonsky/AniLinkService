package xyz.ezsky.anilink.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import xyz.ezsky.anilink.model.vo.BangumiRankSubjectVO;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Bangumi 动画排行榜（仅动画，数据源：next API 开放条目浏览 /p1/subjects）。
 *
 * <p>与 czy0729/Bangumi 客户端一致，请求 {@code next.<base>/p1/subjects}（官方或镜像基址）：
 * 服务端完成平台(cat)/年份月份(year/month)/公共标签(tags, 覆盖 来源/题材/地区/受众) AND
 * 筛选与排序(sort: rank/trends/collects/date/title)，返回含 rank/评分/人数 的 JSON，
 * 匿名可用、无需登录态。
 * 页面固定每页 24 条（p1 行为），total 字段为总页数。</p>
 */
@Service
@Log4j2
public class BangumiRankService {

    /** p1 每页条数（服务端固定） */
    private static final int PAGE_SIZE = 24;

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Pattern INFO_YEAR_MONTH = Pattern.compile("(20\\d{2}|19\\d{2})年(\\d{1,2})月");

    /** 平台 → p1 cat（官方 SubjectAnimeCategory：0其他/1TV/2OVA/3剧场版(Movie)/5WEB） */
    private static final Map<String, Integer> PLATFORM_CAT = Map.of(
            "TV", 1,
            "OVA", 2,
            "剧场版", 3,
            "WEB", 5,
            "其他", 0
    );

    @Autowired
    private BangumiApiService bangumiApiService;

    public static final class RankPageResult {
        public final List<BangumiRankSubjectVO> list;
        public final int totalPages;
        public final int page;
        public final int pageSize;

        RankPageResult(List<BangumiRankSubjectVO> list, int totalPages, int page) {
            this.list = list == null ? Collections.emptyList() : list;
            this.totalPages = Math.max(1, totalPages);
            this.page = page;
            this.pageSize = PAGE_SIZE;
        }
    }

    /**
     * 查询动画排行榜某一页。
     *
     * @param platform 平台：TV / WEB / OVA / 剧场版 / 其他（空=全部）
     * @param source   来源（空=全部，作为公共标签筛选）
     * @param tag      题材/标签（空=全部）
     * @param area     地区（空=全部）
     * @param target   受众（空=全部）
     * @param year     年份
     * @param month    月份（仅与年份同传生效）
     * @param sort     rank/trends/collects/date/title
     * @param page     页码（从 1 开始）
     */
    public RankPageResult query(String platform, String source, String tag, String area, String target,
                                Integer year, Integer month, String sort, int page) {
        Integer cat = null;
        if (StringUtils.hasText(platform)) {
            cat = PLATFORM_CAT.get(platform.trim());
        }
        List<String> metaTags = new ArrayList<>(4);
        addIfPresent(metaTags, source);
        addIfPresent(metaTags, tag);
        addIfPresent(metaTags, area);
        addIfPresent(metaTags, target);

        String raw = bangumiApiService.getP1SubjectsRaw(cat, year, month, sort, page, metaTags);
        if (raw == null) {
            throw new BangumiApiException("排行榜数据源不可用：请检查 Bangumi 网络方案/镜像配置后重试");
        }
        return parse(raw, page);
    }

    private void addIfPresent(List<String> list, String v) {
        if (StringUtils.hasText(v)) {
            list.add(v.trim());
        }
    }

    private RankPageResult parse(String raw, int page) {
        List<BangumiRankSubjectVO> list = new ArrayList<>(PAGE_SIZE + 2);
        int totalPages = 1;
        try {
            JsonNode root = MAPPER.readTree(raw);
            totalPages = Math.max(1, root.path("total").asInt(1));
            JsonNode data = root.get("data");
            if (data != null && data.isArray()) {
                for (JsonNode node : data) {
                    BangumiRankSubjectVO vo = new BangumiRankSubjectVO();
                    long id = node.path("id").asLong(0);
                    if (id <= 0) {
                        continue;
                    }
                    vo.setSubjectId(id);
                    vo.setNameCn(node.path("nameCN").asText(""));
                    vo.setName(node.path("name").asText(""));

                    String cover = "";
                    JsonNode images = node.get("images");
                    if (images != null) {
                        JsonNode common = images.get("common");
                        cover = common != null && common.isTextual() ? common.asText() : "";
                        if (!StringUtils.hasText(cover)) {
                            JsonNode large = images.get("large");
                            cover = large != null && large.isTextual() ? large.asText() : "";
                        }
                    }
                    vo.setCover(cover);

                    String date = parseInfoDate(node.path("info").asText(""));
                    vo.setDate(date);
                    vo.setRank(node.path("rating").path("rank").asInt(0));
                    vo.setScore(node.path("rating").path("score").asDouble(0));
                    vo.setVotes(node.path("rating").path("total").asInt(0));
                    list.add(vo);
                }
            }
        } catch (Exception e) {
            log.warn("解析 p1 排行榜响应失败 page={}", page, e);
            throw new BangumiApiException("排行榜数据解析失败，请稍后重试");
        }
        return new RankPageResult(list, totalPages, page);
    }

    /** info 形如 “24话 / 2008年10月2日 / …”，抽取年月作为卡片年份 */
    private static String parseInfoDate(String info) {
        if (!StringUtils.hasText(info)) {
            return "";
        }
        Matcher m = INFO_YEAR_MONTH.matcher(info);
        if (m.find()) {
            String year = m.group(1);
            String month = m.group(2);
            return year + "-" + (month.length() == 1 ? "0" : "") + month + "-01";
        }
        return "";
    }

    /** 排行榜数据源不可用时抛出，消息可直接展示给用户 */
    public static class BangumiApiException extends RuntimeException {
        public BangumiApiException(String message) {
            super(message);
        }
    }
}
