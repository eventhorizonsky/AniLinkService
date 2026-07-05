package xyz.ezsky.anilink.controller;

import cn.dev33.satoken.annotation.SaCheckRole;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import xyz.ezsky.anilink.model.vo.ApiResponseVO;
import xyz.ezsky.anilink.model.vo.CacheClearResultVO;
import xyz.ezsky.anilink.model.vo.CacheStatsVO;
import xyz.ezsky.anilink.service.CacheManageService;

import java.util.Map;

/**
 * 缓存管理控制器
 *
 * <p>提供 API 缓存统计和分级清理功能，仅超级管理员可访问</p>
 */
@RestController
@RequestMapping("/api/cache")
@Tag(name = "缓存管理", description = "API 缓存统计与清理")
@SaCheckRole("super-admin")
public class CacheManageController {

    @Autowired
    private CacheManageService cacheManageService;

    /**
     * 获取缓存统计信息
     */
    @GetMapping("/stats")
    @Operation(summary = "获取缓存统计", description = "返回缓存总数、有效数、过期数及按类型分组统计")
    public ApiResponseVO<CacheStatsVO> getStats() {
        CacheStatsVO stats = cacheManageService.getCacheStats();
        return ApiResponseVO.success(stats, "获取缓存统计成功");
    }

    /**
     * 获取可用的缓存类型列表
     */
    @GetMapping("/types")
    @Operation(summary = "获取缓存类型列表", description = "返回可用于按类型清理的缓存类型")
    public ApiResponseVO<Map<String, String>> getCacheTypes() {
        Map<String, String> types = cacheManageService.getAvailableCacheTypes();
        return ApiResponseVO.success(types, "获取缓存类型成功");
    }

    /**
     * 清理过期缓存（Level 1）
     */
    @DeleteMapping("/expired")
    @Operation(summary = "清理过期缓存", description = "删除所有已过期的缓存条目（最安全）")
    public ApiResponseVO<CacheClearResultVO> clearExpired() {
        CacheClearResultVO result = cacheManageService.clearExpired();
        return ApiResponseVO.success(result, "清理过期缓存完成，共删除 " + result.getDeletedCount() + " 条");
    }

    /**
     * 按类型清理缓存（Level 2）
     *
     * @param type 缓存类型：comment / bangumi
     */
    @DeleteMapping("/type/{type}")
    @Operation(summary = "按类型清理缓存", description = "按缓存类型（comment、bangumi）删除对应缓存")
    public ApiResponseVO<CacheClearResultVO> clearByType(@PathVariable String type) {
        CacheClearResultVO result = cacheManageService.clearByType(type);
        return ApiResponseVO.success(result, "按类型 [" + type + "] 清理缓存完成，共删除 " + result.getDeletedCount() + " 条");
    }

    /**
     * 清空全部缓存（Level 3）
     */
    @DeleteMapping("/all")
    @Operation(summary = "清空全部缓存", description = "删除所有 API 缓存（需谨慎使用）")
    public ApiResponseVO<CacheClearResultVO> clearAll() {
        CacheClearResultVO result = cacheManageService.clearAll();
        return ApiResponseVO.success(result, "清空全部缓存完成，共删除 " + result.getDeletedCount() + " 条");
    }
}
