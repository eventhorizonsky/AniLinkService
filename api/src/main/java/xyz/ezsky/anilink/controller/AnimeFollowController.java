package xyz.ezsky.anilink.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.stp.StpUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import xyz.ezsky.anilink.model.dto.AnimeFollowDTO;
import xyz.ezsky.anilink.model.vo.AnimeFollowVO;
import xyz.ezsky.anilink.model.vo.ApiResponseVO;
import xyz.ezsky.anilink.model.vo.PageVO;
import xyz.ezsky.anilink.service.AnimeFollowService;

import java.util.List;
import java.util.Map;

/**
 * 追番管理接口
 */
@Tag(name = "追番管理", description = "用户追番功能")
@RestController
@RequestMapping("/api/follows")
@SaCheckLogin
public class AnimeFollowController {
    
    @Autowired
    private AnimeFollowService animeFollowService;
    
    /**
     * 追番
     */
    @Operation(summary = "添加追番", description = "用户追番某个番剧")
    @PostMapping
    public ApiResponseVO<AnimeFollowVO> followAnime(@RequestBody AnimeFollowDTO dto) {
        Long userId = StpUtil.getLoginIdAsLong();
        AnimeFollowVO result = animeFollowService.followAnime(userId, dto);
        return ApiResponseVO.success(result);
    }
    
    /**
     * 取消追番
     */
    @Operation(summary = "取消追番", description = "用户取消追番某个番剧")
    @DeleteMapping("/{animeId}")
    public ApiResponseVO<Boolean> unfollowAnime(
            @Parameter(description = "番剧ID", required = true)
            @PathVariable Long animeId) {
        Long userId = StpUtil.getLoginIdAsLong();
        boolean result = animeFollowService.unfollowAnime(userId, animeId);
        return ApiResponseVO.success(result);
    }
    
    /**
     * 获取用户追番列表（分页）
     */
    @Operation(summary = "获取追番列表", description = "获取用户的追番列表（分页，按更新时间倒序）")
    @GetMapping
    public ApiResponseVO<PageVO<AnimeFollowVO>> getUserFollows(
            @Parameter(description = "页码，从1开始", required = false)
            @RequestParam(defaultValue = "1") Integer page,
            @Parameter(description = "每页大小", required = false)
            @RequestParam(defaultValue = "20") Integer pageSize) {
        Long userId = StpUtil.getLoginIdAsLong();
        PageVO<AnimeFollowVO> result = animeFollowService.getUserFollows(userId, page, pageSize);
        return ApiResponseVO.success(result);
    }
    
    /**
     * 获取用户追番列表（不分页）
     */
    @Operation(summary = "获取所有追番", description = "获取用户的所有追番列表（不分页，按更新时间倒序）")
    @GetMapping("/all")
    public ApiResponseVO<List<AnimeFollowVO>> getUserFollowsList() {
        Long userId = StpUtil.getLoginIdAsLong();
        List<AnimeFollowVO> result = animeFollowService.getUserFollowsList(userId);
        return ApiResponseVO.success(result);
    }
    
    /**
     * 获取用户指定状态的追番列表
     */
    @Operation(summary = "按状态获取追番", description = "获取用户指定状态的追番列表(wish/watched/watching/on_hold/dropped)")
    @GetMapping("/status/{status}")
    public ApiResponseVO<List<AnimeFollowVO>> getUserFollowsByStatus(
            @Parameter(description = "追番状态：wish(想看)/watched(看过)/watching(在看)/on_hold(搁置)/dropped(抛弃)", required = true)
            @PathVariable String status) {
        Long userId = StpUtil.getLoginIdAsLong();
        List<AnimeFollowVO> result = animeFollowService.getUserFollowsByStatus(userId, status);
        return ApiResponseVO.success(result);
    }
    
    /**
     * 获取追番详情
     */
    @Operation(summary = "获取追番详情", description = "获取用户对某个番剧的追番详情")
    @GetMapping("/{animeId}")
    public ApiResponseVO<AnimeFollowVO> getFollowDetail(
            @Parameter(description = "番剧ID", required = true)
            @PathVariable Long animeId) {
        Long userId = StpUtil.getLoginIdAsLong();
        AnimeFollowVO result = animeFollowService.getFollowDetail(userId, animeId);
        if (result == null) {
            return ApiResponseVO.fail(404, "追番记录不存在");
        }
        return ApiResponseVO.success(result);
    }
    
    /**
     * 检查是否追番
     */
    @Operation(summary = "检查是否追番", description = "检查用户是否追番过某个番剧")
    @GetMapping("/check/{animeId}")
    public ApiResponseVO<Boolean> isFollowing(
            @Parameter(description = "番剧ID", required = true)
            @PathVariable Long animeId) {
        Long userId = StpUtil.getLoginIdAsLong();
        boolean result = animeFollowService.isFollowing(userId, animeId);
        return ApiResponseVO.success(result);
    }
    
    /**
     * 更新追番状态
     */
    @Operation(summary = "更新追番状态", description = "更新用户对某个番剧的追番状态")
    @PutMapping("/{animeId}/status")
    public ApiResponseVO<AnimeFollowVO> updateFollowStatus(
            @Parameter(description = "番剧ID", required = true)
            @PathVariable Long animeId,
            @Parameter(description = "新状态：wish(想看)/watched(看过)/watching(在看)/on_hold(搁置)/dropped(抛弃)", required = true)
            @RequestParam String status) {
        Long userId = StpUtil.getLoginIdAsLong();
        AnimeFollowVO result = animeFollowService.updateFollowStatus(userId, animeId, status);
        if (result == null) {
            return ApiResponseVO.fail(404, "追番记录不存在");
        }
        return ApiResponseVO.success(result);
    }

    @Operation(summary = "获取活跃追番", description = "获取用户想看+在看状态的追番（首页用）")
    @GetMapping("/active")
    public ApiResponseVO<List<AnimeFollowVO>> getActiveFollows() {
        Long userId = StpUtil.getLoginIdAsLong();
        List<AnimeFollowVO> result = animeFollowService.getActiveFollows(userId);
        return ApiResponseVO.success(result);
    }

    @Operation(summary = "绑定未匹配追番", description = "为从 Bangumi 拉取但未匹配的追番记录绑定本地番剧")
    @PutMapping("/{followId}/bind")
    public ApiResponseVO<AnimeFollowVO> bindFollow(
            @Parameter(description = "追番记录ID", required = true)
            @PathVariable Long followId,
            @RequestBody Map<String, Object> body) {
        Long userId = StpUtil.getLoginIdAsLong();
        Long animeId = body.get("animeId") != null ? Long.valueOf(body.get("animeId").toString()) : null;
        String animeTitle = body.get("animeTitle") != null ? body.get("animeTitle").toString() : null;
        String imageUrl = body.get("imageUrl") != null ? body.get("imageUrl").toString() : null;
        AnimeFollowVO result = animeFollowService.bindFollow(userId, followId, animeId, animeTitle, imageUrl);
        if (result == null) {
            return ApiResponseVO.fail(404, "追番记录不存在");
        }
        return ApiResponseVO.success(result);
    }
}
