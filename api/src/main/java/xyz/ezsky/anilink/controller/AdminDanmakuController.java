package xyz.ezsky.anilink.controller;

import cn.dev33.satoken.annotation.SaCheckRole;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import xyz.ezsky.anilink.model.vo.ApiResponseVO;
import xyz.ezsky.anilink.model.vo.DanmakuRecordVO;
import xyz.ezsky.anilink.model.vo.PageVO;
import xyz.ezsky.anilink.service.DanmakuRecordService;

/**
 * 管理端弹幕记录查询接口
 */
@Tag(name = "弹幕记录管理", description = "管理员查看全量弹幕发送记录")
@RestController
@RequestMapping("/api/admin")
@SaCheckRole("super-admin")
@Log4j2
public class AdminDanmakuController {

    @Autowired
    private DanmakuRecordService danmakuRecordService;

    /**
     * 查询弹幕记录（支持按用户、弹幕库、番剧、关键词筛选）
     */
    @Operation(
        summary = "查询弹幕记录",
        description = "管理员查看全量弹幕发送记录，支持按用户ID、弹幕库ID、番剧ID和关键词筛选。"
    )
    @GetMapping("/danmaku-records")
    public ApiResponseVO<PageVO<DanmakuRecordVO>> searchRecords(
            @Parameter(description = "页码，从1开始", required = false)
            @RequestParam(defaultValue = "1") int page,
            @Parameter(description = "每页大小", required = false)
            @RequestParam(defaultValue = "20") int pageSize,
            @Parameter(description = "用户ID", required = false)
            @RequestParam(required = false) Long userId,
            @Parameter(description = "弹幕库ID", required = false)
            @RequestParam(required = false) Long episodeId,
            @Parameter(description = "番剧ID", required = false)
            @RequestParam(required = false) Long animeId,
            @Parameter(description = "关键词（模糊匹配弹幕内容/用户名/番剧标题/分集标题）", required = false)
            @RequestParam(required = false) String keyword) {
        return ApiResponseVO.success(
                danmakuRecordService.searchAdmin(userId, episodeId, animeId, keyword, page, pageSize));
    }
}
