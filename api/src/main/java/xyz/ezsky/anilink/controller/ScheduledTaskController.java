package xyz.ezsky.anilink.controller;

import cn.dev33.satoken.annotation.SaCheckRole;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import xyz.ezsky.anilink.model.vo.ApiResponseVO;
import xyz.ezsky.anilink.model.vo.ScheduledTaskVO;
import xyz.ezsky.anilink.service.ScheduledTaskService;

import java.util.List;

/**
 * 定时任务管理控制器。
 *
 * <p>提供定时任务列表查看与立即触发能力，仅超级管理员可访问。</p>
 */
@RestController
@RequestMapping("/api/admin/scheduled-tasks")
@Tag(name = "定时任务管理", description = "定时任务列表与立即触发")
@SaCheckRole("super-admin")
public class ScheduledTaskController {

    @Autowired
    private ScheduledTaskService scheduledTaskService;

    /**
     * 获取所有定时任务。
     */
    @GetMapping
    @Operation(summary = "获取定时任务列表", description = "返回所有已注册定时任务的元信息与最近运行状态")
    public ApiResponseVO<List<ScheduledTaskVO>> listTasks() {
        return ApiResponseVO.success(scheduledTaskService.listTasks(), "获取定时任务列表成功");
    }

    /**
     * 立即触发指定定时任务。
     */
    @PostMapping("/{id}/trigger")
    @Operation(summary = "立即触发定时任务", description = "异步立即执行指定定时任务，不阻塞请求")
    public ApiResponseVO<ScheduledTaskVO> triggerNow(@PathVariable String id) {
        ScheduledTaskVO vo = scheduledTaskService.triggerNow(id);
        return ApiResponseVO.success(vo, "定时任务 [" + vo.getName() + "] 已触发执行");
    }

    /**
     * 设置指定定时任务的启停状态。
     */
    @PutMapping("/{id}/enabled")
    @Operation(summary = "开关定时任务", description = "开启后恢复自动调度，关闭后停止自动调度（手动触发仍可用）；部分任务不允许开关")
    public ApiResponseVO<ScheduledTaskVO> setEnabled(@PathVariable String id, @RequestParam boolean enabled) {
        ScheduledTaskVO vo = scheduledTaskService.setEnabled(id, enabled);
        return ApiResponseVO.success(vo, "定时任务 [" + vo.getName() + "] 已" + (enabled ? "开启" : "关闭"));
    }
}
