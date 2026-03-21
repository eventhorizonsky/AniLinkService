package xyz.ezsky.anilink.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import xyz.ezsky.anilink.model.vo.AnimeVO;
import xyz.ezsky.anilink.model.vo.ApiResponseVO;
import xyz.ezsky.anilink.model.vo.EpisodeVO;
import xyz.ezsky.anilink.model.vo.PageVO;
import xyz.ezsky.anilink.service.AnimeService;

import java.util.List;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 动漫管理接口
 */
@Tag(name = "动漫管理", description = "用于获取动漫信息和剧集列表")
@RestController
@RequestMapping("/api/animes")
@Log4j2
public class AnimeController {

    @Autowired
    private AnimeService animeService;

    /**
     * 获取所有动漫列表（分页）
     *
     * @param page 页码（从1开始）
     * @param pageSize 每页大小
     * @param keyword 搜索关键词（可选）
     * @return 动漫列表分页数据
     */
    @Operation(summary = "获取所有动漫（分页）", description = "获取系统中已记录的所有动漫列表，支持分页和按标题模糊查询")
    @GetMapping
    public ApiResponseVO<PageVO<AnimeVO>> getAllAnimes(
            @Parameter(description = "页码，从1开始", required = false)
            @RequestParam(defaultValue = "1") Integer page,
            @Parameter(description = "每页大小", required = false)
            @RequestParam(defaultValue = "20") Integer pageSize,
            @Parameter(description = "搜索关键词（按标题模糊查询）", required = false)
            @RequestParam(required = false) String keyword) {
        PageVO<AnimeVO> result = animeService.getAnimesPage(page, pageSize, keyword);
        return ApiResponseVO.success(result);
    }

    /**
     * 根据弹幕库动漫ID获取动漫详情
     *
     * @param animeId 弹幕库动漫ID
     * @return 动漫信息
     */
    @Operation(summary = "获取动漫详情", description = "根据弹幕库动漫ID获取动漫的详细信息")
    @GetMapping("/{animeId}")
    public ApiResponseVO<AnimeVO> getAnimeById(
            @Parameter(description = "弹幕库动漫ID", required = true)
            @PathVariable Long animeId) {
        AnimeVO anime = animeService.getAnimeById(animeId);
        if (anime == null) {
            return ApiResponseVO.fail(404, "动漫不存在");
        }
        return ApiResponseVO.success(anime);
    }

    /**
     * 根据动漫ID获取视频库中该动漫的剧集（支持分页）
     *
     * @param animeId 弹幕库动漫ID
     * @param page 页码（从1开始）
     * @param pageSize 每页大小
     * @return 分页的剧集列表
     */
    @Operation(summary = "获取动漫的剧集列表", description = "根据弹幕库动漫ID获取当前视频库中该动漫的所有剧集，可分页")
    @GetMapping("/{animeId}/episodes")
    public ApiResponseVO<PageVO<EpisodeVO>> getEpisodesByAnimeId(
            @Parameter(description = "弹幕库动漫ID", required = true)
            @PathVariable Long animeId,
            @Parameter(description = "页码，从1开始", required = false)
            @RequestParam(defaultValue = "1") Integer page,
            @Parameter(description = "每页大小", required = false)
            @RequestParam(defaultValue = "20") Integer pageSize) {
        PageVO<EpisodeVO> episodesPage = animeService.getEpisodesByAnimeId(animeId, page, pageSize);
        return ApiResponseVO.success(episodesPage);
    }

    /**
     * 获取动漫详情（使用数据库ID）
     *
     * @param id 数据库ID
     * @return 动漫信息
     */
    @Operation(summary = "根据数据库ID获取动漫", description = "使用数据库内部ID获取动漫详情")
    @GetMapping("/db/{id}")
    public ApiResponseVO<AnimeVO> getAnimeByDbId(
            @Parameter(description = "数据库ID", required = true)
            @PathVariable Long id) {
        AnimeVO anime = animeService.getAnimeByDbId(id);
        if (anime == null) {
            return ApiResponseVO.fail(404, "动漫不存在");
        }
        return ApiResponseVO.success(anime);
    }

    /**
     * 根据动漫ID获取原始JSON数据
     *
     * @param animeId 动漫ID
     * @return 原始JSON数据
     */
    @Operation(summary = "获取原始JSON数据", description = "根据动漫ID获取原始JSON数据")
    @GetMapping("/{animeId}/raw-json")
    public ApiResponseVO<Object> getRawJsonByAnimeId(
            @Parameter(description = "动漫ID", required = true)
            @PathVariable Long animeId) {
        String rawJson = animeService.getRawJsonByAnimeId(animeId);
        if (rawJson == null) {
            return ApiResponseVO.fail(404, "原始JSON数据不存在");
        }
        try {
            Object json = new ObjectMapper().readValue(rawJson, Object.class);
            return ApiResponseVO.success(json);
        } catch (JsonProcessingException e) {
            log.error("Failed to parse raw anime JSON for animeId={}", animeId, e);
            return ApiResponseVO.fail(500, "原始JSON数据解析失败");
        }
    }

    /**
     * 获取新番列表原始JSON数据
     *
     * @return 新番原始JSON数据
     */
    @Operation(summary = "获取新番原始JSON数据", description = "代理弹弹 /api/v2/bangumi/shin，使用数据库缓存")
    @GetMapping("/shin/raw-json")
    public ApiResponseVO<Object> getShinRawJson() {
        String rawJson = animeService.getShinRawJson();
        if (rawJson == null) {
            return ApiResponseVO.fail(404, "新番原始JSON数据不存在");
        }
        try {
            Object json = new ObjectMapper().readValue(rawJson, Object.class);
            return ApiResponseVO.success(json);
        } catch (JsonProcessingException e) {
            return ApiResponseVO.fail(500, "新番原始JSON数据解析失败");
        }
    }
}
