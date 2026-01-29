package xyz.ezsky.anilink.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import cn.dev33.satoken.annotation.SaCheckRole;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import xyz.ezsky.anilink.model.vo.SystemInfoVO;
import xyz.ezsky.anilink.model.vo.ApiResponseVO;
import xyz.ezsky.anilink.service.SystemInfoService;

/**
 * 系统信息控制器
 */
@RestController
@RequestMapping("/api/system")
@Tag(name = "系统信息", description = "系统信息相关接口")
public class SystemInfoController {
    
    @Autowired
    private SystemInfoService systemInfoService;
    
    /**
     * 获取系统信息
     */
    @GetMapping("info")
    @SaCheckRole("super-admin")
    @Operation(summary = "获取系统信息", description = "获取数据库类型、IP地址、内存、CPU等系统信息")
    public ApiResponseVO<SystemInfoVO> getSystemInfo() {
        SystemInfoVO systemInfo = systemInfoService.getSystemInfo();
        return ApiResponseVO.success(systemInfo, "获取系统信息成功");
    }
}
