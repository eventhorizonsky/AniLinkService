package xyz.ezsky.anilink.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import xyz.ezsky.anilink.model.vo.ApiResponseVO;
import xyz.ezsky.anilink.service.DanmakuService;

/**
 * 弹幕管理接口
 */
@Tag(name = "弹幕管理", description = "用于获取弹幕数据")
@RestController
@RequestMapping("/api/v2")
@Log4j2
public class DanmakuController {

    @Autowired
    private DanmakuService danmakuService;

    /**
     * 获取指定弹幕库的所有弹幕（带30分钟缓存）
     *
     * @param episodeId 弹幕库ID
     * @param withRelated 是否包含第三方关联网址的弹幕
     * @return 弹幕数据
     */
    @Operation(
        summary = "获取弹幕", 
        description = "代理弹弹play /api/v2/comment/{episodeId} 接口，使用30分钟数据库缓存。" +
                      "当 withRelated 参数为 true 时，将返回此弹幕库对应的所有第三方关联网址的弹幕。"
    )
    @GetMapping("/comment/{episodeId}")
    public ApiResponseVO<Object> getComment(
            @Parameter(description = "弹幕库ID", required = true)
            @PathVariable Long episodeId,
            @Parameter(description = "是否包含第三方关联弹幕", required = false)
            @RequestParam(required = false, defaultValue = "false") Boolean withRelated) {
        
        String rawJson = danmakuService.getCommentByEpisodeId(episodeId, withRelated);
        
        if (rawJson == null) {
            return ApiResponseVO.fail(404, "弹幕数据不存在");
        }
        
        try {
            // 将 JSON 字符串转换为对象返回
            Object json = new ObjectMapper().readValue(rawJson, Object.class);
            return ApiResponseVO.success(json);
        } catch (JsonProcessingException e) {
            log.error("Failed to parse danmaku JSON for episodeId={}, withRelated={}", episodeId, withRelated, e);
            return ApiResponseVO.fail(500, "弹幕数据解析失败");
        }
    }

    @Operation(
        summary = "搜索剧集",
        description = "代理弹弹play /api/v2/search/episodes 接口，支持按动漫标题、剧集关键词或 tmdbId 搜索匹配候选。"
    )
    @GetMapping("/search/episodes")
    public ApiResponseVO<Object> searchEpisodes(
            @Parameter(description = "动漫标题关键词", required = false)
            @RequestParam(required = false) String anime,
            @Parameter(description = "剧集关键词", required = false)
            @RequestParam(required = false) String episode,
            @Parameter(description = "TMDB ID", required = false)
            @RequestParam(required = false) String tmdbId) {
        try {
            String rawJson = danmakuService.searchEpisodes(anime, episode, tmdbId);
            if (rawJson == null) {
                return ApiResponseVO.fail(404, "未找到匹配结果");
            }

            Object json = new ObjectMapper().readValue(rawJson, Object.class);
            return ApiResponseVO.success(json);
        } catch (IllegalArgumentException e) {
            log.warn("Invalid search episodes request: anime={}, episode={}, tmdbId={}", anime, episode, tmdbId, e);
            return ApiResponseVO.fail(400, e.getMessage());
        } catch (JsonProcessingException e) {
            return ApiResponseVO.fail(500, "搜索结果解析失败");
        } catch (Exception e) {
            return ApiResponseVO.fail("搜索剧集失败: " + e.getMessage());
        }
    }
}
