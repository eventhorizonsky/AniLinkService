package xyz.ezsky.anilink.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.stp.StpUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import xyz.ezsky.anilink.model.vo.ApiResponseVO;
import xyz.ezsky.anilink.model.vo.MessageVO;
import xyz.ezsky.anilink.model.vo.PageVO;
import xyz.ezsky.anilink.service.MessageService;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 消息管理接口
 */
@Tag(name = "消息管理", description = "系统消息和通知管理")
@RestController
@RequestMapping("/api/messages")
@SaCheckLogin
public class MessageController {
    
    @Autowired
    private MessageService messageService;
    
    /**
     * 获取用户消息列表（分页）
     */
    @Operation(summary = "获取消息列表", description = "获取用户的消息列表（分页，按创建时间倒序）")
    @GetMapping
    public ApiResponseVO<PageVO<MessageVO>> getUserMessages(
            @Parameter(description = "页码，从1开始", required = false)
            @RequestParam(defaultValue = "1") Integer page,
            @Parameter(description = "每页大小", required = false)
            @RequestParam(defaultValue = "20") Integer pageSize) {
        Long userId = StpUtil.getLoginIdAsLong();
        PageVO<MessageVO> result = messageService.getUserMessages(userId, page, pageSize);
        return ApiResponseVO.success(result);
    }
    
    /**
     * 获取用户的未读消息
     */
    @Operation(summary = "获取未读消息", description = "获取用户的所有未读消息")
    @GetMapping("/unread")
    public ApiResponseVO<List<MessageVO>> getUnreadMessages() {
        Long userId = StpUtil.getLoginIdAsLong();
        List<MessageVO> result = messageService.getUserUnreadMessages(userId);
        return ApiResponseVO.success(result);
    }
    
    /**
     * 获取用户的所有消息
     */
    @Operation(summary = "获取所有消息", description = "获取用户的所有消息（不分页）")
    @GetMapping("/all")
    public ApiResponseVO<List<MessageVO>> getAllMessages() {
        Long userId = StpUtil.getLoginIdAsLong();
        List<MessageVO> result = messageService.getUserAllMessages(userId);
        return ApiResponseVO.success(result);
    }
    
    /**
     * 获取未读消息数
     */
    @Operation(summary = "获取未读数", description = "获取用户的未读消息数")
    @GetMapping("/unread-count")
    public ApiResponseVO<Map<String, Long>> getUnreadCount() {
        Long userId = StpUtil.getLoginIdAsLong();
        long count = messageService.getUnreadCount(userId);
        Map<String, Long> result = new HashMap<>();
        result.put("unreadCount", count);
        return ApiResponseVO.success(result);
    }
    
    /**
     * 标记消息为已读
     */
    @Operation(summary = "标记已读", description = "标记单个消息为已读")
    @PutMapping("/{messageId}/read")
    public ApiResponseVO<MessageVO> markMessageAsRead(
            @Parameter(description = "消息ID", required = true)
            @PathVariable Long messageId) {
        MessageVO result = messageService.markMessageAsRead(messageId);
        if (result == null) {
            return ApiResponseVO.fail(404, "消息不存在");
        }
        return ApiResponseVO.success(result);
    }
    
    /**
     * 标记所有消息为已读
     */
    @Operation(summary = "全部标记已读", description = "标记用户的所有消息为已读")
    @PutMapping("/mark-all-read")
    public ApiResponseVO<Void> markAllMessagesAsRead() {
        Long userId = StpUtil.getLoginIdAsLong();
        messageService.markAllMessagesAsRead(userId);
        return ApiResponseVO.success(null);
    }

    /**
     * 标记指定剧集的未读消息为已读（进入播放页时调用，消除该集未读角标）
     */
    @Operation(summary = "按剧集标记已读", description = "将用户某部剧集的全部未读消息标记为已读")
    @PutMapping("/read-by-episode/{episodeId}")
    public ApiResponseVO<Integer> markReadByEpisode(
            @Parameter(description = "剧集ID（DanDan episodeId）", required = true)
            @PathVariable String episodeId) {
        Long userId = StpUtil.getLoginIdAsLong();
        int count = messageService.markMessagesReadByEpisodeId(userId, episodeId);
        return ApiResponseVO.success(count);
    }
    
    /**
     * 删除消息
     */
    @Operation(summary = "删除消息", description = "删除单个消息")
    @DeleteMapping("/{messageId}")
    public ApiResponseVO<Boolean> deleteMessage(
            @Parameter(description = "消息ID", required = true)
            @PathVariable Long messageId) {
        Long userId = StpUtil.getLoginIdAsLong();
        boolean result = messageService.deleteMessage(messageId, userId);
        return ApiResponseVO.success(result);
    }
    
    /**
     * 获取特定类型的消息
     */
    @Operation(summary = "按类型获取消息", description = "获取特定类型的消息（如episode_update）")
    @GetMapping("/type/{type}")
    public ApiResponseVO<PageVO<MessageVO>> getMessagesByType(
            @Parameter(description = "消息类型", required = true)
            @PathVariable String type,
            @Parameter(description = "页码，从1开始", required = false)
            @RequestParam(defaultValue = "1") Integer page,
            @Parameter(description = "每页大小", required = false)
            @RequestParam(defaultValue = "20") Integer pageSize) {
        Long userId = StpUtil.getLoginIdAsLong();
        PageVO<MessageVO> result = messageService.getMessagesByType(userId, type, page, pageSize);
        return ApiResponseVO.success(result);
    }
}
