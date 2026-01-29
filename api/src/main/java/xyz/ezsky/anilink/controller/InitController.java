package xyz.ezsky.anilink.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import xyz.ezsky.anilink.model.vo.ApiResponseVO;
import xyz.ezsky.anilink.model.dto.SetSiteConfigRequest;
import xyz.ezsky.anilink.model.dto.MediaLibraryDTO;
import xyz.ezsky.anilink.model.vo.SystemInfoVO;
import xyz.ezsky.anilink.model.vo.MediaLibraryVO;
import xyz.ezsky.anilink.model.vo.PathVO;
import xyz.ezsky.anilink.service.SiteConfigService;
import xyz.ezsky.anilink.service.MediaLibraryService;
import xyz.ezsky.anilink.service.SystemInfoService;

import java.util.List;

/**
 * 系统初始化控制器
 */
@RestController
@RequestMapping("/api/init")
@Tag(name = "系统初始化", description = "系统初始化相关接口")
public class InitController {
    
    @Autowired
    private SiteConfigService siteConfigService;
    
    @Autowired
    private MediaLibraryService mediaLibraryService;
    
    @Autowired
    private SystemInfoService systemInfoService;
    
    /**
     * 检查是否未安装
     */
    private void checkNotInstalled() {
        if (siteConfigService.isInstalled()) {
            throw new RuntimeException("系统已安装，无需重复操作");
        }
    }
    
    /**
     * 获取系统信息
     * 用于安装时检查系统状态
     */
    @GetMapping("system-info")
    @Operation(summary = "获取系统信息", description = "获取数据库类型、IP地址、内存、CPU等系统信息")
    public ApiResponseVO<SystemInfoVO> getSystemInfo() {
        checkNotInstalled();
        SystemInfoVO systemInfo = systemInfoService.getSystemInfo();
        return ApiResponseVO.success(systemInfo, "获取系统信息成功");
    }
    
    /**
     * 初始化站点配置和管理员账号
     * 用于安装引导
     */
    @PostMapping("site-config")
    @Operation(summary = "初始化站点配置", description = "设置站点信息和管理员账号密码，用于安装引导")
    public ApiResponseVO<String> initSiteConfig(@RequestBody SetSiteConfigRequest request) {
        checkNotInstalled();
        siteConfigService.setSiteConfig(request);
        return ApiResponseVO.success("设置成功", "站点配置已更新");
    }
    
    /**
     * 添加媒体库
     * 用于安装引导
     */
    @PostMapping("media-library")
    @Operation(summary = "添加媒体库", description = "添加媒体库，用于安装引导")
    public ApiResponseVO<MediaLibraryVO> initMediaLibrary(@RequestBody MediaLibraryDTO dto) {
        checkNotInstalled();
        MediaLibraryVO result = mediaLibraryService.addLibrary(dto);
        return ApiResponseVO.success(result, "添加成功");
    }
    
    /**
     * 获取所有媒体库
     * 用于安装引导
     */
    @GetMapping("media-library")
    @Operation(summary = "获取所有媒体库", description = "获取所有已添加的媒体库，用于安装引导")
    public ApiResponseVO<List<MediaLibraryVO>> getAllMediaLibraries() {
        checkNotInstalled();
        return ApiResponseVO.success(mediaLibraryService.findAll());
    }
    
    /**
     * 查询服务端路径
     * 用于安装引导
     */
    @GetMapping("media-library/paths")
    @Operation(summary = "查询服务端路径", description = "查询指定根目录下的所有文件和文件夹路径，用于安装引导")
    public ApiResponseVO<List<PathVO>> listServerPaths(
            @RequestParam String rootPath,
            @RequestParam(required = false, defaultValue = "false") boolean onlyDir,
            @RequestParam(required = false) String fileType) {
        checkNotInstalled();
        return ApiResponseVO.success(mediaLibraryService.listServerPaths(rootPath, onlyDir, fileType));
    }
}
