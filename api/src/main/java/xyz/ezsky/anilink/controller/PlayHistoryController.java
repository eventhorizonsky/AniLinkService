package xyz.ezsky.anilink.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.stp.StpUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import xyz.ezsky.anilink.model.dto.PlayHistoryDTO;
import xyz.ezsky.anilink.model.vo.ApiResponseVO;
import xyz.ezsky.anilink.model.vo.PageVO;
import xyz.ezsky.anilink.model.vo.PlayHistoryVO;
import xyz.ezsky.anilink.service.PlayHistoryService;

/**
 * 播放历史接口
 */
@Tag(name = "播放历史", description = "用户播放进度和历史记录")
@RestController
@RequestMapping("/api/play-history")
@SaCheckLogin
public class PlayHistoryController {
    
    @Autowired
    private PlayHistoryService playHistoryService;
    
    /**
     * 更新播放进度
     */
    @Operation(summary = "更新播放进度", description = "更新用户的番剧播放进度")
    @PostMapping("/progress")
    public ApiResponseVO<PlayHistoryVO> updatePlayProgress(@RequestBody PlayHistoryDTO dto) {
        if (dto == null || dto.getAnimeId() == null) {
            return ApiResponseVO.fail(400, "animeId不能为空");
        }
        Long userId = StpUtil.getLoginIdAsLong();
        PlayHistoryVO result = playHistoryService.updatePlayProgress(userId, dto);
        return ApiResponseVO.success(result);
    }
    
    /**
     * 获取用户的播放历史（分页）
     */
    @Operation(summary = "获取播放历史", description = "获取用户的播放历史列表（分页，按最后播放时间倒序）")
    @GetMapping
    public ApiResponseVO<PageVO<PlayHistoryVO>> getUserPlayHistory(
            @Parameter(description = "页码，从1开始", required = false)
            @RequestParam(defaultValue = "1") Integer page,
            @Parameter(description = "每页大小", required = false)
            @RequestParam(defaultValue = "20") Integer pageSize) {
        Long userId = StpUtil.getLoginIdAsLong();
        PageVO<PlayHistoryVO> result = playHistoryService.getUserPlayHistory(userId, page, pageSize);
        return ApiResponseVO.success(result);
    }
    
    /**
     * 获取用户某个番剧的播放进度（无记录时 data 为 null，不返回 404，便于详情页等场景一次请求判断）
     */
    @Operation(summary = "获取番剧续播信息", description = "同 getAnimePlayProgress，但无记录时返回成功且 data 为空")
    @GetMapping("/anime/{animeId}/resume")
    public ApiResponseVO<PlayHistoryVO> getAnimePlayResume(
            @Parameter(description = "番剧ID", required = true)
            @PathVariable Long animeId) {
        Long userId = StpUtil.getLoginIdAsLong();
        PlayHistoryVO result = playHistoryService.getAnimePlayProgress(userId, animeId);
        return ApiResponseVO.success(result);
    }

    /**
     * 获取用户某个番剧的播放进度
     */
    @Operation(summary = "获取番剧播放进度", description = "获取用户对某个番剧的最近播放记录")
    @GetMapping("/anime/{animeId}")
    public ApiResponseVO<PlayHistoryVO> getAnimePlayProgress(
            @Parameter(description = "番剧ID", required = true)
            @PathVariable Long animeId) {
        Long userId = StpUtil.getLoginIdAsLong();
        PlayHistoryVO result = playHistoryService.getAnimePlayProgress(userId, animeId);
        if (result == null) {
            return ApiResponseVO.fail(404, "播放记录不存在");
        }
        return ApiResponseVO.success(result);
    }
    
    /**
     * 删除指定的播放记录
     */
    @Operation(summary = "删除播放记录", description = "删除用户的单条播放历史记录")
    @DeleteMapping("/{historyId}")
    public ApiResponseVO<Boolean> deletePlayHistory(
            @Parameter(description = "播放记录ID", required = true)
            @PathVariable Long historyId) {
        Long userId = StpUtil.getLoginIdAsLong();
        boolean result = playHistoryService.deletePlayHistory(userId, historyId);
        return ApiResponseVO.success(result);
    }
    
    /**
     * 清空播放历史
     */
    @Operation(summary = "清空播放历史", description = "清空用户的所有播放历史记录")
    @DeleteMapping("/clear")
    public ApiResponseVO<Void> clearPlayHistory() {
        Long userId = StpUtil.getLoginIdAsLong();
        playHistoryService.clearUserPlayHistory(userId);
        return ApiResponseVO.success(null);
    }
}
