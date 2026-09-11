package xyz.ezsky.anilink.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import xyz.ezsky.anilink.model.vo.ApiResponseVO;
import xyz.ezsky.anilink.service.BangumiRankService;
import xyz.ezsky.anilink.service.BangumiRankService.RankPageResult;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Bangumi 动画排行榜接口（仅动画）。
 *
 * <p>数据源为 next API 条目浏览 /p1/subjects：平台 / 年份 / 月份 / 排序 /
 * 来源 / 题材标签 / 地区 / 受众等筛选项，匿名可用。</p>
 */
@Tag(name = "Bangumi 排行榜", description = "Bangumi 动画排行榜（仅动画，含多种筛选项）")
@RestController
@RequestMapping("/api/bangumi/rank")
@Log4j2
public class BangumiRankController {

    @Autowired
    private BangumiRankService bangumiRankService;

    @Operation(summary = "动画排行榜列表", description = "按筛选项返回动画排行（分页，每页 24 条）")
    @GetMapping("/subjects")
    public ApiResponseVO<Map<String, Object>> getRankSubjects(
            @Parameter(description = "排序：rank 排名 / trends 热度 / collects 收藏 / date 日期 / title 名称")
            @RequestParam(defaultValue = "rank") String sort,
            @Parameter(description = "平台：TV / WEB / OVA / 剧场版 / 其他（空=全部）")
            @RequestParam(required = false) String platform,
            @Parameter(description = "年份（空=全部）")
            @RequestParam(required = false) Integer year,
            @Parameter(description = "月份 1-12（需与年份一起生效）")
            @RequestParam(required = false) Integer month,
            @Parameter(description = "来源：原创 / 漫画改 / 小说改 / 游戏改 / 影视改")
            @RequestParam(required = false) String source,
            @Parameter(description = "题材/标签：恋爱 / 科幻 / 日常 等")
            @RequestParam(required = false) String tag,
            @Parameter(description = "地区：日本 / 欧美 / 中国 / 美国 / 韩国 / 英国 / 法国 / 俄罗斯 / 苏联 / 香港 / 台湾 / 捷克")
            @RequestParam(required = false) String area,
            @Parameter(description = "受众：子供向 / 少年向 / 少女向 / 青年向 / 女性向 / BL / GL")
            @RequestParam(required = false) String target,
            @Parameter(description = "页码，从 1 开始")
            @RequestParam(defaultValue = "1") Integer page) {

        try {
            int p = page == null || page < 1 ? 1 : page;
            RankPageResult result = bangumiRankService.query(
                    trim(platform), trim(source), trim(tag), trim(area), trim(target),
                    year, month, sort, p);

            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("page", result.page);
            payload.put("pageSize", result.pageSize);
            payload.put("totalPages", result.totalPages);
            payload.put("hasMore", p < result.totalPages);
            payload.put("list", result.list);
            return ApiResponseVO.success(payload);
        } catch (BangumiRankService.BangumiApiException e) {
            return ApiResponseVO.fail(502, e.getMessage());
        } catch (Exception e) {
            log.error("获取 Bangumi 排行榜失败", e);
            return ApiResponseVO.fail(500, "排行榜获取失败：" + e.getMessage());
        }
    }

    private static String trim(String s) {
        return s == null ? null : s.trim();
    }
}
