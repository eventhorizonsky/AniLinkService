package xyz.ezsky.anilink.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import xyz.ezsky.anilink.service.SiteConfigService;
import xyz.ezsky.anilink.util.DandanClientUtil;

import java.util.Map;

/**
 * Dandan API 代理接口
 * 将前端请求透明转发到弹弹play API
 */
@Tag(name = "Dandan API 代理", description = "弹弹play 公开接口代理")
@RestController
@RequestMapping("/api/v2")
@Log4j2
public class DandanProxyController {

    @Autowired
    private DandanClientUtil dandanClientUtil;

    @Autowired
    private SiteConfigService siteConfigService;

    @Operation(summary = "季度番剧列表", description = "代理弹弹play /api/v2/bangumi/season/anime 接口")
    @GetMapping("/bangumi/season/anime")
    public ResponseEntity<String> getSeasonAnime(
            @Parameter(description = "查询参数")
            @RequestParam(required = false) Map<String, String> params) {
        return dandanClientUtil.get(siteConfigService.getDandanBaseUrl(), "/api/v2/bangumi/season/anime", params);
    }

    @Operation(summary = "按年月查季度番剧", description = "代理弹弹play /api/v2/bangumi/season/anime/{year}/{month} 接口")
    @GetMapping("/bangumi/season/anime/{year}/{month}")
    public ResponseEntity<String> getSeasonAnimeByYearMonth(
            @Parameter(description = "年份", required = true) @PathVariable int year,
            @Parameter(description = "月份", required = true) @PathVariable int month,
            @Parameter(description = "查询参数")
            @RequestParam(required = false) Map<String, String> params) {
        String path = String.format("/api/v2/bangumi/season/anime/%d/%d", year, month);
        return dandanClientUtil.get(siteConfigService.getDandanBaseUrl(), path, params);
    }

    @Operation(summary = "热门趋势", description = "代理弹弹play /api/v2/trending/all/hot/{period} 接口")
    @GetMapping("/trending/all/hot/{period}")
    public ResponseEntity<String> getTrendingHot(
            @Parameter(description = "时间周期", required = true) @PathVariable String period,
            @Parameter(description = "查询参数")
            @RequestParam(required = false) Map<String, String> params) {
        String path = "/api/v2/trending/all/hot/" + period;
        return dandanClientUtil.get(siteConfigService.getDandanBaseUrl(), path, params);
    }

    @Operation(summary = "上升趋势", description = "代理弹弹play /api/v2/trending/all/rising/{period} 接口")
    @GetMapping("/trending/all/rising/{period}")
    public ResponseEntity<String> getTrendingRising(
            @Parameter(description = "时间周期", required = true) @PathVariable String period,
            @Parameter(description = "查询参数")
            @RequestParam(required = false) Map<String, String> params) {
        String path = "/api/v2/trending/all/rising/" + period;
        return dandanClientUtil.get(siteConfigService.getDandanBaseUrl(), path, params);
    }

    @Operation(summary = "新番热度", description = "代理弹弹play /api/v2/trending/new-anime/hot/{scope} 接口")
    @GetMapping("/trending/new-anime/hot/{scope}")
    public ResponseEntity<String> getTrendingNewAnimeHot(
            @Parameter(description = "范围", required = true) @PathVariable String scope,
            @Parameter(description = "查询参数")
            @RequestParam(required = false) Map<String, String> params) {
        String path = "/api/v2/trending/new-anime/hot/" + scope;
        return dandanClientUtil.get(siteConfigService.getDandanBaseUrl(), path, params);
    }
}
